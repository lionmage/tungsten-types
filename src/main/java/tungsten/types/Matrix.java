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
import tungsten.types.matrix.impl.IdentityMatrix;
import tungsten.types.numerics.IntegerType;
import tungsten.types.numerics.RationalType;
import tungsten.types.numerics.RealType;
import tungsten.types.numerics.Sign;
import tungsten.types.numerics.impl.IntegerImpl;
import tungsten.types.numerics.impl.Zero;
import tungsten.types.util.ClassTools;
import tungsten.types.util.MathUtils;
import tungsten.types.util.OptionalOperations;
import tungsten.types.vector.ColumnVector;
import tungsten.types.vector.RowVector;
import tungsten.types.vector.impl.ArrayColumnVector;
import tungsten.types.vector.impl.ArrayRowVector;

import java.lang.reflect.Array;
import java.math.BigInteger;
import java.math.MathContext;
import java.util.concurrent.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.LongStream;
import java.util.stream.Stream;

/**
 * The root type for matrices.
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

    long columns();
    long rows();
    T valueAt(long row, long column);
    T determinant();
    Matrix<? extends Numeric> inverse();
    
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
            Logger.getLogger(Matrix.class.getName()).log(Level.WARNING, "isTriangular() calculation was interrupted", ie);
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
        // the naive version below might be easier to read and understand,
        // but for big matrices, you really want to do these checks in parallel
        // if you possibly can
//        return isLowerTriangular() || isUpperTriangular();
    }
    
    default T trace() {
        if (this.columns() != this.rows()) {
            throw new ArithmeticException("Trace is only defined for square matrices.");
        }
        Numeric accum = valueAt(0L, 0L);
        final Class<T> clazz = (Class<T>) accum.getClass();
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
                            " is out of bounds for a " + rows + " by " + columns + " matrix");
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
    
    Matrix<T> add(Matrix<T> addend);
    Matrix<T> multiply(Matrix<T> multiplier);
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
     * <br/>Any classes implementing this interface which override
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
            Numeric maxValue = allValues
                    .max(MathUtils.obtainGenericComparator()).orElseThrow();
            try {
                return (RealType) maxValue.coerceTo(RealType.class);
            } catch (CoercionException e) {
                throw new IllegalStateException("While computing the matrix norm", e);
            }
        }
    }
    
    default Matrix<? extends Numeric> pow(Numeric n) {
        if (n instanceof RationalType) {
            RationalType exponent = (RationalType) n;
            if (exponent.denominator().isPowerOf2()) {
                Matrix<Numeric> intermediate = (Matrix<Numeric>) pow(exponent.numerator());
                long denom = exponent.denominator().asBigInteger().longValueExact();
                while (denom > 1L) {
                    intermediate = (Matrix<Numeric>) MathUtils.sqrt(intermediate);
                    denom >>= 1L;
                }
                return intermediate;
            }
            // TODO handle rational exponents with denominators that are not powers of 2
            throw new ArithmeticException("Rational exponents must be of the form m/2\u207F, but received " + exponent);
        }
        // if we didn't get special handling above, n must be an integer
        if (!(n instanceof IntegerType)) {
            throw new IllegalArgumentException("Non-integer exponents other than of the form m/2\u207F are not allowed for this type of matrix");
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
                exponent = exponent.shiftRight(1); // divide(TWO);
            } else {
                // odd case
                y = x.multiply(y);
                x = x.multiply(x);
                exponent = exponent.subtract(BigInteger.ONE).shiftRight(1); // divide(TWO);
            }
        }
        return x.multiply(y);
    }

    default Matrix<T> subtract(Matrix<T> subtrahend) {
        final Class<T> clazz = (Class<T>) subtrahend.valueAt(0L, 0L).getClass();
        try {
            final T negOne = (T) new IntegerImpl(BigInteger.valueOf(-1L)).coerceTo(clazz);
            return this.add(subtrahend.scale(negOne));
        } catch (CoercionException ce) {
            throw new IllegalStateException(ce);
        }
    }
    
    default RowVector<T> getRow(long row) {
        Class<T> clazz = (Class<T>) ClassTools.getInterfaceTypeFor(valueAt(row, 0L).getClass());
        T[] temp = (T[]) Array.newInstance(clazz, (int) columns());
        for (int i = 0; i < columns(); i++) {
            temp[i] = valueAt(row, i);
        }
        return new ArrayRowVector<>(temp);
    }
    
    default ColumnVector<T> getColumn(long column) {
        Class<T> clazz = (Class<T>) ClassTools.getInterfaceTypeFor(valueAt(0L, column).getClass());
        T[] temp = (T[]) Array.newInstance(clazz, (int) rows());
        for (int j = 0; j < rows(); j++) {
            temp[j] = valueAt(j, column);
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
