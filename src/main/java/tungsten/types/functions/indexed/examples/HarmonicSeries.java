/*
 * The MIT License
 *
 * Copyright © 2025 Robert Poole <Tarquin.AZ@gmail.com>.
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
 *
 */

package tungsten.types.functions.indexed.examples;

import tungsten.types.functions.indexed.IndexFunction;
import tungsten.types.functions.indexed.IndexRange;
import tungsten.types.functions.indexed.Summation;
import tungsten.types.numerics.IntegerType;
import tungsten.types.numerics.RationalType;
import tungsten.types.numerics.impl.IntegerImpl;
import tungsten.types.numerics.impl.RationalImpl;

import java.math.BigInteger;
import java.math.MathContext;

/**
 * An example of how to use a {@code Summation} to compute the
 * harmonic series.
 */
public class HarmonicSeries extends Summation<RationalType> {
    private static final IntegerType one = new IntegerImpl(BigInteger.ONE);

    /**
     * Construct a harmonic series with a given {@code MathContext}.
     * @param ctx the {@code MathContext} to use
     */
    public HarmonicSeries(MathContext ctx) {
        super(new IndexFunction<>(RationalType.class) {
            @Override
            protected RationalType compute(IntegerType index) {
                return new RationalImpl(one, index);
            }

            @Override
            public String toString() {
                return "1\u2215" + this.getArgumentName();
            }
        }, ctx);
    }

    /**
     * Convenience method for evaluating the harmonic series from
     * 1 to N, which is a way to approximate ln(N) for large values of N.
     * @param N the upper limit for computing the harmonic series
     * @return the sum of terms 1&#x2215;k for k=1 to N
     */
    public RationalType evaluate(IntegerType N) {
        IndexRange range = new IndexRange(one, N, true);
        return evaluate(range);
    }
}
