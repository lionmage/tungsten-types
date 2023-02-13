/*
 * The MIT License
 *
 * Copyright © 2018 Robert Poole <a href="mailto:Tarquin.AZ@gmail.com">Tarquin.AZ@gmail.com</a>.
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
import tungsten.types.util.OptionalOperations;
import tungsten.types.vector.impl.ComplexVector;
import tungsten.types.vector.impl.RealVector;

import java.lang.reflect.Array;
import java.lang.reflect.ParameterizedType;
import java.math.MathContext;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Representation of a row vector.  This can also be
 * treated as a 1&#215;N (single-row) matrix with N columns.
 *
 * @author Robert Poole <a href="mailto:Tarquin.AZ@gmail.com">Tarquin.AZ@gmail.com</a>
 * @param <T> the {@link Numeric} type of this row vector
 */
public abstract class RowVector<T extends Numeric> implements Vector<T>, Matrix<T> {
    private MathContext mctx;

    protected RowVector() {
        // default constructor
        mctx = MathContext.UNLIMITED;
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
    public T magnitude() {
        final Class<T> clazz = (Class<T>) ((Class) ((ParameterizedType) getClass()
                .getGenericSuperclass()).getActualTypeArguments()[0]);
        try {
            T zero = (T) ExactZero.getInstance(mctx).coerceTo(clazz);
            return (T) stream().reduce(zero, (x, y) -> (T) x.add(y.multiply(y))).sqrt().coerceTo(clazz);
        } catch (CoercionException ex) {
            Logger.getLogger(RowVector.class.getName()).log(Level.SEVERE, "Unable to compute magnitude of row vector.", ex);
            throw new ArithmeticException("Cannot compute magnitude of row vector");
        }
    }

    @Override
    public T dotProduct(Vector<T> other) {
        if (other.length() != this.length()) throw new ArithmeticException("Cannot compute dot product for vectors of different length.");
        final Class<T> clazz = (Class<T>) ((Class) ((ParameterizedType) getClass()
                .getGenericSuperclass()).getActualTypeArguments()[0]);
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
        if (other.length() != this.length()) throw new ArithmeticException("Cannot compute cross product for vectors of different dimension.");
        final Class<T> clazz = (Class<T>) ((Class) ((ParameterizedType) other.getClass()
                .getGenericSuperclass()).getActualTypeArguments()[0]);
        final Class<T> myclass = (Class<T>) ((Class) ((ParameterizedType) getClass()
                .getGenericSuperclass()).getActualTypeArguments()[0]);
        if (OptionalOperations.findCommonType(clazz, myclass) == Numeric.class) {
            throw new UnsupportedOperationException("No types in common between " +
                    clazz.getTypeName() + " and " + myclass.getTypeName());
        }
        if (RealType.class.isAssignableFrom(clazz)) {
            RealType[] elementArray = (RealType[]) Array.newInstance(RealType.class, (int) length());
            this.stream().map(x -> {
                try {
                    return x.coerceTo(RealType.class);
                } catch (CoercionException e) {
                    throw new IllegalStateException(e);
                }
            }).toArray(size -> elementArray);
            RealVector realvec = new RealVector(elementArray, mctx);
            return (Vector<T>) realvec.crossProduct((Vector<RealType>) other);
        } else if (ComplexType.class.isAssignableFrom(clazz)) {
            ComplexType[] elementArray = (ComplexType[]) Array.newInstance(ComplexType.class, (int) length());
            this.stream().map(x -> {
                try {
                    return x.coerceTo(ComplexType.class);
                } catch (CoercionException e) {
                    throw new IllegalStateException(e);
                }
            }).toArray(size -> elementArray);
            ComplexVector cplxvec = new ComplexVector(elementArray, mctx);
            return (Vector<T>) cplxvec.crossProduct((Vector<ComplexType>) other);
        }
        Logger.getLogger(RowVector.class.getName()).log(Level.SEVERE, "No way to compute cross product for {0}.", clazz.getTypeName());
        throw new UnsupportedOperationException("Not supported yet");
    }

    @Override
    public Vector<T> normalize() {
        final Class<T> clazz = (Class<T>) ((Class) ((ParameterizedType) getClass()
                .getGenericSuperclass()).getActualTypeArguments()[0]);
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
        throw new UnsupportedOperationException("Not supported yet");
    }

    @Override
    public MathContext getMathContext() {
        return mctx;
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

//    @Override
//    public int hashCode() {
//        int hash = 7;
//        hash = 53 * hash + Arrays.deepHashCode(this.elements);
//        hash = 53 * hash + Objects.hashCode(this.mctx);
//        return hash;
//    }
    
    @Override
    public String toString() {
        // 202F = Narrow No-Break Space
        return stream().map(Object::toString).collect(Collectors.joining(", ", "[\u202F", "\u202F]"));
    }

    @Override
    public abstract Matrix<T> add(Matrix<T> addend);

    @Override
    public Matrix<T> multiply(Matrix<T> multiplier) {
        if (this.rows() != multiplier.columns()) {
            throw new ArithmeticException("Multiplier must have a single column");
        }

        // Apparently, the convention here is to compute the dot product of two vectors
        // and put the result into a 1x1 matrix.

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
