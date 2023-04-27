package tungsten.types;

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

import tungsten.types.exceptions.CoercionException;
import tungsten.types.numerics.RealType;

import java.lang.reflect.ParameterizedType;
import java.math.MathContext;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Robert Poole <a href="mailto:Tarquin.AZ@gmail.com">Tarquin.AZ@gmail.com</a>
 * @param <T> the numeric type of this vector
 */
public interface Vector<T extends Numeric> {
    /**
     * Calculates the number of elements in this vector.
     * This is the dimensionality of this vector.
     * @return the number of elements, or dimension
     */
    long length();
    /**
     * Returns the element contained in this vector at {@code position}.
     * @param position the 0-based position within the vector
     * @return the specified element
     */
    T elementAt(long position);
    /**
     * Set or replace the element at {@code position}.
     * @param element the new element
     * @param position the 0-based position within the vector
     */
    void setElementAt(T element, long position);
    /**
     * Append the given element to this vector.  This has
     * the side effect of increasing the {@link #length() }.
     * @param element the element to append
     */
    void append(T element);
    Vector<T> add(Vector<T> addend);
    Vector<T> subtract(Vector<T> subtrahend);
    Vector<T> negate();
    Vector<T> scale(T factor);
    /**
     * Compute the magnitude of this vector.  This is
     * equivalent to geometric length of this vector.
     * @return this vector's magnitude
     */
    default RealType magnitude() {
        Numeric result = this.dotProduct(this).sqrt();
        try {
            return (RealType) result.coerceTo(RealType.class);
        } catch (CoercionException ce) {
            Logger.getLogger(Vector.class.getName()).log(Level.SEVERE,
                    "Vector magnitude should always be a real value; result = " + result, ce);
            throw new ArithmeticException("Non-real result: " + result);
        }
    }

    /**
     * Obtain the type of the elements contained by this {@link Vector}.
     * @return the {@link Class} of the vector elements
     */
    default Class<T> getElementType() {
        Class<T> clazz = (Class<T>) ((Class) ((ParameterizedType) getClass()
                .getGenericSuperclass()).getActualTypeArguments()[0]);
        if (clazz == null && length() > 0L) {
            clazz = (Class<T>) elementAt(0L).getClass();
        }
        return clazz;
    }

    /**
     * Compute the dot product of this vector and the given vector.
     * Dividing this result by the product of {@code this.magnitude()}
     * and {@code other.magnitude()} gives the cosine of the angle
     * between the two vectors.
     * @param other the vector with which to compute the dot product
     * @return the dot product
     */
    T dotProduct(Vector<T> other);
    /**
     * Compute the cross product of this vector and the given vector.
     * The resulting vector is orthogonal to this and {@code other},
     * with a magnitude equal to ||this||&sdot;||other||&sdot;sin(&theta;) &mdash;
     * with &theta; being the angle between these two vectors.
     * <br/><strong>Note:</strong> The cross product of two vectors
     * is only defined for 3- and 7-dimensions. Other dimensionalities, or
     * a dimensional mismatch between the two vectors, should result in
     * an exception being thrown.
     * @param other the vector with which to compute the cross product
     * @return the cross product
     */
    Vector<T> crossProduct(Vector<T> other);
    /**
     * Return a normalized vector that has the same direction as this
     * vector, but with a magnitude of 1 (i.e., a unit vector).
     * @return the normalized vector
     */
    Vector<T> normalize();
    /**
     * Compute the angle in radians between this vector and the given vector.
     * @param other the other vector
     * @return the value of the angle between these vectors in radians
     */
    RealType computeAngle(Vector<T> other);
    
    MathContext getMathContext();

    /*
    Methods necessary for Groovy operator overloading follow.
     */
    default Vector<T> plus(Vector<T> operand) {
        return this.add(operand);
    }
    default Vector<T> minus(Vector<T> operand) {
        return this.subtract(operand);
    }
    default Vector<T> negative() {
        return this.negate();
    }
    default Vector<T> leftShift(T element) {
        this.append(element);
        return this;
    }
    default T getAt(int i) {
        if (i < 0) return this.elementAt(length() + i);
        return this.elementAt(i);
    }
    default void putAt(int i, T element) {
        if (i < 0) this.setElementAt(element, length() + i);
        this.setElementAt(element, i);
    }
}
