package tungsten.types.functions.impl;
/*
 * The MIT License
 *
 * Copyright © 2022 Robert Poole <Tarquin.AZ@gmail.com>.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

import tungsten.types.Numeric;
import tungsten.types.Range;
import tungsten.types.exceptions.CoercionException;
import tungsten.types.functions.ArgVector;
import tungsten.types.functions.UnaryFunction;
import tungsten.types.functions.support.Simplifiable;
import tungsten.types.numerics.IntegerType;
import tungsten.types.numerics.RationalType;
import tungsten.types.numerics.RealType;
import tungsten.types.numerics.impl.ExactZero;
import tungsten.types.numerics.impl.One;
import tungsten.types.numerics.impl.Zero;
import tungsten.types.util.OptionalOperations;
import tungsten.types.util.RangeUtils;

import java.math.MathContext;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A function that represents a product of two or more functions.
 * Formally, &prod;&fnof;<sub>n</sub>(x) = &fnof;<sub>1</sub>(x) &sdot; &fnof;<sub>2</sub>(x) &ctdot; &fnof;<sub>N</sub>(x)<br>
 * This function is entirely intended for composition, and is fully
 * differentiable.
 *
 * @param <T> the input parameter type
 * @param <R> the output parameter type
 */
public class Product<T extends Numeric, R extends Numeric> extends UnaryFunction<T, R> implements Simplifiable {
    private final List<UnaryFunction<T, R>> terms = new ArrayList<>();


    public Product(String argName, Class<R> rtnType) {
        super(argName, rtnType);
    }

