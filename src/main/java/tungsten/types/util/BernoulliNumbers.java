package tungsten.types.util;
/*
 * The MIT License
 *
 * Copyright © 2023 Robert Poole <Tarquin.AZ@gmail.com>.
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
import tungsten.types.numerics.IntegerType;
import tungsten.types.numerics.RationalType;
import tungsten.types.numerics.impl.IntegerImpl;
import tungsten.types.numerics.impl.RationalImpl;

import java.math.BigInteger;
import java.math.MathContext;
import java.util.Arrays;
import java.util.stream.LongStream;
import java.util.stream.Stream;

/**
 * A utility class for obtaining and working with Bernoulli numbers.
 * The base constructor takes a single integer argument that determines
 * how many numbers will be precomputed.  All additional values beyond
 * the precomputed Bernoulli numbers will be dynamically computed using
 * the <a href="https://proofwiki.org/wiki/Definition:Bernoulli_Numbers/Recurrence_Relation">recurrence relation</a>
 * for the Bernoulli numbers.
 * @author Robert Poole <a href="mailto:tarquin@alum.mit.edu">MIT alumni e-mail</a>
 *   or <a href="mailto:Tarquin.AZ@gmail.com">Gmail</a>
 * @see <a href="https://en.wikipedia.org/wiki/Bernoulli_number#Efficient_computation_of_Bernoulli_numbers">algorithms
 *   for efficiently caclculating the Bernoulli numbers</a> (with an example written in Julia)
 */
public class BernoulliNumbers {
    private final RationalType[] B;
    private MathContext mctx = MathContext.UNLIMITED;
    private final LRUCache<Long, RationalType> cache;

    /**
     * Initialize this class with the first N&nbsp;+&nbsp;1 Bernoulli
     * numbers, B<sub>0</sub> through B<sub>N</sub> inclusive.
     * @param N the index of the highest Bernoulli number to generate
     */
    public BernoulliNumbers(int N) {
        if (N == Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Cannot allocate N+1 elements");
        }
        if (N < 2) {
            throw new IllegalArgumentException("N must be \u2265 2");
        }
        B = new RationalType[N + 1];
        B[0] = new RationalImpl(BigInteger.ONE, BigInteger.ONE);
        B[1] = new RationalImpl(BigInteger.ONE.negate(), BigInteger.TWO);
        precalculate(N);
        // and preallocate a sensible cache, say 2/3 of N
        cache = new LRUCache<>(2 * N / 3);
    }

    /**
     * Initialize this class by precomputing the first N&nbsp;+&nbsp;1
     * Bernoulli numbers with a given {@link MathContext}.
     * @param N    the index of the highest Bernoulli number to precompute
     * @param mctx the desired {@link MathContext} for these values
     */
    public BernoulliNumbers(int N, MathContext mctx) {
        this(N);
        setMathContext(mctx);
    }

    public void setMathContext(MathContext mctx) {
        this.mctx = mctx;
        for (RationalType Bk : B) OptionalOperations.setMathContext(Bk, mctx);
        if (!cache.isEmpty()) {
            for (RationalType Bk : cache.values()) OptionalOperations.setMathContext(Bk, mctx);
        }
    }

    private void precalculate(int n) {
        for (int m = 2; m <= n; m++) {
            for (long k = 0; k <= m; k++) {
                for (long v = 0; v <= k; v++) {
                    // binomial(k,v) * v^(m) / (k+1)
                    IntegerType vm = new IntegerImpl(pow(v, m));
                    RationalType term = new RationalImpl((IntegerType) MathUtils.nChooseK(k, v).multiply(vm),
                            new IntegerImpl(BigInteger.valueOf(k + 1L)));
                    if (B[m] == null) B[m] = term;
                    else if (v % 2 == 0) {
                        B[m] = (RationalType) B[m].add(term);
                    } else {
                        B[m] = (RationalType) B[m].subtract(term);
                    }
                }
            }
        }
    }

    /**
     * {@link Math#pow(double, double)} only deals with double values, and we want to
     * quickly exponentiate longs.
     * @param base     the value to exponentiate
     * @param exponent the exponent, a non-negative integer value
     * @return the result of calculating base<sup>exponent</sup>
     */
    private BigInteger pow(long base, int exponent) {
        if (exponent < 0) {
            throw new IllegalArgumentException("Cannot handle negative exponent for long");
        }
        if (exponent == 0 || base == 1L) return BigInteger.ONE;
        if (base == 0L) return BigInteger.ZERO;
        if (exponent == 1) return BigInteger.valueOf(base);
        // since exponent is currently an int in precalculate(), no need for fancy long power logic here
        return BigInteger.valueOf(base).pow(exponent);
    }

    /**
     * If B<sub>n</sub> is precalculated, return that value.
     * Otherwise, calculate B<sub>n</sub> using the recurrence relationship with prior B<sub>k</sub> values.
     * @param n the index of the Bernoulli number to obtain
     * @return the n<sup>th</sup> Bernoulli number
     * @see <a href="https://proofwiki.org/wiki/Definition:Bernoulli_Numbers/Recurrence_Relation">the recurrence relationship</a>
     *   for computing B<sub>k</sub>
     */
    public RationalType getB(long n) {
        if (n < (long) B.length) {
            return B[(int) n];
        }
        // aside from B₁, all odd-numbered Bernoulli numbers are zero; B₁ is covered above
        if (n % 2L == 1L) {
            return new RationalImpl(0L, 1L, mctx);
        }
        // if it's in the cache, we're done
        if (cache.containsKey(n)) return cache.get(n);
        // otherwise, use the recurrence relationship
        final RationalType coeff = new RationalImpl(-1L, n + 1L, mctx);
        RationalType calculated = (RationalType) LongStream.range(0L, n) // .parallel()
                .mapToObj(k -> MathUtils.nChooseK(n + 1L, k).multiply(getB(k)))
                .reduce(Numeric::add).map(x -> x.multiply(coeff))
                .orElseThrow(() -> new IllegalStateException("Error computing B" + UnicodeTextEffects.numericSubscript((int) n)));
        cache.put(n, calculated); // slower than the array, but more efficient since we only store non-zero values
        return calculated;
    }

    public Stream<RationalType> stream() {
        // the use of LongStream.range() is a kludge -- in any event, getB(k) will probably misbehave
        // long before k gets anywhere close to Long.MAX_VALUE
        // not using rangeClosed() because getB(n) calculates n + 1
        return Stream.concat(Arrays.stream(B),
                LongStream.range(B.length, Long.MAX_VALUE).mapToObj(this::getB));
    }
}
