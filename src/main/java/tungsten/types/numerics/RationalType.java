/* 
 * The MIT License
 *
 * Copyright © 2018 Robert Poole <a href="mailto:Tarquin.AZ@gmail.com">Tarquin.AZ@gmail.com</a>.
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
package tungsten.types.numerics;

import tungsten.types.Numeric;

import java.math.BigDecimal;

/**
 * General interface for types representing rational values of the form
 * p/q, where p and q are integral values, q &ne; 0, and in general normalized
 * so that q &gt; 0.
 *
 * @author tarquin
 */
public interface RationalType extends Numeric, Comparable<RationalType> {
    /**
     * Returns the magnitude (absolute value) of this rational
     * number in reduced form.
     * @return the magnitude of this value
     */
    @Override
    public RationalType magnitude();
    @Override
    public RationalType negate();
    public IntegerType numerator();
    public IntegerType denominator();
    public BigDecimal asBigDecimal();
    /**
     * Reduce this fraction by the biggest common factor
     * of the numerator and denominator.
     * 
     * @return a new {@link RationalType} equivalent to this object
     */
    public RationalType reduce();
    @Override
    public Numeric sqrt();
    public Sign sign();
    public IntegerType floor();
    public IntegerType ceil();
    public IntegerType[] divideWithRemainder();
    public IntegerType modulus();
}