    @SafeVarargs
    public Product(UnaryFunction<T, R>... productOf) {
        super("x", productOf[0].getReturnType());
        Arrays.stream(productOf).filter(Product.class::isInstance).forEach(this::appendTerm);
        Arrays.stream(productOf).filter(f -> !(f instanceof Product))
                .filter(f -> !(f instanceof Const)).forEach(terms::add);
        // combine all const terms into one
        try {
            R prodOfConstants = (R) Arrays.stream(productOf).filter(Const.class::isInstance)
                    .map(Const.class::cast).map(Const::inspect)
                    .reduce(One.getInstance(MathContext.UNLIMITED), Numeric::multiply)
                    .coerceTo(getReturnType());
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
        super(argName, first.getReturnType() != null ? first.getReturnType() : second.getReturnType());
        appendTerm(first);
        appendTerm(second);
    }

    /**
     * Returns a {@code Product} of two unary functions.
     * @param first  the first unary function
     * @param second the second unary function
     * @return the product of {@code first} and {@code second}
     * @param <T> the input parameter type
     * @param <R> the output parameter type
     */
    public static <T extends Numeric, R extends Numeric> Product<T, R> of(UnaryFunction<T, R> first, UnaryFunction<T, R> second) {
        final String argName1 = first.expectedArguments()[0];
        final String argName2 = second.expectedArguments()[0];
        String argName = argName1.equals(argName2) ? argName1 : "x";
        return new Product<>(argName, first, second);
    }

    /**
     * Append a term to this product, consolidating constants
     * and flattening products.
     * @param term the unary function to append
     */
    public void appendTerm(UnaryFunction<T, R> term) {
        if (term instanceof Const && termCount() > 0L) {
            final Const<T, R> cterm = (Const<T, R>) term;
            final MathContext ctx = cterm.inspect().getMathContext();
            if (One.isUnity(cterm.inspect())) return;
            try {
                R one = OptionalOperations.dynamicInstantiate(getReturnType(), 1);
                R prodOfConstants = (R) parallelStream().filter(Const.class::isInstance)
                        .map(Const.class::cast).map(Const::inspect)
                        .reduce(One.getInstance(ctx), Numeric::multiply)
                        .add(cterm.inspect())
                        .coerceTo(getReturnType());
                terms.removeIf(Const.class::isInstance);
                if (Zero.isZero(prodOfConstants)) terms.clear();  // zero renders all other terms irrelevant
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
     * @param <T> the input parameter type for {@code p1}, {@code p2}, and the combined product
     * @param <R> the return type of {@code p1}, {@code p2}, and the combined result
     */
    public static <T extends Numeric, R extends Numeric> Product<T, R> combineTerms(Product<T, R> p1, Product<T, R> p2) {
        final String argName = p1.getArgumentName().equals(p2.getArgumentName()) ? p1.getArgumentName() : "x";
        Product<T, R> p3 = new Product<>(argName, p1.getReturnType());
        p3.terms.addAll(p1.terms);
        p3.terms.addAll(p2.terms);
        try {
            R prodOfConstants = (R) p3.parallelStream().filter(Const.class::isInstance)
                    .map(Const.class::cast).map(Const::inspect)
                    .reduce(One.getInstance(MathContext.UNLIMITED), Numeric::multiply)
                    .coerceTo(p1.getReturnType() != null ? p1.getReturnType() : p2.getReturnType());
            p3.terms.removeIf(Const.class::isInstance);
            if (!One.isUnity(prodOfConstants)) p3.terms.add(Const.getInstance(prodOfConstants));
        } catch (CoercionException e) {
            throw new IllegalStateException("Problem combining two products", e);
        }

        return p3;
    }

    /**
     * Obtain the count of terms in this product.
     * @return the count of terms
     */
    public long termCount() {
        return stream().count();
    }

    @Override
    public R apply(ArgVector<T> arguments) {
        try {
            return (R) terms.parallelStream().map(f -> f.apply(arguments))
                    .map(Numeric.class::cast)
                    .reduce(One.getInstance(MathContext.UNLIMITED), Numeric::multiply).coerceTo(getReturnType());
        } catch (CoercionException e) {
            throw new IllegalStateException("Unable to coerce result to " + getReturnType().getTypeName(), e);
        }
    }

    @Override
    public UnaryFunction<T, R> simplify() {
        if (Negate.isNegateEquivalent(this)) {
            Product<T, R> p = new Product<>(getArgumentName(), getReturnType());
            terms.stream().filter(f -> !Const.isConstEquivalent(f)).forEach(p::appendTerm);
            return p.andThen(Negate.getInstance(getReturnType()));
        }
        if (terms.stream().anyMatch(Const::isConstEquivalent)) {
            Optional<RealType> cval = terms.stream().filter(Const::isConstEquivalent).map(Const::getConstEquivalent)
                    .map(Const::inspect).reduce(Product::safeReduce);
            R value = safeCoerce(cval.orElseThrow());
            if (Zero.isZero(value)) return Const.getInstance(value);
            List<UnaryFunction<T, R>> cleaned = terms.stream().filter(f -> !Const.isConstEquivalent(f)).collect(Collectors.toList());
            if (cleaned.isEmpty()) return Const.getInstance(value);
            Product<T, R> p = new Product<>(getArgumentName(), getReturnType());
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
            Product<T, R> p = new Product<>(getArgumentName(), getReturnType());
            terms.stream().filter(f -> !(f instanceof Pow)).forEach(p::appendTerm);
            if (Zero.isZero(aggExponent)) return p.termCount() > 0L ? p : Const.getInstance(OptionalOperations.dynamicInstantiate(getReturnType(), "1"));
            if (aggExponent instanceof IntegerType) {
                p.appendTerm(new Pow<>(((IntegerType) aggExponent).asBigInteger().longValueExact(), getReturnType()));
            } else {
                p.appendTerm(new Pow<>((RationalType) aggExponent, getReturnType()));
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
            return (R) val.coerceTo(getReturnType());
        } catch (CoercionException e) {
            throw new IllegalArgumentException("Value " + val + " cannot be coerced to " + getReturnType().getTypeName(), e);
        }
    }

    @Override
    public Range<RealType> inputRange(String argName) {
        return terms.parallelStream().map(f -> f.inputRange(argName))
                .reduce(RangeUtils.ALL_REALS, Range::chooseNarrowest);
    }

    @Override
    public Class<T> getArgumentType() {
        Iterator<UnaryFunction<T, R>> iter = terms.iterator();
        Class<T> result = iter.next().getArgumentType();
        while (iter.hasNext()) {
            Class<T> altArgType = iter.next().getArgumentType();
            if (altArgType != result) {
                Logger.getLogger(Product.class.getName()).log(Level.WARNING,
                        "Mismatched arg types: {0} vs. {1}",
                        new Object[] { altArgType.getTypeName(), result.getTypeName() });
                if (altArgType.isAssignableFrom(result)) result = altArgType;
            }
        }
        return result;
    }

    /**
     * Return a stream of the terms in this product.
     * @return a stream of unary functions
     */
    public Stream<UnaryFunction<T, R>> stream() {
        return terms.stream();
    }

    /**
     * Return a parallel stream of the terms in this product.
     * @return a parallel stream of unary functions
     */
    public Stream<UnaryFunction<T, R>> parallelStream() {
        return terms.parallelStream();
    }

    @Override
    public String toString() {
        final long termCount = termCount();
        if (termCount == 2L) {
            StringBuilder buf = new StringBuilder();
            buf.append(terms.get(0));
            if (terms.get(0) instanceof  Const && (terms.get(1) instanceof Pow || terms.get(1) instanceof Reflexive)) {
                buf.append('\u2062'); // invisible times
            } else {
                char whitespace = '\u205F';  // U+205F medium mathematical space
                if (terms.get(0) instanceof Const || terms.get(1) instanceof Const) whitespace = '\u2009';  // thin space
                // U+22C5 dot operator
                buf.append(whitespace).append('\u22C5').append(whitespace);
            }
            buf.append(terms.get(1));
            return buf.toString();
        }
        return "\u220F\u2009\u0192\u2099(" + getArgumentName() + "), N = " + termCount;
    }

    @Override
    public int hashCode() {
        return Objects.hash(getArgumentName(), terms, getReturnType());
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof Product) {
            Product<?, ?> other = (Product<?, ?>) obj;
            if (!getArgumentName().equals(other.getArgumentName())) return false;
            if (termCount() != other.termCount()) return false;
            if (!getReturnType().isAssignableFrom(other.getReturnType())) return false;
            return parallelStream().allMatch(other.terms::contains);
        }
        return false;
    }
}
