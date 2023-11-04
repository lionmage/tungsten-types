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
package tungsten.types.vector;

import tungsten.types.Matrix;
import tungsten.types.Numeric;
import tungsten.types.Vector;
import tungsten.types.exceptions.CoercionException;
import tungsten.types.matrix.impl.SingletonMatrix;
import tungsten.types.numerics.ComplexType;
import tungsten.types.numerics.RealType;
import tungsten.types.numerics.impl.ExactZero;
import tungsten.types.util.MathUtils;
import tungsten.types.util.OptionalOperations;
import tungsten.types.vector.impl.ArrayColumnVector;
import tungsten.types.vector.impl.ComplexVector;
import tungsten.types.vector.impl.RealVector;

import java.lang.reflect.Array;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Representation of a row vector.  This can also be
 * treated as a 1&#215;N (single-row) matrix with N columns.
 *
 * @param <T> the {@link Numeric} type of this row vector
 * @author Robert Poole <a href="mailto:Tarquin.AZ@gmail.com">Tarquin.AZ@gmail.com</a>
 */
public abstract class RowVector<T extends Numeric> implements Vector<T>, Matrix<T> {
    private MathContext mctx;
    protected Class<T> elementType;

    protected RowVector() {
        // default constructor
        mctx = MathContext.UNLIMITED;
    }

    protected RowVector(Class<T> clazz) {
        this();
        elementType = clazz;
    }

    public void setMathContext(MathContext mctx) {
        this.mctx = mctx;
    }

    @Override
    public abstract long length();

    @Override
    public abstract T elementAt(long position);

    @Override
    public abstract void setElementAt(T element, long position);

    @Override
    public abstract void append(T element);

    @Override
    public abstract RowVector<T> add(Vector<T> addend);

    @Override
    public RowVector<T> subtract(Vector<T> subtrahend) {
        return this.add(subtrahend.negate());
    }

    @Override
    public abstract RowVector<T> negate();

    @Override
    public abstract RowVector<T> scale(T factor);

    @Override
    public T dotProduct(Vector<T> other) {
        if (other.length() != this.length()) throw new ArithmeticException("Cannot compute dot product for vectors of different length");
        final Class<T> clazz = getElementType();
        if (ComplexType.class.isAssignableFrom(other.getElementType())) {
            List<T> copyOf = new ArrayList<>((int) other.length());
            for (long k = 0L; k < other.length(); k++) copyOf.add(other.elementAt(k));
            other = (Vector<T>) new ComplexVector((List<ComplexType>) copyOf).conjugate();
        }
        try {
            Numeric accum = ExactZero.getInstance(mctx);
            for (long i = 0L; i < this.length(); i++) {
                accum = accum.add(this.elementAt(i).multiply(other.elementAt(i)));
            }
            return (T) accum.coerceTo(clazz);
        } catch (CoercionException ex) {
            Logger.getLogger(RowVector.class.getName()).log(Level.SEVERE, "Error computing dot product.", ex);
            throw new ArithmeticException("Error computing dot product");
        }
    }

    @Override
    public Vector<T> crossProduct(Vector<T> other) {
        if (other.length() != this.length()) throw new ArithmeticException("Cannot compute cross product for vectors of different dimension");
        final Class<T> clazz = other.getElementType();
        final Class<T> myclass = getElementType();
        if (OptionalOperations.findCommonType(clazz, myclass) == Numeric.class) {
            throw new UnsupportedOperationException("No types in common between " +
                    clazz.getTypeName() + " and " + myclass.getTypeName());
        }
        if (RealType.class.isAssignableFrom(clazz)) {
            RealType[] elementArray = this.stream().map(x -> {
                try {
                    return (RealType) x.coerceTo(RealType.class);
                } catch (CoercionException e) {
                    throw new IllegalStateException(e);
                }
            }).toArray(RealType[]::new);
            RealVector realvec = new RealVector(elementArray, mctx);
            return (Vector<T>) realvec.crossProduct((Vector<RealType>) other);
        } else if (ComplexType.class.isAssignableFrom(clazz)) {
            ComplexType[] elementArray = this.stream().map(x -> {
                try {
                    return (ComplexType) x.coerceTo(ComplexType.class);
                } catch (CoercionException e) {
                    throw new IllegalStateException(e);
                }
            }).toArray(ComplexType[]::new);
            ComplexVector cplxvec = new ComplexVector(elementArray, mctx);
            return (Vector<T>) cplxvec.crossProduct((Vector<ComplexType>) other);
        }
        Logger.getLogger(RowVector.class.getName()).log(Level.SEVERE, "No way to compute cross product for {0}.", clazz.getTypeName());
        throw new UnsupportedOperationException("Not supported yet");
    }

    @Override
    public Vector<T> normalize() {
        final Class<T> clazz = getElementType();
        try {
            return this.scale((T) this.magnitude().inverse().coerceTo(clazz));
        } catch (CoercionException ex) {
            Logger.getLogger(RowVector.class.getName()).log(Level.SEVERE,
                    "Unable to normalize vector for type " + clazz.getTypeName(), ex);
            throw new ArithmeticException("Error computing vector normal");
        }
    }

