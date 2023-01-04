package tungsten.types.functions.impl;

import tungsten.types.Numeric;
import tungsten.types.Range;
import tungsten.types.exceptions.CoercionException;
import tungsten.types.functions.ArgVector;
import tungsten.types.functions.UnaryFunction;
import tungsten.types.numerics.IntegerType;
import tungsten.types.numerics.RationalType;
import tungsten.types.numerics.RealType;
import tungsten.types.numerics.impl.ExactZero;
import tungsten.types.numerics.impl.One;
import tungsten.types.numerics.impl.Zero;
import tungsten.types.util.OptionalOperations;
import tungsten.types.util.RangeUtils;

import java.lang.reflect.ParameterizedType;
import java.math.MathContext;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A function that represents a product of two or more functions.
 * Formally, &prod;&fnof;<sub>n</sub>(x) = &fnof;<sub>1</sub>(x) &sdot; &fnof;<sub>2</sub>(x) &ctdot; &fnof;<sub>N</sub>(x)<br/>
 * This function is entirely intended for composition, and is fully
 * differentiable.
 *
 * @param <T> the input parameter type
 * @param <R> the output parameter type
 */
public class Product<T extends Numeric, R extends Numeric> extends UnaryFunction<T, R> {
    private final Class<R> resultClass = (Class<R>) ((Class) ((ParameterizedType) this.getClass()
            .getGenericSuperclass()).getActualTypeArguments()[1]);
    private final List<UnaryFunction<T, R>> terms = new ArrayList<>();


    public Product(String argName) {
        super(argName);
    }

    @SafeVarargs
    public Product(UnaryFunction<T, R>... productOf) {
        super("x");
        Arrays.stream(productOf).filter(Product.class::isInstance).forEach(this::appendTerm);
        Arrays.stream(productOf).filter(f -> !(f instanceof Product)).filter(f -> !(f instanceof Const)).forEach(terms::add);
        // combine all const terms into one
        try {
            R prodOfConstants = (R) Arrays.stream(productOf).filter(Const.class::isInstance)
                    .map(Const.class::cast).map(Const::inspect)
                    .reduce(One.getInstance(MathContext.UNLIMITED), Numeric::multiply)
                    .coerceTo(resultClass);
            if (!One.isUnity(prodOfConstants)) terms.add(Const.getInstance(prodOfConstants));
        } catch (CoercionException e) {
            throw new IllegalArgumentException("Constant product cannot be coerced to function return type", e);
        }
    }

    /**
     * A constructor that takes two functions as input, which is a common use case for
     * this function.  This constructor avoids the varargs penalty incurred by the
     * principal constructor.
     *
     * @param argName the name of the sole argument to this function
     * @param first   the first function in the product
     * @param second  the second function in the product
     */
    public Product(String argName, UnaryFunction<T, R> first, UnaryFunction<T, R> second) {
        super(argName);
        appendTerm(first);
        appendTerm(second);
    }

    public static <T extends Numeric, R extends Numeric> Product<T, R> of(UnaryFunction<T, R> first, UnaryFunction<T, R> second) {
        final String argName1 = first.expectedArguments()[0];
        final String argName2 = second.expectedArguments()[0];
        String argName = argName1.equals(argName2) ? argName1 : "x";
        return new Product<>(argName, first, second);
    }

    public void appendTerm(UnaryFunction<T, R> term) {
        if (term instanceof Const && termCount() > 0L) {
            try {
                R prodOfConstants = (R) parallelStream().filter(Const.class::isInstance)
                        .map(Const.class::cast).map(Const::inspect)
                        .reduce(((Const) term).inspect(), Numeric::multiply)
                        .coerceTo(resultClass);
                terms.removeIf(Const.class::isInstance);
                if (!One.isUnity(prodOfConstants)) terms.add(Const.getInstance(prodOfConstants));
            } catch (CoercionException e) {
                throw new IllegalArgumentException("Constant product cannot be coerced to function return type", e);
            }
        } else if (term instanceof Product) {
            ((Product<T, R>) term).stream().forEach(this::appendTerm);
        } else {
            terms.add(term);
        }
    }

    /**
     * Combine two {@link Product}s into a single {@link Product}, effectively multiplying
     * the two functions together.  All constants are combined into a single constant term.
     *
     * @param p1 the first product function to be combined
     * @param p2 the second product function to be combined
     * @return the product of {@code p1} and {@code p2}, a combined function
     * @param <T> the input parameter type for {@code p1}, {@code p2}, and {@code p3}
     * @param <R> the return type of {@code p1}, {@code p2}, and the combined result
     */
    public static <T extends Numeric, R extends Numeric> Product<T, R> combineTerms(Product<T, R> p1, Product<T, R> p2) {
        final String argName = p1.getArgumentName().equals(p2.getArgumentName()) ? p1.getArgumentName() : "x";
        Product<T, R> p3 = new Product<>(argName);
        p3.terms.addAll(p1.terms);
        p3.terms.addAll(p2.terms);
        try {
            R prodOfConstants = (R) p3.parallelStream().filter(Const.class::isInstance)
                    .map(Const.class::cast).map(Const::inspect)
                    .reduce(One.getInstance(MathContext.UNLIMITED), Numeric::multiply)
                    .coerceTo(p1.resultClass != null ? p1.resultClass : p2.resultClass);
            p3.terms.removeIf(Const.class::isInstance);
            if (!One.isUnity(prodOfConstants)) p3.terms.add(Const.getInstance(prodOfConstants));
        } catch (CoercionException e) {
            throw new IllegalStateException("Problem combining two products", e);
        }

        return p3;
    }

