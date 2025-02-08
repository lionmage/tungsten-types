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

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.regex.Pattern;

/**
 * General interface for types representing rational values of the form
 * p/q, where p and q are integral values (p, q &isin; &#x2124;), q&nbsp;&ne;&nbsp;0, and in general normalized
 * so that q&nbsp;&gt;&nbsp;0.  The set of rational numbers is typically denoted &#x211A;.
 *
 * @author Robert Poole, <a href="mailto:tarquin@alum.mit.edu">MIT alumni e-mail</a>
 */
public interface RationalType extends Numeric, Comparable<RationalType> {
    String REDUCE_FOR_EQUALITY_TEST = "tungsten.types.numerics.RationalType.reduceForEquals";
    Pattern SOLIDUS_REGEX = Pattern.compile("[/\u2044\u2215]");
    /**
     * Returns the magnitude (absolute value) of this rational
     * number in reduced form.
     * @return the magnitude of this value
     */
    @SuppressWarnings("unchecked")
    @Override
    RationalType magnitude();
    @Override
    RationalType negate();
    IntegerType numerator();
    IntegerType denominator();
    BigDecimal asBigDecimal();
    /**
     * Reduce this fraction by the biggest common factor
     * of the numerator and denominator.
     * 
     * @return a new {@link RationalType} equivalent to this object
     */
    RationalType reduce();

    /**
     * Determines if this rational value is reducible.
     * @return true if this fraction can be reduced, false if it is already in reduced form
     */
    boolean isReducible();
    @Override
    Numeric sqrt();
    Sign sign();
    IntegerType floor();
    IntegerType ceil();

    /**
     * Perform integer division of the {@link #numerator() numerator} by
     * the {@link #denominator()  denominator}, returning an array
     * containing the whole part of the division and the remainder, in
     * that order.
     * @return an array containing the integer dividend and the remainder
     */
    IntegerType[] divideWithRemainder();
    IntegerType modulus();

    static boolean reduceForEqualityTest() {
        return Boolean.getBoolean(REDUCE_FOR_EQUALITY_TEST);
    }

    /*
     Methods necessary for Groovy operator overloading follow.
     */

    default RationalType positive() {
        return this.magnitude();
    }

    default Object asType(Class<?> clazz) {
        if (BigInteger.class.isAssignableFrom(clazz)) {
            if (denominator().asBigInteger().equals(BigInteger.ONE)) return numerator().asBigInteger();
            throw new ArithmeticException("Value cannot be reduced to an integer");
        } else if (BigDecimal.class.isAssignableFrom(clazz)) {
            return this.asBigDecimal();
        }
        return Numeric.super.asType(clazz);
    }
}
