/*
 * The MIT License
 *
 * Copyright © 2024 Robert Poole <Tarquin.AZ@gmail.com>.
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

package tungsten.types.util;

import tungsten.types.numerics.RationalType;

import java.math.BigInteger;
import java.util.Iterator;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Given a rational value, this iterator extracts the terms
 * of a continued fraction representation and returns them sequentially.
 */
public class RationalCFTermAdapter implements Iterator<Long> {
    private BigInteger num;
    private BigInteger denom;

    public RationalCFTermAdapter(RationalType rational) {
        num = rational.numerator().asBigInteger();
        denom = rational.denominator().asBigInteger();
        Logger.getLogger(RationalCFTermAdapter.class.getName())
                .log(Level.FINE, "Created a continued fraction term adapter for {0}.", rational);
    }

    @Override
    public boolean hasNext() {
        return !denom.equals(BigInteger.ZERO);
    }

    @Override
    public Long next() {
        if (denom.equals(BigInteger.ZERO)) return null;
        BigInteger p = num.divide(denom);

        var temp = denom;
        // if p is 0, this merely inverts the original fraction
        denom = num.subtract(p.multiply(denom));
        num = temp;

        return p.longValueExact();
    }
}
