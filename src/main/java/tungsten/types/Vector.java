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

import tungsten.types.numerics.RealType;

import java.math.MathContext;

/**
 *
 * @author Robert Poole <Tarquin.AZ@gmail.com>
 * @param <T> the numeric type of this vector
 */
public interface Vector<T extends Numeric> {
    /**
     * Calculates the number of elements in this vector.
     * This is the dimensionality of this vector.
     * @return the number of elements, or dimension
     */
    public long length();
    /**
     * Returns the element contained in this vector at {@code position}.
     * @param position the 0-based position within the vector
     * @return the specified element
     */
    public T elementAt(long position);
    /**
     * Set or replace the element at {@code position}.
     * @param element the new element
     * @param position the 0-based position within the vector
     */
    public void setElementAt(T element, long position);
    /**
     * Append the given element to this vector.  This has
     * the side effect of increasing the {@link #length() }.
     * @param element the element to append
     */
    public void append(T element);
    public Vector<T> add(Vector<T> addend);
    public Vector<T> subtract(Vector<T> subtrahend);
    public Vector<T> negate();
    public Vector<T> scale(T factor);
    /**
     * Compute the magnitude of this vector.  This is
     * equivalent to geometric length of this vector.
     * @return the magnitude
     */
    public T magnitude();
    /**
     * Compute the dot product of this vector and the given vector.
     * Dividing this result by the product of {@code this.magnitude()}
     * and {@code other.magnitude()} gives the cosine of the angle
     * between the two vectors.
     * @param other the vector with which to compute the dot product
     * @return the dot product
     */
    public T dotProduct(Vector<T> other);
    /**
     * Compute the cross product of this vector and the given vector.
     * The resulting vector is orthogonal to this and {@code other},
     * with a magnitude equal to ||this||&sdot;||other||&sdot;sin(&theta;) and
     * &theta; being the angle between these two vectors;
     * @param other the vector with which to compute the cross product
     * @return the cross product
     */
    public Vector<T> crossProduct(Vector<T> other);
    /**
     * Return a normalized vector that has the same direction as this
     * vector, but with a magnitude of 1 (i.e., a unit vector).
     * @return the normalized vector
     */
    public Vector<T> normalize();
    /**
     * Compute the angle in radians between this vector and the given vector.
     * @param other the other vector
     * @return the value of the angle between these vectors in radians
     */
    public RealType computeAngle(Vector<T> other);
    
    public MathContext getMathContext();
}
