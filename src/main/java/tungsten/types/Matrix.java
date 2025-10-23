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

import tungsten.types.annotations.Columnar;
import tungsten.types.exceptions.CoercionException;
import tungsten.types.matrix.impl.IdentityMatrix;
import tungsten.types.matrix.impl.ZeroMatrix;
import tungsten.types.numerics.*;
import tungsten.types.numerics.impl.RealImpl;
import tungsten.types.numerics.impl.Zero;
import tungsten.types.util.ClassTools;
import tungsten.types.util.MathUtils;
import tungsten.types.util.OptionalOperations;
import tungsten.types.vector.ColumnVector;
import tungsten.types.vector.RowVector;
import tungsten.types.vector.impl.ArrayColumnVector;
import tungsten.types.vector.impl.ArrayRowVector;

import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.util.concurrent.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.LongStream;
import java.util.stream.Stream;

/**
 * The root type for <a href="https://en.wikipedia.org/wiki/Matrix_(mathematics)">matrices</a>.
 *
 * @author Robert Poole <a href="mailto:Tarquin.AZ@gmail.com">Tarquin.AZ@gmail.com</a>
 * @param <T> the {@link Numeric} type of the elements of this matrix 
 */
public interface Matrix<T extends Numeric> {
    /**
     * Environment variable determining whether to use the more
     * computationally expensive Frobenius norm instead of the
     * max norm.
     */
    String USE_FROBENIUS_NORM = "tungsten.types.Matrix.useFrobenius";
    /**
     * The multiplication sign to be used for rendering.
     */
    String MULT_SIGN = "\u00D7";

    /**
     * Obtain the number of columns in this matrix.
     * @return the number of columns, a positive integer value
     */
    long columns();

    /**
     * Obtain the number of rows in this matrix.
     * @return the number of rows, a positive integer value
     */
    long rows();

    /**
     * Obtain the value at a given row and column.
     * @param row    a row index
     * @param column a column index
     * @return the value at {@code row} and {@code column}
     */
    T valueAt(long row, long column);

    /**
     * Obtain the determinant of this matrix.
     * @return the determinant
     */
    T determinant();

    /**
     * Obtain the inverse of this matrix.
     * @return the inverse
     */
    Matrix<? extends Numeric> inverse();

    /**
     * Test whether this matrix is upper triangular.
     * @return true if this matrix is upper triangular, false otherwise
     * @apiNote The default implementation checks whether the elements
     *   in the lower triangle are all zero.
     */
    default boolean isUpperTriangular() {
        if (columns() != rows()) return false;
        if (rows() == 1L) return false;  // singleton matrix can't really be either upper or lower triangular

        for (long row = 1L; row < rows(); row++) {
            for (long column = 0L; column < columns() - (rows() - row); column++) {
                if (!Zero.isZero(valueAt(row, column))) return false;
            }
        }
        return true;
    }

    /**
     * Test whether this matrix is lower triangular.
     * @return true if this matrix is lower triangular, false otherwise
     * @apiNote The default implementation checks whether the elements
     *   in the upper triangle are all zero.
     */
    default boolean isLowerTriangular() {
        if (columns() != rows()) return false;
        if (rows() == 1L) return false;  // singleton matrix can't really be either upper or lower triangular

        for (long row = 0L; row < rows() - 1L; row++) {
            for (long column = row + 1L; column < columns(); column++) {
                if (!Zero.isZero(valueAt(row, column))) return false;
            }
        }
        return true;
    }

    /**
     * Determine whether this matrix is upper <em>or</em> lower
     * triangular.
     * @return true if this matrix satisfies the requirements for
     *   upper or lower triangularity
     * @apiNote Because the tests for triangularity can be slow or
     *   computationally expensive, the default implementation
     *   is multithreaded and checks both upper and lower triangles
     *   simultaneously.
     */
    default boolean isTriangular() {
        final ExecutorService executor = Executors.newFixedThreadPool(2);
        Callable<Boolean> isLower = this::isLowerTriangular;
        Callable<Boolean> isUpper = this::isUpperTriangular;
        Future<Boolean> lowResult = executor.submit(isLower);
        Future<Boolean> upResult = executor.submit(isUpper);
        try {
            // get() will block until the result is available
            if (lowResult.get() || upResult.get()) return true;
        } catch (InterruptedException ie) {
            // log a warning, but let this method return false for now
            Logger.getLogger(Matrix.class.getName()).log(Level.WARNING, "isTriangular() calculation was interrupted.", ie);
        } catch (ExecutionException exEx) {
            Logger.getLogger(Matrix.class.getName()).log(Level.SEVERE, "Execution of one or both triangularity tests failed.", exEx);
            throw new IllegalStateException(exEx);
        } finally {
            executor.shutdownNow();
        }
        return false;
        // the parallel stream version below is not guaranteed to run in parallel,
        // which sort of defeats the purpose since we have two roughly equal workloads...
//        Predicate<tungsten.types.Matrix<T>> isLower = (tungsten.types.Matrix<T> t) -> t.isLowerTriangular();
//        Predicate<tungsten.types.Matrix<T>> isUpper = (tungsten.types.Matrix<T> t) -> t.isUpperTriangular();
//        return Arrays.asList(isLower, isUpper).parallelStream()
//                .<Boolean> map(p -> p.test(this))
//                .anyMatch(x -> x.booleanValue());
        // the naïve version below might be easier to read and understand,
        // but for big matrices, you really want to do these checks in parallel
        // if you possibly can
//        return isLowerTriangular() || isUpperTriangular();
    }

