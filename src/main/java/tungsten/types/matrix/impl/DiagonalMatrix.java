/*
 * The MIT License
 *
 * Copyright © 2019 Robert Poole <Tarquin.AZ@gmail.com>.
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
package tungsten.types.matrix.impl;

import tungsten.types.Matrix;
import tungsten.types.Numeric;
import tungsten.types.Vector;
import tungsten.types.exceptions.CoercionException;
import tungsten.types.numerics.ComplexType;
import tungsten.types.numerics.IntegerType;
import tungsten.types.numerics.RealType;
import tungsten.types.numerics.impl.Euler;
import tungsten.types.numerics.impl.ExactZero;
import tungsten.types.numerics.impl.Zero;
import tungsten.types.util.MathUtils;

import java.lang.reflect.Array;
import java.lang.reflect.ParameterizedType;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * A compact representation of a diagonal matrix.
 *
 * @author Robert Poole <a href="mailto:Tarquin.AZ@gmail.com">Tarquin.AZ@gmail.com</a>
 * @param <T> the numeric type of the elements of this matrix
 */
public class DiagonalMatrix<T extends Numeric> implements Matrix<T>  {
    final private T[] elements;
    
    @SafeVarargs
    public DiagonalMatrix(T... elements) {
        this.elements = Arrays.copyOf(elements, elements.length);
    }
    
