package tungsten.types;/*
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

import tungsten.types.exceptions.CoercionException;

import java.math.MathContext;

/**
 * Root interface for all numeric types.
 *
 * @author Robert Poole, <a href="mailto:tarquin@alum.mit.edu">MIT alumni e-mail</a>
 *  or <a href="mailto:Tarquin.AZ@gmail.com">Gmail</a>
 */
public interface Numeric {
    /**
     * Is this numeric object exact?  Typically, this
     * would depend on whether this object is an exact
     * representation, or whether it is subject to
     * e.g. measurement error.  This attribute is
     * intended to be analogous to the concept of
     * exactness in LISP-like languages (e.g., Scheme).
     * @return true if this object represents an exact value, false otherwise
     */
    boolean isExact();

    /**
     * Determines whether this object can be coerced to
     * the indicated numeric subtype.
     * @param numtype the target numeric subtype
     * @return true if this object is coercible, false otherwise
     */
    boolean isCoercibleTo(Class<? extends Numeric> numtype);

    /**
     * Attempt to coerce this object to the supplied subtype.
     * @param numtype the desired numeric subtype
     * @return this numeric object, coerced to a class implementing {@code numtype}
     * @throws CoercionException if the coercion cannot be attempted for any reason,
     *   or if coercion fails
     */
    default Numeric coerceTo(Class<? extends Numeric> numtype) throws CoercionException {
        if (numtype == Numeric.class) return this;
        throw new CoercionException("coerceTo() not implemented for " +
                numtype.getTypeName() + " in class " + this.getClass().getName(),
                this.getClass(), numtype);
    }

    /**
     * Computes the magnitude of this value. For many {@link Numeric} types,
     * this is equivalent to computing the absolute value. The return type is
     * guaranteed to implement {@link Comparable}, useful for types that are not
     * themselves comparable (e.g., complex numbers).
     * @return the magnitude of {@code this}
     * @param <R> the return type of the calculation, which may not be the same as {@code this.getClass()}
     */
    <R extends Numeric & Comparable<R>> R magnitude();

    /**
     * Compute the additive inverse of this value.
     * @return the negative of {@code this}
     */
    Numeric negate();

    /**
     * Compute the sum of this and the supplied value.
     * @param addend the value to add to {@code this}
     * @return the sum of {@code this} and {@code addend}
     */
    Numeric add(Numeric addend);
    /**
     * Compute the difference of this and the supplied value.
     * @param subtrahend the value to subtract from {@code this}
     * @return the difference between {@code this} and {@code subtrahend}
     */
    Numeric subtract(Numeric subtrahend);
    /**
     * Compute the product of this and the supplied value.
     * @param multiplier the value to multiply {@code this} by
     * @return the product of {@code this} and {@code multiplier}
     */
    Numeric multiply(Numeric multiplier);

    /**
     * Divide this value by the supplied value.
     * @param divisor the value to divide {@code this} by
     * @return the dividend of {@code this} and {@code divisor}
     */
    Numeric divide(Numeric divisor);

    /**
     * Compute the multiplicative inverse of this value.
     * The result may be of a different type from {@code this}.
     * @return the inverse of {@code this}
     */
    Numeric inverse();

    /**
     * Compute the square root of this value.  Note that the
     * type of the returned value may not be the same as {@code this}
     * (e.g., in the case of roots of negative real values).
     * The resolution may be dependent on the type of {@code this}
     * (e.g., integer values may provide a truncated result).
     * @return the square root of {@code this}
     */
    Numeric sqrt();

    /**
     * Obtain the {@link MathContext} associated with this numeric object.
     * The {@code MathContext} governs the number of significant digits
     * for all basic operations as well as the rounding mode.
     * The precision of the {@code MathContext} may not reflect the
     * actual number of digits of this object's value, but is rather
     * &ldquo;aspirational&rdquo; and places an upper limit on the
     * precision of computed results.
     * @return the {@code MathContext} associated with this numeric object
     */
    MathContext getMathContext();

    /*
     Methods necessary for Groovy operator overloading follow.
     */
    default Numeric plus(Numeric operand) {
        return this.add(operand);
    }
    default Numeric minus(Numeric operand) {
        return this.subtract(operand);
    }
    // multiply() is already provided above
    default Numeric div(Numeric operand) {
        return this.divide(operand);
    }
    Numeric power(Numeric operand);
    default Numeric negative() {
        return this.negate();
    }
    // positive() should only be implemented for subinterfaces other than ComplexType
    default Object asType(Class<?> clazz) {
        if (Numeric.class.isAssignableFrom(clazz)) {
            try {
                return this.coerceTo((Class<? extends Numeric>) clazz);
            } catch (CoercionException e) {
                throw new ArithmeticException("Error converting type: " + e.getMessage());
            }
        } else if (CharSequence.class.isAssignableFrom(clazz)) {
            if (clazz == StringBuilder.class) return new StringBuilder().append(this);
            return this.toString();
        }
        throw new ClassCastException("Unable to coerce to " + clazz.getTypeName());
    }
}