    /**
     * Obtain the trace Tr(<strong>M</strong>) of this matrix <strong>M</strong>.
     * The trace is the sum of the diagonal elements of <strong>M</strong>.
     * @return the trace if this matrix
     */
    default T trace() {
        if (this.columns() != this.rows()) {
            throw new ArithmeticException("Trace is only defined for square matrices");
        }
        Numeric accum = valueAt(0L, 0L);
        final Class<T> clazz = (Class<T>) OptionalOperations.findTypeFor(this); // safer than accum.getClass()
        for (long index = 1L; index < this.columns(); index++) {
            accum = accum.add(valueAt(index, index));
        }
        try {
            return (T) accum.coerceTo(clazz);
        } catch (CoercionException ex) {
            Logger.getLogger(Matrix.class.getName()).log(Level.SEVERE,
                    "Could not coerce " + accum + " to " + clazz.getTypeName(), ex);
            throw new ArithmeticException("Type coercion failed");
        }
    }

    /**
     * Obtain the transpose <strong>M</strong><sup>T</sup> of this
     * matrix, <strong>M</strong>.  The transpose exchanges
     * columns and rows.
     * @return the transpose of this matrix
     */
    default Matrix<T> transpose() {
        final long rows = this.columns();
        final long columns = this.rows();
        final Matrix<T> source = this;
        
        return new Matrix<>() {
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
                if (row < 0L || row >= rows || column < 0L || column >= columns) {
                    throw new IndexOutOfBoundsException("row:" + row + ", column:" + column +
                            " is out of bounds for a " + rows + MULT_SIGN + columns + " matrix");
                }
                return source.valueAt(column, row);
            }

            @Override
            public T determinant() {
                // the determinant of the transpose is the same as the determinant
                // of the original matrix
                return source.determinant();
            }

            @Override
            public Matrix<T> add(Matrix<T> addend) {
                // delegate to the add method of the addend if possible
                // since matrix addition is commutative
                if (!addend.getClass().isAnonymousClass()) {
                    return addend.add(this);
                }
                // (A + B)^T = A^T + B^T
                return source.add(addend.transpose()).transpose();
            }

            @Override
            public Matrix<T> multiply(Matrix<T> multiplier) {
                // (A*B)^T = B^T * A^T
                return multiplier.transpose().multiply(source).transpose();
            }

            @Override
            public Matrix<T> transpose() {
                return source;
            }

            @Override
            public Matrix<? extends Numeric> inverse() {
                // the inverse of the transpose is the transpose of the inverse of the original matrix
                return source.inverse().transpose();
            }

            @Override
            public Matrix<T> scale(T scaleFactor) {
                return source.scale(scaleFactor).transpose();
            }

            @Override
            public RowVector<T> getRow(long row) {
                return source.getColumn(row).transpose();
            }

            @Override
            public ColumnVector<T> getColumn(long column) {
                return source.getRow(column).transpose();
            }
        };
    }

    /**
     * Add the supplied matrix to this matrix.  If this matrix has
     * elements A<sub>j,k</sub> and the addend has elements
     * B<sub>j, k</sub>, the resulting matrix M has elements
     * A<sub>j,k</sub>&nbsp;+&nbsp;B<sub>j,k</sub>.
     * @param addend the matrix to be added to {@code this}
     * @return a {@code Matrix} that is the sum of {@code this} and {@code addend}
     */
    Matrix<T> add(Matrix<T> addend);
    /**
     * Multiply the supplied matrix with this matrix.  The standard
     * rules of matrix multiplication applies.
     * @param multiplier the matrix to be multiplied with {@code this}
     * @return a {@code Matrix} that is the product of {@code this} and {@code multiplier}
     * @throws ArithmeticException if {@code this.columns() != multiplier.rows()}
     */
    Matrix<T> multiply(Matrix<T> multiplier);

    /**
     * Given a scalar value, scale this matrix by that value.
     * The resulting matrix consists of elements that are those
     * of the original matrix multiplied by the scalar.
     * @param scaleFactor a scalar value
     * @return the scaled matrix
     */
    Matrix<T> scale(T scaleFactor);

    /**
     * Convenience method to determine whether to use the
     * max norm or the Frobenius norm.
     * @return true if Frobenius should be used, false otherwise
     */
    default boolean useFrobeniusNorm() {
        return Boolean.getBoolean(USE_FROBENIUS_NORM);
    }

    /**
     * Compute the norm for this matrix.  The norm is analogous to
     * magnitude, and in practice there are many ways to compute the
     * norm for a matrix.  By default, this method computes the
     * max norm and returns it as a positive {@link RealType real}
     * value.  If the environment variable {@link #USE_FROBENIUS_NORM}
     * is set to {@code true}, the Frobenius norm is computed instead.
     * <br>Any classes implementing this interface which override
     * this default implementation <strong>must</strong> handle computing both types
     * of norm; it is recommended that implementing classes use {@link #useFrobeniusNorm()}
     * to make this determination rather than rolling their own.
     * @return the norm for this matrix
     * @see #USE_FROBENIUS_NORM
     * @see <a href="https://en.wikipedia.org/wiki/Matrix_norm">the Wikipedia article on matrix norms</a>
     */
    default RealType norm() {
        final Stream<Numeric> allValues = LongStream.range(0L, rows())
                .mapToObj(this::getRow).flatMap(RowVector::stream)
                .map(Numeric::magnitude).map(Numeric.class::cast);
        if (useFrobeniusNorm()) {
            Numeric sumOfSquares = allValues.map(x -> x.multiply(x)).reduce(Numeric::add).map(Numeric::sqrt)
                    .orElseThrow(() -> new ArithmeticException("No result while computing Frobenius norm"));
            try {
                return (RealType) sumOfSquares.coerceTo(RealType.class);
            } catch (CoercionException e) {
                throw new IllegalStateException("While computing the Frobenius norm", e);
            }
        } else {
            // use max norm
            Numeric maxValue = allValues
                    .max(MathUtils.obtainGenericComparator())
                    .orElseThrow(() -> new ArithmeticException("Unable to compute max norm"));
            try {
                return (RealType) maxValue.coerceTo(RealType.class);
            } catch (CoercionException e) {
                throw new IllegalStateException("While computing the matrix norm", e);
            }
        }
    }

    /**
     * Compute this matrix raised to an exponent.
     * @param n the exponent
     * @return a matrix that is this matrix raised to the {@code n}<sup>th</sup> power
     */
    default Matrix<? extends Numeric> pow(Numeric n) {
        if (n instanceof RationalType exponent) {
            Matrix<Numeric> intermediate = (Matrix<Numeric>) pow(exponent.numerator());
            if (exponent.denominator().isPowerOf2()) {
                long denom = exponent.denominator().asBigInteger().longValueExact();
                while (denom > 1L) {
                    intermediate = (Matrix<Numeric>) MathUtils.sqrt(intermediate);
                    denom >>= 1L;
                }
                return intermediate;
            }
            if (this.isUpperTriangular()) {
                // let's try the Parlett method -- an upper triangular matrix raised to a power is upper triangular
                return MathUtils.nthRoot(intermediate, exponent.denominator());
            }
            // TODO handle rational exponents with denominators that are not powers of 2 for non-real base types
            throw new ArithmeticException("Rational exponents must be of the form m/2\u207F, but received " + exponent);
        }
        // if we didn't get special handling above, n must be an integer
        if (!(n instanceof IntegerType)) {
            throw new IllegalArgumentException("Non-integer exponents other than of the form m/2\u207F are not supported for this type of matrix");
        }
        if (OptionalOperations.sign(n) == Sign.NEGATIVE) {
            return inverse().pow(n.negate());
        }
        if (rows() != columns()) throw new ArithmeticException("Cannot compute power of non-square matrix");
        BigInteger exponent = ((IntegerType) n).asBigInteger();
        final MathContext mctx = valueAt(0L, 0L).getMathContext();
        if (exponent.equals(BigInteger.ZERO)) return new IdentityMatrix(rows(), mctx);
        if (exponent.equals(BigInteger.ONE)) return this;
        
        // Otherwise, iteratively compute this matrix raised to the n power
        // using exponentiation by squares, which is more efficient.
        Matrix<Numeric> x = (Matrix<Numeric>) this;
        Matrix<Numeric> y = new IdentityMatrix(rows(), mctx);
        while (exponent.compareTo(BigInteger.ONE) > 0) {
            if (exponent.mod(BigInteger.TWO).equals(BigInteger.ZERO)) {
                // even case
                x = x.multiply(x);
            } else {
                // odd case
                y = x.multiply(y);
                x = x.multiply(x);
                // no need to perform subtraction here, bit shift does it all
            }
            exponent = exponent.shiftRight(1); // bit shift instead of division by 2
        }
        return x.multiply(y);
    }

    /**
     * Subtract the supplied matrix from this matrix.  If this matrix has
     * elements A<sub>j,k</sub> and the subtrahend has elements
     * B<sub>j,k</sub>, the resulting matrix M has elements
     * A<sub>j,k</sub>&nbsp;&minus;&nbsp;B<sub>j,k</sub>.
     * @param subtrahend the matrix to be added to {@code this}
     * @return a {@code Matrix} that is the difference between {@code this} and {@code subtrahend}
     */
    default Matrix<T> subtract(Matrix<T> subtrahend) {
        if (subtrahend.rows() != this.rows() || subtrahend.columns() != this.columns()) {
            throw new IllegalArgumentException("Matrix dimensions are mismatched");
        }
        if (ZeroMatrix.isZeroMatrix(subtrahend)) {
            // A-0 = A
            return this;
        }
        if (IdentityMatrix.isIdentityMatrix(subtrahend)) {
            // optimized calculation of A-I
            return MathUtils.calcAminusI(this);
        }
        Class<T> clazz = (Class<T>) OptionalOperations.findTypeFor(subtrahend); // safer than subtrahend.valueAt(0L, 0L).getClass()
        final MathContext ctx = subtrahend.getClass().isAnnotationPresent(Columnar.class) ?
                subtrahend.getColumn(0L).getMathContext() :
                subtrahend.getRow(0L).getMathContext();
        if (clazz == null || clazz == Numeric.class) {
            Logger.getLogger(Matrix.class.getName()).log(Level.FINE,
                    "Subtrahend matrix elements are of an abstract Numeric type; using lhs element type instead.");
            clazz = (Class<T>) OptionalOperations.findTypeFor(this);
        }
        try {
            final T negOne = (T) new RealImpl(BigDecimal.valueOf(-1L), ctx).coerceTo(clazz);
            return this.add(subtrahend.scale(negOne));
        } catch (CoercionException ce) {
            throw new IllegalStateException("While subtracting a " +
                    subtrahend.rows() + MULT_SIGN + subtrahend.columns() + " matrix", ce);
        }
    }

    /**
     * Obtain a row vector from this matrix for a given row index.
     * @param row the index of the row to obtain
     * @return a {@link RowVector} containing the elements of row {@code row}
     */
    default RowVector<T> getRow(long row) {
        Class<? extends Numeric> pclass = LongStream.range(0L, columns()).mapToObj(col -> valueAt(row, col))
                .map(Numeric::getClass).max(NumericHierarchy.obtainTypeComparator())
                .orElseThrow(() -> new IllegalStateException("Row " + row + " has no elements to compare"));
        final Class<T> clazz = (Class<T>) ClassTools.getInterfaceTypeFor(pclass);
        T[] temp = (T[]) Array.newInstance(clazz, (int) columns());
        try {
            for (int i = 0; i < columns(); i++) {
                Numeric value = valueAt(row, i);
                // guard against any incompatible types sneaking through
                if (!clazz.isAssignableFrom(value.getClass())) value = value.coerceTo(clazz);
                temp[i] = (T) value;
            }
        } catch (CoercionException e) {
            throw new IllegalStateException("While obtaining row " + row, e);
        }
        return new ArrayRowVector<>(temp);
    }

    /**
     * Obtain a column vector from this matrix for a given column index.
     * @param column the index of the row to obtain
     * @return a {@link ColumnVector} containing the elements of column {@code column}
     */
    default ColumnVector<T> getColumn(long column) {
        Class<? extends Numeric> pclass = LongStream.range(0L, rows()).mapToObj(row -> valueAt(row, column))
                .map(Numeric::getClass).max(NumericHierarchy.obtainTypeComparator())
                .orElseThrow(() -> new IllegalStateException("Column " + column + " has no elements to compare"));
        final Class<T> clazz = (Class<T>) ClassTools.getInterfaceTypeFor(pclass);
        T[] temp = (T[]) Array.newInstance(clazz, (int) rows());
        try {
            for (int j = 0; j < rows(); j++) {
                Numeric value = valueAt(j, column);
                if (!clazz.isAssignableFrom(value.getClass())) value = value.coerceTo(clazz);
                temp[j] = (T) value;
            }
        } catch (CoercionException e) {
            throw new IllegalStateException("While obtaining column " + column, e);
        }
        return new ArrayColumnVector<>(temp);
    }

    /*
     Methods necessary for Groovy operator overloading follow.
     */
    default Matrix<T> plus(Matrix<T> operand) {
        return this.add(operand);
    }
    default Matrix<T> minus(Matrix<T> operand) {
        return this.subtract(operand);
    }
    default Matrix<? extends Numeric> power(Numeric operand) {
        return this.pow(operand);
    }
}
