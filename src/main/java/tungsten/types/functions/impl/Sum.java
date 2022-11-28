package tungsten.types.functions.impl;

import tungsten.types.Numeric;
import tungsten.types.Range;
import tungsten.types.exceptions.CoercionException;
import tungsten.types.functions.ArgVector;
import tungsten.types.functions.UnaryFunction;
import tungsten.types.numerics.RealType;
import tungsten.types.numerics.impl.ExactZero;
import tungsten.types.numerics.impl.Zero;
import tungsten.types.util.RangeUtils;

import java.lang.reflect.ParameterizedType;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

/**
 * A function that represents a sum of two or more functions.
 * Formally, &sum;&fnof;<sub>n</sub>(x) = &fnof;<sub>1</sub>(x) + &fnof;<sub>2</sub>(x) + &ctdot; + &fnof;<sub>N</sub>(x)<br/>
 * This function is entirely intended for composition, and is fully
 * differentiable.
 *
 * @param <T> the input parameter type
 * @param <R> the output parameter type
 */
public class Sum<T extends Numeric, R extends Numeric> extends UnaryFunction<T, R> {
    private final Class<R> resultClass = (Class<R>) ((Class) ((ParameterizedType) this.getClass()
                    .getGenericSuperclass()).getActualTypeArguments()[1]);
    private final List<UnaryFunction<T, R>> terms = new ArrayList<>();

    public Sum(String argName) {
        super(argName);
    }

    @SafeVarargs
    public Sum(UnaryFunction<T, R>... sumOf) {
        super("x");
        Arrays.stream(sumOf).filter(f -> !(f instanceof Const)).forEach(terms::add);
        // combine all const terms into one
        try {
            R sumOfConstants = (R) Arrays.stream(sumOf).filter(Const.class::isInstance)
                    .map(Const.class::cast).map(Const::inspect)
                    .reduce(ExactZero.getInstance(MathContext.UNLIMITED), Numeric::add)
                    .coerceTo(resultClass);
            if (!Zero.isZero(sumOfConstants)) terms.add(Const.getInstance(sumOfConstants));
        } catch (CoercionException e) {
            throw new IllegalArgumentException("Constant sum cannot be coerced to function return type", e);
        }
    }

    protected Sum(List<? extends UnaryFunction<T, R>> init) {
        super("x");
        terms.addAll(init);
    }

    public void appendTerm(UnaryFunction<T, R> term) {
        if (term instanceof Const) {
            try {
                R sumOfConstants = (R) parallelStream().filter(Const.class::isInstance)
                        .map(Const.class::cast).map(Const::inspect)
                        .reduce(((Const) term).inspect(), Numeric::add)
                        .coerceTo(resultClass);
                terms.removeIf(Const.class::isInstance);
                if (!Zero.isZero(sumOfConstants)) terms.add(Const.getInstance(sumOfConstants));
            } catch (CoercionException e) {
                throw new IllegalArgumentException("Constant sum cannot be coerced to function return type", e);
            }
        } else if (term instanceof Sum) {
            ((Sum<T, R>) term).stream().forEach(this::appendTerm);
        } else {
            terms.add(term);
        }
    }

    /**
     * Combine two {@link Sum}s into a single {@link Sum}, effectively adding
     * the two functions together.  All constants are combined into a single constant term.
     *
     * @param s1 the first sum function to be combined
     * @param s2 the second sum function to be combined
     * @return the sum of {@code s1} and {@code s2}, a combined function
     * @param <T> the input parameter type for {@code s1}, {@code s2}, and {@code s3}
     * @param <R> the return type of {@code s1}, {@code s2}, and the combined result
     */
    public static <T extends Numeric, R extends Numeric> Sum<T, R> combineTerms(Sum<T, R> s1, Sum<T, R> s2) {
        final String argName = s1.getArgumentName().equals(s2.getArgumentName()) ? s1.getArgumentName() : "x";
        Sum<T, R> s3 = new Sum<>(argName);
        s3.terms.addAll(s1.terms);
        s3.terms.addAll(s2.terms);
        try {
            R sumOfConstants = (R) s3.parallelStream().filter(Const.class::isInstance)
                    .map(Const.class::cast).map(Const::inspect)
                    .reduce(ExactZero.getInstance(MathContext.UNLIMITED), Numeric::add)
                    .coerceTo(s1.resultClass);
            s3.terms.removeIf(Const.class::isInstance);
            if (!Zero.isZero(sumOfConstants)) s3.terms.add(Const.getInstance(sumOfConstants));
        } catch (CoercionException e) {
            throw new IllegalStateException("Problem combining two sums", e);
        }

        return s3;
    }

    public long termCount() {
        return stream().count();
    }

    @Override
    public R apply(ArgVector<T> arguments) {
        try {
            R result = (R) terms.parallelStream().map(f -> f.apply(arguments))
                    .map(Numeric.class::cast)
                    .reduce(ExactZero.getInstance(MathContext.UNLIMITED), Numeric::add).coerceTo(resultClass);
            return result;
        } catch (CoercionException e) {
            throw new IllegalStateException("Unable to coerce result to " + resultClass.getTypeName(), e);
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
        return "\u2211\u2009\u0192\u2099(" + getArgumentName() + "), N = " + termCount();
    }
}
