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
    private BigInteger p, q;
    private BigInteger pPrime, qPrime;

    /**
     * Construct this convergent using the first term, x<sub>0</sub>, of a continued fraction.
     * @param x0 the first term of a continued fraction
     */
    public Convergent(long x0) {
        this(BigInteger.valueOf(x0));
    }

    /**
     * Construct this convergent using the first term of a continued fraction.
     * @param x0 the first term of a continued fraction expressed as a {@code BigInteger}
     */
    public Convergent(BigInteger x0) {
        p = x0;
        q = BigInteger.ONE;
        pPrime = BigInteger.ONE;
        qPrime = BigInteger.ZERO;
    }

    /**
     * Given the x<sub>i</sub> term of a continued fraction, evolve the state
     * of this convergent to match.  A valid convergent requires that all
     * of the terms of the original CF are supplied to this method, in sequence.
     * @param xi the x<sub>i</sub> term of a continued fraction
     */
    public void nextTerm(long xi) {
        var pp = p;
        var qq = q;
        BigInteger scale = BigInteger.valueOf(xi);
        p = p.multiply(scale).add(pPrime);
        q = q.multiply(scale).add(qPrime);
        pPrime = pp;
        qPrime = qq;
    }

    /**
     * Obtain the value of p.
     * @return the value of p
     */
    public BigInteger getP() {
        return p;
    }

    /**
     * Obtain the value of q.
     * @return the value of q
     */
    public BigInteger getQ() {
        return q;
    }

    /**
     * Obtain the value of p&#x2032;.
     * @return the value of p&#x2032;
     */
    public BigInteger getpPrime() {
        return pPrime;
    }

    /**
     * Obtain the value of q&#x2032;.
     * @return the value of q&#x2032;
     */
    public BigInteger getqPrime() {
        return qPrime;
    }
}