    @Override
    public RealType computeAngle(Vector<T> other) {
        Numeric cosine = this.dotProduct(other).divide(this.magnitude().multiply(other.magnitude()));
        try {
            return (RealType) MathUtils.arccos(cosine).coerceTo(RealType.class);
        } catch (CoercionException e) {
            Logger.getLogger(ColumnVector.class.getName()).log(Level.INFO,
                    "While computing the angle between {0} and {1}, cos = {2}: " + e.getMessage(),
                    new Object[] {this, other, cosine});
            throw new ArithmeticException("arccos computed a non-real angle from " + cosine);
        }
    }

    @Override
    public MathContext getMathContext() {
        return mctx;
    }

    @Override
    public Class<T> getElementType() {
        if (elementType != null) return elementType;
        return Vector.super.getElementType();
    }

    @Override
    public long columns() {
        return length();
    }

    @Override
    public long rows() {
        return 1L;
    }

    @Override
    public boolean isUpperTriangular() {
        return false;
    }

    @Override
    public boolean isLowerTriangular() {
        return false;
    }

    @Override
    public boolean isTriangular() {
        return false;
    }

    @Override
    public T valueAt(long row, long column) {
        if (row != 0L) throw new IndexOutOfBoundsException("Row vector does not have a row " + row);
        return elementAt(column);
    }

    @Override
    public T determinant() {
        if (length() == 1L) return elementAt(0L);
        throw new ArithmeticException("Cannot compute determinant of a matrix with unequal columns and rows");
    }
    
    @Override
    public T trace() {
        if (length() == 1L) return elementAt(0L);
        throw new ArithmeticException("Cannot compute trace of a matrix with unequal columns and rows");
    }
    
    @Override
    public abstract ColumnVector<T> transpose();

    public abstract Stream<T> stream();

    /**
     * Like {@link Object#clone()}, but more type safe
     * (and doesn't throw any checked exceptions).
     * This method should return an exact copy of this
     * row vector; since the elements are effectively
     * value beans, a deep copy is not required, only
     * a copy of the implementation's data store.
     * @return a copy of this row vector
     */
    public abstract RowVector<T> copy();
    
    @Override
    public boolean equals(Object o) {
        if (o instanceof Vector) {
            Vector<T> that = (Vector<T>) o;
            if (this.length() != that.length()) return false;
            for (long i = 0L; i < this.length(); i++) {
                if (!this.elementAt(i).equals(that.elementAt(i))) return false;
            }
            return true;
        } else if (o instanceof Matrix) {
            Matrix<T> that = (Matrix<T>) o;
            if (that.columns() != this.columns() || that.rows() != this.rows()) return false;
            for (long i = 0L; i < this.columns(); i++) {
                if (!this.valueAt(0L, i).equals(that.valueAt(0L, i))) return false;
            }
            return true;
        }
        return false;
    }

    @Override
    public String toString() {
        // 202F = Narrow No-Break Space, 205F = Medium Mathematical Space
        return stream().map(Object::toString).collect(Collectors.joining(",\u205F", "[\u202F", "\u202F]"));
    }

    @Override
    public abstract Matrix<T> add(Matrix<T> addend);

    /**
     * Trim this row vector to contain at most {@code columns} values.
     * If {@code columns > this.columns()}, return this vector itself.
     * This method can only be used to obtain a subset of a row vector,
     * not to create a longer vector.  The entries retained are those
     * with an index 0 &le; k &lt; columns.
     * @param columns the desired number of columns of the resulting row vector
     * @return a row vector containing at most {@code columns} entries
     */
    public abstract RowVector<T> trimTo(long columns);

    @Override
    public Matrix<T> multiply(Matrix<T> multiplier) {
        if (multiplier.columns() != 1L) {
            if (this.columns() != multiplier.rows()) {
                throw new ArithmeticException("Multiplier must have the same number of rows as this row vector has elements");
            }
            final Class<T> clazz = getElementType();
            T[] values = (T[]) Array.newInstance(clazz, (int) multiplier.columns());
            for (int k = 0; k < multiplier.columns(); k++) {
                values[k] = this.dotProduct(multiplier.getColumn(k));
            }
            return new ArrayColumnVector<>(values);
        }

        // Apparently, the convention here is to compute the dot product of two vectors
        // and put the result into a 1×1 matrix.

        return new SingletonMatrix<>(this.dotProduct(multiplier.getColumn(0L)));
    }

    @Override
    public RowVector<T> getRow(long row) {
        if (row != 0L) throw new IndexOutOfBoundsException("Index does not match the single row of this matrix");
        return this;
    }

    @Override
    public Matrix<? extends Numeric> inverse() {
        if (length() == 1L) {
            return new SingletonMatrix<>(elementAt(0L).inverse());
        }
        throw new ArithmeticException("Inverse only applies to square matrices");
    }
}
