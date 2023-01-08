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
import tungsten.types.exceptions.CoercionException;
import tungsten.types.numerics.IntegerType;
import tungsten.types.numerics.RealType;
import tungsten.types.util.MathUtils;

import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;


/**
 * A 1&#215;1 matrix consisting of a single element.
 *
 * @author Robert Poole <a href="mailto:Tarquin.AZ@gmail.com">Tarquin.AZ@gmail.com</a>
 * @param <T> the type of the single element of this matrix
 */
public class SingletonMatrix<T extends Numeric> implements Matrix<T> {
    private final T element;
    
    public SingletonMatrix(T element) {
        this.element = element;
    }

    @Override
    public long columns() {
        return 1L;
    }

    @Override
    public long rows() {
        return 1L;
    }

    @Override
    public T valueAt(long row, long column) {
        if (row != 0L || column != 0L) throw new IndexOutOfBoundsException("The only valid row, column indices for a singleton matrix are 0, 0.");
        return element;
    }

    @Override
    public T determinant() {
        return element;
    }

    @Override
    public Matrix<? extends Numeric> inverse() {
        return new SingletonMatrix<>(element.inverse());
    }

    @Override
    public T trace() {
        return element;
    }

    @Override
    public Matrix<T> transpose() {
        return this;
    }

    @Override
    public Matrix<T> add(Matrix<T> addend) {
        if (addend.rows() != rows() || addend.columns() != columns()) throw new ArithmeticException("Addend must be a 1\u00D71 matrix.");
        try {
            T sum = (T) element.add(addend.valueAt(0L, 0L)).coerceTo(element.getClass());
            return new SingletonMatrix<>(sum);
        } catch (CoercionException ex) {
            Logger.getLogger(SingletonMatrix.class.getName()).log(Level.SEVERE,
                    "Failed to cast sum of " + element + " and " + addend.valueAt(0L, 0L), ex);
            throw new ArithmeticException("Coercion failure: " + ex.getMessage());
        }
    }

    @Override
    public Matrix<T> multiply(Matrix<T> multiplier) {
        if (multiplier.rows() != rows() || multiplier.columns() != columns()) throw new ArithmeticException("Addend must be a 1\u00D71 matrix.");
        try {
            T sum = (T) element.multiply(multiplier.valueAt(0L, 0L)).coerceTo(element.getClass());
            return new SingletonMatrix<>(sum);
        } catch (CoercionException ex) {
            Logger.getLogger(SingletonMatrix.class.getName()).log(Level.SEVERE,
                    "Failed to cast product of " + element + " and " + multiplier.valueAt(0L, 0L), ex);
            throw new ArithmeticException("Coercion failure: " + ex.getMessage());
        }
    }

    @Override
    public SingletonMatrix<T> scale(T scaleFactor) {
        return new SingletonMatrix<>((T) element.multiply(scaleFactor));
    }
    
    @Override
    public SingletonMatrix<? extends Numeric> pow(Numeric n) {
        Numeric result;
        if (element instanceof RealType) {
            result = MathUtils.generalizedExponent((RealType) element, n, element.getMathContext());
        } else {
            if (!(n instanceof IntegerType)) {
                // we can check to see if we got a real that's actually just an integer
                if (n.isCoercibleTo(IntegerType.class)) {
                    // if so, coerce the value and fall through to computeIntegerExponent below
                    try {
                        n = n.coerceTo(IntegerType.class);
                    } catch (CoercionException e) {
                        throw new IllegalStateException("isCoercibleTo() is inconsistent with coerceTo()", e);
                    }
                } else {
                    throw new IllegalArgumentException("Currently, non-integer exponents are not supported for non-real types.");
                }
            }
            result = MathUtils.computeIntegerExponent(element, (IntegerType) n);
        }
        return new SingletonMatrix<>(result);
    }
    
    @Override
    public boolean equals(Object o) {
        if (o instanceof Matrix) {
            Matrix<? extends Numeric> that = (Matrix<? extends Numeric>) o;
            if (that.rows() != 1L || that.columns() != 1L) return false;
            return element.equals(that.valueAt(0L, 0L));
        }
        return false;
    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 13 * hash + Objects.hashCode(this.element);
        return hash;
    }
    
    @Override
    public String toString() {
        return String.join("\u2009", "[[", element.toString(), "]]");  // 2009 = thin-space
    }
}