    public long termCount() {
        return stream().count();
    }

    @Override
    public R apply(ArgVector<T> arguments) {
        try {
            R result = (R) terms.parallelStream().map(f -> f.apply(arguments))
                    .map(Numeric.class::cast)
                    .reduce(One.getInstance(MathContext.UNLIMITED), Numeric::multiply).coerceTo(resultClass);
            return result;
        } catch (CoercionException e) {
            throw new IllegalStateException("Unable to coerce result to " + resultClass.getTypeName(), e);
        }
    }

    public UnaryFunction<T, R> simplify() {
        if (Negate.isNegateEquivalent(this)) {
            Product<T, R> p = new Product<>(getArgumentName());
            terms.stream().filter(f -> !Const.isConstEquivalent(f)).forEach(p::appendTerm);
            return p.andThen(Negate.getInstance(resultClass));
        }
        if (terms.stream().anyMatch(Const::isConstEquivalent)) {
            Optional<RealType> cval = terms.stream().filter(Const::isConstEquivalent).map(Const::getConstEquivalent)
                    .map(Const::inspect).reduce(Product::safeReduce);
            R value = safeCoerce(cval.orElseThrow());
            if (Zero.isZero(value)) return Const.getInstance(value);
            List<UnaryFunction<T, R>> cleaned = terms.stream().filter(f -> !Const.isConstEquivalent(f)).collect(Collectors.toList());
            if (cleaned.size() == 0) return Const.getInstance(value);
            Product<T, R> p = new Product<>(getArgumentName());
            p.terms.addAll(cleaned);
            if (!One.isUnity(value)) {
                p.appendTerm(Const.getInstance(value));
            }
            if (p.termCount() == 1L) {
                assert cleaned.size() == 1;
                // if, after all the above, we have a single term left in the resulting product,
                // unwrap that function and return it instead
                return cleaned.get(0);
            }
            return p;
        }
        // if there are 2 or more Pow instances, add the exponents
        if (terms.stream().filter(Pow.class::isInstance).count() >= 2L) {
            Numeric aggExponent = terms.stream().filter(Pow.class::isInstance).map(Pow.class::cast)
                    .map(Pow::getExponent).reduce(ExactZero.getInstance(MathContext.UNLIMITED), Numeric::add);
            Product<T, R> p = new Product<>(getArgumentName());
            terms.stream().filter(f -> !(f instanceof Pow)).forEach(p::appendTerm);
            if (Zero.isZero(aggExponent)) return p.termCount() > 0L ? p : Const.getInstance(OptionalOperations.dynamicInstantiate(resultClass, "1"));
            if (aggExponent instanceof IntegerType) {
                p.appendTerm(new Pow<>(((IntegerType) aggExponent).asBigInteger().longValueExact()));
            } else {
                p.appendTerm(new Pow<>((RationalType) aggExponent));
            }
            return p;
        }

        // if all else fails, return the original
        return this;
    }

    private static RealType safeReduce(RealType A, RealType B) {
        return (RealType) A.multiply(B);
    }

    private R safeCoerce(Numeric val) {
        try {
            return (R) val.coerceTo(resultClass);
        } catch (CoercionException e) {
            throw new IllegalArgumentException("Value " + val + " cannot be coerced to " + resultClass.getTypeName(), e);
        }
    }

    @Override
    public Range<RealType> inputRange(String argName) {
        return terms.parallelStream().map(f -> f.inputRange(argName))
                .reduce(RangeUtils.ALL_REALS, Range::chooseNarrowest);
    }

    public Stream<UnaryFunction<T, R>> stream() {
        return terms.stream();
    }

    public Stream<UnaryFunction<T, R>> parallelStream() {
        return terms.parallelStream();
    }

    @Override
    public String toString() {
        return "\u220F\u2009\u0192\u2099(" + getArgumentName() + "), N = " + termCount();
    }

    @Override
    public int hashCode() {
        return Objects.hash(getArgumentName(), terms, resultClass);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof Product) {
            Product<?, ?> other = (Product<?, ?>) obj;
            if (!getArgumentName().equals(other.getArgumentName())) return false;
            if (termCount() != other.termCount()) return false;
            if (!resultClass.isAssignableFrom(other.resultClass)) return false;
            return parallelStream().allMatch(other.terms::contains);
        }
        return false;
    }
}
