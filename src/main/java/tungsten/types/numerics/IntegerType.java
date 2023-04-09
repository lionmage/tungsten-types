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
 * @author Robert Poole
 */
public interface IntegerType extends Numeric, Comparable<IntegerType> {
    @SuppressWarnings("unchecked")
    @Override
    IntegerType magnitude();
    @Override
    IntegerType negate();
    /**
     * Compute the remainder of this / divisor.
     * @param divisor the divisor
     * @return the modulus, or remainder
     */
    IntegerType modulus(IntegerType divisor);
    boolean isEven();
    boolean isOdd();
    boolean isPerfectSquare();
    /**
     * The number of digits in this integer in base 10.
     * @return the number of digits 
     */
    long numberOfDigits();
    /**
     * Return the value of the digit at {@code position}. The index
     * is 0-based and starts with the least significant digit.
     * @param position the 0-based index of the desired digit
     * @return an integer value in the range 0 to 9
     * @throws IndexOutOfBoundsException if position is out of range
     */
    int digitAt(long position) throws IndexOutOfBoundsException;
    /**
     * Compute this^exponent, given an integer exponent.
     * @param exponent an integer exponent
     * @return this raised to the {@code exponent} power
     */
    Numeric pow(IntegerType exponent);

    /**
     * Compute this<sup>n</sup> mod m.
     * @param n the exponent
     * @param m the modulus
     * @return this<sup>n</sup> mod m
     */
    IntegerType powMod(long n, IntegerType m);
    IntegerType powMod(IntegerType n, IntegerType m);
    
    @Override
    IntegerType sqrt();
    
    default boolean isPowerOf2() {
        return sign() != Sign.NEGATIVE && asBigInteger().bitCount() == 1;
    }
    
    BigInteger asBigInteger();
    Sign sign();

    /*
    Methods necessary for Groovy operator overloading follow.
     */
    default IntegerType mod(IntegerType operand) {
        return this.modulus(operand);
    }
    default Numeric positive() {
        return this.magnitude();
    }
    default Object asType(Class<?> clazz) {
        if (BigInteger.class.isAssignableFrom(clazz)) {
            return this.asBigInteger();
        }
        return Numeric.super.asType(clazz);
    }
    IntegerType or(IntegerType operand);
    IntegerType and(IntegerType operand);
    IntegerType xor(IntegerType operand);
    IntegerType leftShift(IntegerType operand);
    IntegerType rightShift(IntegerType operand);
    IntegerType next();
    IntegerType previous();
    IntegerType bitwiseNegate();
}
