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
import tungsten.types.numerics.NumericHierarchy;
import tungsten.types.numerics.impl.Zero;
import tungsten.types.util.MathUtils;
import tungsten.types.util.OptionalOperations;
import tungsten.types.vector.RowVector;
import tungsten.types.vector.impl.ArrayRowVector;

import java.lang.reflect.Array;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A matrix implementation where the values of the matrix are given
 * by a generator function.
 *
 * @author Robert Poole <a href="mailto:Tarquin.AZ@gmail.com">Tarquin.AZ@gmail.com</a>
 * @param <T> the {@link Numeric} type of the elements contained by this matrix
 */
public class ParametricMatrix<T extends Numeric> implements Matrix<T> {
    private final long rows, columns;
    private final Generator<T> generatorFunction;
    
    protected ParametricMatrix(long rows, long columns, Generator<T> generatorFunction) {
        this.rows = rows;
        this.columns = columns;
        this.generatorFunction = generatorFunction;
    }
    
    /**
     * Create a matrix whose values are defined by a function taking two
     * {@link Long} arguments, the row and column indices of the matrix.
     * Note that if {@code function} is not idempotent, the behavior of
     * this matrix will be undefined.
     * 
     * @param rows the number of rows this matrix will have
     * @param columns the number of columns this matrix will have
     * @param function any function of two {@code Long} arguments that, when
     *     applied, gives a result of type {@link T}.
     */
    public ParametricMatrix(long rows, long columns, BiFunction<Long, Long, T> function) {
        this.rows = rows;
        this.columns = columns;
        this.generatorFunction = new Generator<>(function);
    }

    @Override
    public long columns() {
        return columns;
    }

    @Override
    public long rows() {
        return rows;
    }

    @Override
    public T valueAt(long row, long column) {
        return generatorFunction.apply(row, column);
    }

    @Override
    public T determinant() {
        if (rows() != columns()) {
            throw new ArithmeticException("Can only compute determinant for a square matrix");
        }
        if (rows() == 1L) { // 1x1 matrix
            return valueAt(0L, 0L);
        }
        if (rows() == 2L) {
            T a = valueAt(0L, 0L);
            T b = valueAt(0L, 1L);
            T c = valueAt(1L, 0L);
            T d = valueAt(1L, 1L);
            return (T) a.multiply(d).subtract(c.multiply(b));  // should not require coercion here
        }
        if (rows() > MathUtils.MAX_CLONE_DEPTH) {
            return new SubMatrix<>(this).determinant();
        }
        return new BasicMatrix<>(this).determinant();
    }

    @Override
    public Matrix<? extends Numeric> inverse() {
        if (rows() != columns()) {
            throw new ArithmeticException("Cannot invert a non-square matrix");
        }
        final T det = this.determinant();
        if (Zero.isZero(det)) {
            throw new ArithmeticException("Matrix is singular");
        }
        if (rows() == 1L) {
            return new SingletonMatrix<>(valueAt(0L, 0L).inverse());
        } else if (rows() == 2L) {
            final Numeric scale = det.inverse();
            T a = valueAt(0L, 0L);
            T b = valueAt(0L, 1L);
            T c = valueAt(1L, 0L);
            T d = valueAt(1L, 1L);
            BasicMatrix<Numeric> result = new BasicMatrix<>();
            result.append(new ArrayRowVector<>(d, b.negate()).scale(scale));
            result.append(new ArrayRowVector<>(c.negate(), a).scale(scale));
            return result;
        }
        if (rows() > MathUtils.MAX_CLONE_DEPTH) {
            return new SubMatrix<>(this).inverse();
        }
        return new BasicMatrix<>(this).inverse();
    }

    @Override
    public Matrix<T> add(Matrix<T> addend) {
        if (addend.rows() != rows || addend.columns() != columns) {
            throw new ArithmeticException("Addend must have the same dimensions as this matrix.");
        }
        
        final Class<T> clazz = (Class<T>) OptionalOperations.findTypeFor(addend);
        BasicMatrix<T> result = new BasicMatrix<>();
        for (long row = 0L; row < rows; row++) {
            T[] accum = (T[]) Array.newInstance(clazz, (int) columns);
            for (long column = 0L; column < columns; column++) {
                accum[(int) column] = (T) generatorFunction.apply(row, column).add(addend.valueAt(row, column));
            }
            result.append(accum);
        }
        return result;
    }

