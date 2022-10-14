/* 
 * The MIT License
 *
 * Copyright © 2018 Robert Poole <Tarquin.AZ@gmail.com>.
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

import java.math.BigInteger;

/**
 * General interface for types representing integral values.
 *
 * @author tarquin
 */
public interface IntegerType extends Numeric, Comparable<IntegerType> {
    @Override
    public IntegerType magnitude();
    @Override
    public IntegerType negate();
    /**
     * Compute the remainder of this / divisor.
     * @param divisor
     * @return the modulus, or remainder
     */
    public IntegerType modulus(IntegerType divisor);
    public boolean isEven();
    public boolean isOdd();
    public boolean isPerfectSquare();
    /**
     * The number of digits in this integer in base 10.
     * @return the number of digits 
     */
    public long numberOfDigits();
    /**
     * Return the value of the digit at {@code position}. The index
     * is 0-based and starts with the least significant digit.
     * @param position the 0-based index of the desired digit
     * @return an integer value in the range 0 to 9
     * @throws IndexOutOfBoundsException 
     */
    public int digitAt(long position) throws IndexOutOfBoundsException;
    /**
     * Compute this^exponent, given an integer exponent.
     * @param exponent an integer exponent
     * @return this raised to the {@code exponent} power
     */
    public Numeric pow(IntegerType exponent);
    
    @Override
    public IntegerType sqrt();
    
    public default boolean isPowerOf2() {
        return sign() != Sign.NEGATIVE && asBigInteger().bitCount() == 1;
    }
    
    public BigInteger asBigInteger();
    public Sign sign();
}
