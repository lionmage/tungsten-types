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
    boolean isExact();
    boolean isCoercibleTo(Class<? extends Numeric> numtype);
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
    Numeric negate();
    
    Numeric add(Numeric addend);
    Numeric subtract(Numeric subtrahend);
    Numeric multiply(Numeric multiplier);
    Numeric divide(Numeric divisor);
    Numeric inverse();
    Numeric sqrt();
    
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
