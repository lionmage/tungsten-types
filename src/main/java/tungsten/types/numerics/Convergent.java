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

package tungsten.types.numerics;

import java.math.BigInteger;

/**
 * A simple class for representing convergent values that are
 * iteratively computed, e.g. when computing powers of
 * continued fractions.<br>
 * This pseudo-matrix contains two columns, &#x27E8;p,&nbsp;q&#x27E9;
 * and &#x27E8;p&#x2032;,&nbsp;q&#x2032;&#x27E9; with p&#x2032; and
 * q&#x2032; representing the previous state.
 * @since 0.7
 * @author Robert Poole, <a href="mailto:Tarquin.AZ@gmail.com">Gmail</a>
 */
public class Convergent {
    BigInteger p, q;
    BigInteger pPrime, qPrime;

    public Convergent(long x0) {
        this(BigInteger.valueOf(x0));
    }

    public Convergent(BigInteger x0) {
        p = x0;
        q = BigInteger.ONE;
        pPrime = BigInteger.ONE;
        qPrime = BigInteger.ZERO;
    }

    public void nextTerm(long xi) {
        var pp = p;
        var qq = q;
        BigInteger scale = BigInteger.valueOf(xi);
        p = p.multiply(scale).add(pPrime);
        q = q.multiply(scale).add(qPrime);
        pPrime = pp;
        qPrime = qq;
    }

    public BigInteger getP() {
        return p;
    }

    public BigInteger getQ() {
        return q;
    }

    public BigInteger getpPrime() {
        return pPrime;
    }

    public BigInteger getqPrime() {
        return qPrime;
    }
}