    public DiagonalMatrix(Vector<T> source) {
        final Class<T> clazz = (Class<T>) ((Class) ((ParameterizedType) source.getClass()
                .getGenericSuperclass()).getActualTypeArguments()[0]);
        int arrayLength = source.length() > (long) Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) source.length();
        if (arrayLength < source.length()) {
            Logger.getLogger(DiagonalMatrix.class.getName()).log(Level.WARNING,
                    "Source vector with {0} elements will not fit into a Java array; truncating.",
                    source.length());
        }
        elements = (T[]) Array.newInstance(clazz, arrayLength);
        for (int i = 0; i < arrayLength; i++) {
            elements[i] = source.elementAt(i);
        }
    }

    @Override
    public long columns() {
        return elements.length;
    }

    @Override
    public long rows() {
        return elements.length;
    }

    @Override
    public T valueAt(long row, long column) {
        if (row == column) {
            return elements[(int) row];
        }
        final Class<T> clazz = (Class<T>) ((Class) ((ParameterizedType) getClass()
                .getGenericSuperclass()).getActualTypeArguments()[0]);
        try {
            return (T) ExactZero.getInstance(elements[0].getMathContext()).coerceTo(clazz);
        } catch (CoercionException ex) {
            Logger.getLogger(DiagonalMatrix.class.getName()).log(Level.SEVERE,
                    "Coercion of Zero to " + clazz.getTypeName() + " failed.", ex);
            throw new IllegalStateException(ex);
        }
    }

    @Override
    public T determinant() {
        final Class<T> clazz = (Class<T>) ((Class) ((ParameterizedType) getClass()
                .getGenericSuperclass()).getActualTypeArguments()[0]);
        T accum = elements[0];
        try {
            for (int idx = 1; idx < elements.length; idx++) {
                accum = (T) accum.multiply(elements[idx]).coerceTo(clazz);
            }
            return accum;
        } catch (CoercionException ce) {
            throw new IllegalStateException(ce);
        }
    }

    @Override
    public T trace() {
        final Class<T> clazz = (Class<T>) ((Class) ((ParameterizedType) getClass()
                .getGenericSuperclass()).getActualTypeArguments()[0]);
        T accum = elements[0];
        try {
            for (int idx = 1; idx < elements.length; idx++) {
                accum = (T) accum.add(elements[idx]).coerceTo(clazz);
            }
            return accum;
        } catch (CoercionException ce) {
            throw new IllegalStateException(ce);
        }
    }

    @Override
    public DiagonalMatrix<T> transpose() {
        return this; // diaginal matrices are their own transpose
    }

    @Override
    public Matrix<T> add(Matrix<T> addend) {
        if (addend.rows() != this.rows() || addend.columns() != this.columns()) {
            throw new ArithmeticException("Addend must match dimensions of this diagonal matrix.");
        }
        
        BasicMatrix<T> result = new BasicMatrix<>(addend);
        final Class<T> clazz = (Class<T>) ((Class) ((ParameterizedType) getClass()
                .getGenericSuperclass()).getActualTypeArguments()[0]);

        try {
            for (long idx = 0L; idx < this.rows(); idx++) {
                T sum = (T) addend.valueAt(idx, idx).add(elements[(int) idx]).coerceTo(clazz);
                result.setValueAt(sum, idx, idx);
            }
        } catch (CoercionException ce) {
            throw new IllegalStateException(ce);
        }
        return result;
    }

    @Override
    public Matrix<T> multiply(Matrix<T> multiplier) {
        BasicMatrix<T> result = new BasicMatrix<>();
        // scale the rows of the multiplier
        for (long idx = 0L; idx < this.rows(); idx++) {
            result.append(multiplier.getRow(idx).scale(elements[(int) idx]));
        }
        return result;
    }

    @Override
    public DiagonalMatrix<? extends Numeric> inverse() {
        if (Arrays.stream(elements).anyMatch(Zero::isZero)) {
            throw new ArithmeticException("Diagonal matrices with any 0 elements on the diagonal have no inverse.");
        }
        Numeric[] result = Arrays.stream(elements).map(Numeric::inverse).toArray(Numeric[]::new);
        return new DiagonalMatrix<>(result);
    }
    
    @Override
    public Matrix<? extends Numeric> pow(Numeric n) {
        Numeric[] result;
        final Class<T> clazz = (Class<T>) ((Class) ((ParameterizedType) getClass()
                .getGenericSuperclass()).getActualTypeArguments()[0]);
        if (RealType.class.isAssignableFrom(clazz)) {
            result = Arrays.stream(elements)
                    .map(element -> MathUtils.generalizedExponent((RealType) element, n, element.getMathContext()))
                    .toArray(Numeric[]::new);
        } else {
            if (!n.isCoercibleTo(IntegerType.class)) {
                throw new IllegalArgumentException("Currently, non-integer exponents are not supported for non-real types");
            }

            try {
                final IntegerType n_int = (IntegerType) n.coerceTo(IntegerType.class);
                result = Arrays.stream(elements)
                        .map(element -> MathUtils.computeIntegerExponent(element, n_int))
                        .toArray(Numeric[]::new);
            } catch (CoercionException e) {
                throw new IllegalStateException("Could not convert exponent " + n + " to integer");
            }
        }
        return new DiagonalMatrix<>(result);
    }

    public Matrix<? extends Numeric> exp() {
        final Euler e = Euler.getInstance(elements[0].getMathContext());

        Numeric[] result = Arrays.stream(elements)
                .map(element -> {
                    return element instanceof ComplexType ? e.exp((ComplexType) element) : e.exp(limitedUpconvert(element));
                }).toArray(Numeric[]::new);
        return new DiagonalMatrix<>(result);
    }
    
    @Override
    public boolean isUpperTriangular() { return true; }
    
    @Override
    public boolean isLowerTriangular() { return true; }
    
    @Override
    public boolean isTriangular() { return true; }
    
    // this method strictly exists to promote lesser types in the hierarchy to real values
    private RealType limitedUpconvert(Numeric val) {
        if (val instanceof RealType)  return (RealType) val;
        try {
            return (RealType) val.coerceTo(RealType.class);
        } catch (CoercionException ex) {
            throw new IllegalStateException(ex);
        }
    }
    
    @Override
    public String toString() {
        // 202F = non-breaking narrow space
        return Arrays.stream(elements).map(Object::toString)
                .collect(Collectors.joining(", ", "diag(\u202F", "\u202F)"));
    }

    @Override
    public DiagonalMatrix<T> scale(T scaleFactor) {
        final Class<T> clazz = (Class<T>) ((Class) ((ParameterizedType) getClass()
                .getGenericSuperclass()).getActualTypeArguments()[0]);
        T[] scaled = Arrays.stream(elements).map(element -> element.multiply(scaleFactor))
                .map(clazz::cast).toArray(size -> (T[]) Array.newInstance(clazz, size));
        return new DiagonalMatrix<>(scaled);
    }
    
    @Override
    public boolean equals(Object o) {
        if (o instanceof Matrix) {
            if (o instanceof DiagonalMatrix) {
                DiagonalMatrix<? extends Numeric> that = (DiagonalMatrix<? extends Numeric>) o;
                return Arrays.equals(this.elements, that.elements);
            }

            Matrix<? extends Numeric> that = (Matrix<? extends Numeric>) o;
            if (rows() != that.rows() || columns() != that.columns()) return false;
            // check to ensure the other matrix is both upper AND lower triangular
            if (that.isUpperTriangular() && that.isLowerTriangular()) {
                // now compare all elements on the diagonal
                for (long idx = 0L; idx < rows(); idx++) {
                    if (!that.valueAt(idx, idx).equals(elements[(int) idx])) return false;
                }
                return true;
            }
        }
        return false;
    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 97 * hash + Arrays.deepHashCode(this.elements);
        return hash;
    }
}