    @Override
    public Matrix<T> multiply(Matrix<T> multiplier) {
        if (columns != multiplier.rows()) {
            throw new ArithmeticException("Multiplier must have the same number of rows as this matrix has columns");
        }
        
        final Class<T> clazz = (Class<T>) OptionalOperations.findTypeFor(multiplier);
        T[][] temp = (T[][]) Array.newInstance(clazz, (int) rows, (int) multiplier.columns());
        for (long row = 0L; row < rows; row++) {
            RowVector<T> rowvec = getRow(row);  // the default implementation should be performant enough for this
            for (long column = 0L; column < multiplier.columns(); column++) {
                temp[(int) row][(int) column] = rowvec.dotProduct(multiplier.getColumn(column));
            }
        }
        return new BasicMatrix<>(temp);
    }
    
    @Override
    public Matrix<T> scale(T scaleFactor) {
        final BiFunction<Long, Long, T> scaleFunction =
                generatorFunction.internal.andThen(x -> (T) x.multiply(scaleFactor));
        return new ParametricMatrix<>(rows, columns, scaleFunction);
    }
    
    @Override
    public Matrix<T> transpose() {
        final Generator<T> transGenerator = new Generator<>((row, column) -> generatorFunction.internal.apply(column, row));
        return new ParametricMatrix<>(columns, rows, transGenerator);
    }
    
    public <R extends Numeric> Matrix<R> upconvert(Class<R> clazz) {
        // first, check to make sure we can do this -- ensure R is a wider type than T
        NumericHierarchy targetType = NumericHierarchy.forNumericType(clazz);
        Class<T> currentClazz = getRow(0L).getElementType();
        if (currentClazz != null) {
            NumericHierarchy currentType = NumericHierarchy.forNumericType(currentClazz);
            // if our elements are already of the requested type, just cast and return
            if (currentType == targetType) return (Matrix<R>) this;
            if (currentType.compareTo(targetType) > 0) {
                throw new ArithmeticException("Cannot upconvert elements of " + currentType + " to elements of " + targetType);
            }
        } else {
            Logger.getLogger(ParametricMatrix.class.getName()).log(Level.WARNING,
                    "Unable to determine actual type argument for {0}. Skipping sanity checks to ensure we can upconvert to element type {1}.",
                    new Object[] {this.getClass().getTypeName(), clazz.getTypeName()});
        }
        
        // for efficiency, unwrapping the internal function of the generator so that we're not range checking twice
        final BiFunction<Long, Long, R> upfunction = generatorFunction.internal.andThen(x -> {
            try {
                return (R) x.coerceTo(clazz);
            } catch (CoercionException ex) {
                // we should never get here
                throw new IllegalStateException(ex);
            }
        });
        return new ParametricMatrix<>(rows, columns, upfunction);
    }
    
    @Override
    public boolean equals(Object o) {
        if (o instanceof Matrix<? extends Numeric> that) {
            if (this.rows() != that.rows() || this.columns() != that.columns()) return false;
            for (long row = 0L; row < rows(); row++) {
                for (long column = 0L; column < columns(); column++) {
                    if (!valueAt(row, column).equals(that.valueAt(row, column))) return false;
                }
            }
            return true;
        }
        return false;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 29 * hash + Long.hashCode(this.rows);
        hash = 29 * hash + Long.hashCode(this.columns);
        hash = 29 * hash + Objects.hashCode(this.generatorFunction);
        return hash;
    }
    
    protected class Generator<T> implements BiFunction<Long, Long, T> {
        /**
         * This is an internal copy of the {@link BiFunction} that this
         * {@code Generator} wraps.  Subclasses should initialize this
         * explicitly if not calling the superclass constructor.
         */
        protected BiFunction<Long, Long, T> internal;
        
        /**
         * Default constructor lets us wrap any {@link BiFunction} that
         * takes two {@link Long} arguments, the row and column indices.
         * @param function a {@link BiFunction} that maps row and column
         *                indices to values
         */
        public Generator(BiFunction<Long, Long, T> function) {
            internal = function;
        }

        @Override
        public T apply(Long row, Long column) {
            if (row < 0L || row >= rows || column < 0L || column >= columns) {
                throw new IndexOutOfBoundsException(String.format("Indices %d, %d are out of bounds for Matrix with dimensions %d, %d",
                        row, column, rows, columns));
            }
            return internal.apply(row, column);
        }
    }
}
