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
import tungsten.types.numerics.RealType;
import tungsten.types.numerics.Sign;
import tungsten.types.numerics.impl.IntegerImpl;
import tungsten.types.numerics.impl.RationalImpl;
import tungsten.types.numerics.impl.RealImpl;
import tungsten.types.util.MathUtils;

import java.math.BigInteger;
import java.math.MathContext;

/**
 * Compute a rational approximation of &pi; using a well-known
 * series.  This is an example of how to use a {@code Summation}
 * for computing a fundamental constant.
 */
public class PiSeries extends Summation<RationalType> {
    /**
     * Instantiate this series for a given {@code MathContext}.
     * @param ctx the {@code MathContext} to use
     */
    public PiSeries(MathContext ctx) {
        super(new PiTerm(), ctx);
    }

    /**
     * Convenience method that takes the upper bound for computing
     * the series and computes the series for k=1 to N.  The
     * rational result is converted into a {@code RealType}.
     * @param N the upper bound for the series
     * @return the real value of this summation
     */
    public RealType evaluate(IntegerType N) {
        if (N.sign() != Sign.POSITIVE) {
            throw new IllegalArgumentException("N must be > 0");
        }
        final IntegerType zero = new IntegerImpl(BigInteger.ZERO);
        IndexRange range = new IndexRange(zero, N, true);
        return new RealImpl(this.evaluate(range));
    }

    private static class PiTerm extends IndexFunction<RationalType> {
        private PiTerm() {
            super(RationalType.class);
        }

        final IntegerType two = new IntegerImpl(BigInteger.TWO);
        final IntegerType one = new IntegerImpl(BigInteger.ONE);

        @Override
        protected RationalType compute(IntegerType index) {
            IntegerType denom = MathUtils.factorial((IntegerType) two.multiply(index).add(one));
            IntegerType pwr2 = (IntegerType) two.pow((IntegerType) index.add(one));
            IntegerType kfact = MathUtils.factorial(index);
            IntegerType num = (IntegerType) kfact.multiply(kfact).multiply(pwr2);
            return new RationalImpl(num, denom);
        }

        @Override
        public String toString() {
            // k is the default index variable name, so using that here
            return "(k!)\u00B2\u22C52ᵏ\u207A\u00B9\u2215(2k\u2009+\u20091)!";
        }
    }
}
