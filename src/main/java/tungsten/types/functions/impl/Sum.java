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
import tungsten.types.functions.NumericFunction;
import tungsten.types.functions.UnaryFunction;
import tungsten.types.functions.support.Simplifiable;
import tungsten.types.numerics.RealType;
import tungsten.types.numerics.impl.ExactZero;
import tungsten.types.numerics.impl.One;
import tungsten.types.numerics.impl.Zero;
import tungsten.types.util.ClassTools;
import tungsten.types.util.OptionalOperations;
import tungsten.types.util.RangeUtils;

import java.math.MathContext;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

/**
 * A function that represents a sum of two or more functions.
 * Formally, &sum;&thinsp;&fnof;<sub>n</sub>(x) = &fnof;<sub>1</sub>(x) + &fnof;<sub>2</sub>(x) + &#x22EF; + &fnof;<sub>N</sub>(x)<br>
 * This function is entirely intended for composition, and is fully
 * differentiable.
 *
 * @param <T> the input parameter type
 * @param <R> the output parameter type
 * @author Robert Poole, <a href="mailto:tarquin@alum.mit.edu">MIT alumni e-mail</a>
 *  or <a href="mailto:Tarquin.AZ@gmail.com">Gmail</a>
 */
public class Sum<T extends Numeric, R extends Numeric> extends UnaryFunction<T, R> implements Simplifiable {
    private final Class<R> resultClass = (Class<R>) ClassTools.getTypeArguments(NumericFunction.class, this.getClass()).get(1);
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
        this("x", init);
    }

    protected Sum(String argName, List<? extends UnaryFunction<T, R>> init) {
        super(argName);
        terms.addAll(init);
    }

    /**
     * Returns a {@code Sum} of two unary function terms.
     * @param first  the first term
     * @param second the second term
     * @return the sum of {@code first} and {@code second}
     * @param <T> the input type
     * @param <R> the output type
     */
    public static <T extends Numeric, R extends Numeric> Sum<T, R> of(UnaryFunction<T, R> first, UnaryFunction<T, R> second) {
        final String argName1 = first.expectedArguments()[0];
        final String argName2 = second.expectedArguments()[0];
        String argName = argName1.equals(argName2) ? argName1 : "x";
        Sum<T, R> sum = new Sum<>(argName);
        sum.appendTerm(first);
        sum.appendTerm(second);
        return sum;
    }

    /**
     * Append a term to this sum, consolidating as appropriate.
     * @param term the unary function to add
     */
    public void appendTerm(UnaryFunction<T, R> term) {
        if (term instanceof Const && termCount() > 0L) {
            final Const<T, R> cterm = (Const<T, R>) term;
            if (Zero.isZero(cterm.inspect())) return;
            try {
                R sumOfConstants = (R) parallelStream().filter(Const.class::isInstance)
                        .map(Const.class::cast).map(Const::inspect)
                        .reduce(cterm.inspect(), Numeric::add)
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

    @Override
    public Sum<T, R> simplify() {
        Map<Integer, List<Integer>> combinerMap = new TreeMap<>();
        for (int j = 0; j < terms.size() - 1; j++) {
            UnaryFunction<T, R> testFor = terms.get(j);
            for (int k = j + 1; k < terms.size(); k++) {
                if (testFor.equals(terms.get(k))) {
                    if (combinerMap.containsKey(j)) {
                        combinerMap.get(j).add(k);
                    } else {
                        final int curr = k;
                        if (combinerMap.values().stream().flatMap(List::stream).anyMatch(val -> val == curr)) continue;
                        List<Integer> combineWith = new ArrayList<>();
                        combineWith.add(k);
                        combinerMap.put(j, combineWith);
                    }
                }
            }
        }
        if (!combinerMap.isEmpty()) {
            Logger.getLogger(Sum.class.getName()).log(Level.INFO,
                    "Found {0} terms out of {1} that can be combined.",
                    new Object[] {combinerMap.size(), terms.size()});
            List<UnaryFunction<T, R>> combinedTerms = new ArrayList<>();
            combinerMap.keySet().forEach(idx -> {
                R coeff = OptionalOperations.dynamicInstantiate(resultClass, combinerMap.get(idx).size() + 1);
                // coeff could theoretically be 1 if the combiner map entry has an empty list,
                // but that means something broke -- so fail fast
                if (One.isUnity(coeff)) throw new IllegalStateException("Fatal error combining terms");
                combinedTerms.add(new Product<>(Const.getInstance(coeff), terms.get(idx)));
            });
            // now copy the remaining terms
            SortedSet<Integer> oldTerms = new TreeSet<>(combinerMap.keySet());
            combinerMap.values().forEach(oldTerms::addAll);
            for (int j = 0; j < terms.size(); j++) {
                if (!oldTerms.contains(j)) combinedTerms.add(terms.get(j));
            }
            return new Sum<>(getArgumentName(), combinedTerms);
        }
        // next check if there are any unexpanded nested Sums
        if (parallelStream().anyMatch(Sum.class::isInstance)) {
            Logger.getLogger(Sum.class.getName()).log(Level.INFO,
                    "Found {0} unexpanded Sum terms out of {1} \u2014 flattening.",
                    new Object[] { stream().filter(Sum.class::isInstance).count(), terms.size() });
            Sum<T, R> flattened = new Sum<>(getArgumentName());
            stream().forEach(flattened::appendTerm);  // letting appendTerm() do all the hard work here
            return flattened;
        }

        // if nothing else works
        return this;
    }

    /**
     * Returns the count of terms in this sum.
     * @return the count of terms
     */
    public long termCount() {
        return stream().count();
    }

    @Override
    public R apply(ArgVector<T> arguments) {
        final MathContext ctx = arguments.getMathContext();
        try {
            return (R) terms.parallelStream().map(f -> f.apply(arguments))
                    .map(Numeric.class::cast)
                    .reduce(ExactZero.getInstance(ctx), Numeric::add).coerceTo(resultClass);
        } catch (CoercionException e) {
            throw new IllegalStateException("Unable to coerce result to " + resultClass.getTypeName() +
                    " for the given arguments " + arguments, e);
        }
    }

    @Override
    public Range<RealType> inputRange(String argName) {
        return terms.parallelStream().map(f -> f.inputRange(argName))
                .reduce(RangeUtils.ALL_REALS, Range::chooseNarrowest);
    }

    /**
     * Obtain a {@code Stream} of terms in this sum.
     * @return a stream of unary functions
     */
    public Stream<UnaryFunction<T, R>> stream() {
        return terms.stream();
    }

    /**
     * Obtain a parallel {@code Stream} of the terms in this sum.
     * @return a parallel stream of unary functions
     */
    public Stream<UnaryFunction<T, R>> parallelStream() {
        return terms.parallelStream();
    }

    @Override
    public String toString() {
        final long termCount = termCount();
        if (termCount == 2L) {
            // U+205F = medium mathematical space
            return terms.get(0) + "\u205F+\u205F" + terms.get(1);
        }
        return "\u2211\u2009\u0192\u2099(" + getArgumentName() + "), N = " + termCount;
    }

    @Override
    public int hashCode() {
        return Objects.hash(getArgumentName(), terms, resultClass);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof Sum) {
            Sum<?, ?> other = (Sum<?, ?>) obj;
            if (!getArgumentName().equals(other.getArgumentName())) return false;
            if (termCount() != other.termCount()) return false;
            if (!resultClass.isAssignableFrom(other.resultClass)) return false;
            return parallelStream().allMatch(other.terms::contains);
        }
        return false;
    }
}
