/*
 * The MIT License
 *
 * Copyright © 2024 Robert Poole <Tarquin.AZ@gmail.com>.
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
 *
 */

package tungsten.types.matrix.impl;

import tungsten.types.Matrix;
import tungsten.types.Numeric;
import tungsten.types.exceptions.CoercionException;
import tungsten.types.numerics.ComplexType;
import tungsten.types.numerics.IntegerType;
import tungsten.types.numerics.impl.ExactZero;
import tungsten.types.numerics.impl.IntegerImpl;
import tungsten.types.numerics.impl.One;
import tungsten.types.numerics.impl.Zero;
import tungsten.types.util.MathUtils;
import tungsten.types.util.UnicodeTextEffects;
import tungsten.types.vector.impl.ArrayRowVector;

import java.lang.reflect.Array;
import java.math.BigInteger;
import java.math.MathContext;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class JordanMatrix<T extends Numeric> implements Matrix<T> {
    private final Class<T> clazz;
    private T one, zero;
    private final JordanBlock[] diagBlocks;

    public JordanMatrix(JordanBlock[] blocks) {
        this.diagBlocks = blocks;
        clazz = (Class<T>) blocks[0].getLambda().getClass();
        generateInternalValues();
    }

    public JordanMatrix(T[] lambdas, long[] blockDimensions) {
        if (lambdas.length != blockDimensions.length) {
            throw new IllegalArgumentException("Lambda values and block sizes must be of the same dimension");
        }
        diagBlocks = (JordanBlock[]) Array.newInstance(JordanBlock.class, lambdas.length);
        for (int i = 0; i < lambdas.length; i++) {
            diagBlocks[i] = new JordanBlock(lambdas[i], blockDimensions[i]);
        }
        clazz = (Class<T>) lambdas.getClass().getComponentType();
        generateInternalValues();
    }

    private void generateInternalValues() {
        List<T> values = Arrays.stream(diagBlocks).map(JordanBlock::getLambda).collect(Collectors.toList());
        final MathContext ctx = MathUtils.inferMathContext(values);
        try {
            one = (T) One.getInstance(ctx).coerceTo(clazz);
            zero = (T) ExactZero.getInstance(ctx).coerceTo(clazz);
        } catch (CoercionException e) {
            throw new IllegalStateException("While setting up internal state of a Jordan matrix", e);
        }
    }

    @Override
    public long columns() {
        return Arrays.stream(diagBlocks).mapToLong(Matrix::columns).sum();
    }

    @Override
    public long rows() {
        return Arrays.stream(diagBlocks).mapToLong(Matrix::rows).sum();
    }

    @Override
    public T valueAt(long row, long column) {
        if (row < 0L || row >= rows() || column < 0L || column >= columns()) {
            throw new IndexOutOfBoundsException("Row/column indices are out of bounds");
        }
        int index = 0;
        JordanBlock block = diagBlocks[0];
        while (row >= block.rows() || column >= block.columns()) {
            if (++index >= diagBlocks.length) break;
            row -= block.rows();
            column -= block.columns();
            if (row < 0L || column < 0L) break;
            block = diagBlocks[index];
        }
        if (row >= 0L && row < block.rows() && column >= 0L && column < block.columns()) {
            return block.valueAt(row, column);
        }
        return zero;
    }

    @Override
    public T determinant() {
        return Arrays.stream(diagBlocks).map(Matrix::determinant)
                .reduce(one, (x, y) -> (T) x.multiply(y));
    }

    @Override
    public Matrix<? extends Numeric> inverse() {
        Matrix<? extends Numeric>[] invblocks = (Matrix<Numeric>[]) new Matrix[diagBlocks.length];
        for (int k = 0; k < diagBlocks.length; k++) {
            invblocks[k] = diagBlocks[k].inverse();
        }
        return AggregateMatrix.blockDiagonal(invblocks);
    }

    @Override
    public boolean isUpperTriangular() {
        return true;
    }

    @Override
    public boolean isLowerTriangular() {
        // check whether any of the diagonal blocks is at least 2×2
        return Arrays.stream(diagBlocks).mapToLong(JordanBlock::rows)
                .noneMatch(x -> x > 1L);
    }

    @Override
    public T trace() {
        return Arrays.stream(diagBlocks).map(Matrix::trace)
                .reduce(zero, (x, y) -> (T) x.add(y));
    }

    @Override
    public Matrix<T> add(Matrix<T> addend) {
        return new BasicMatrix<>(this).add(addend);
    }

    @Override
    public Matrix<T> multiply(Matrix<T> multiplier) {
        return new BasicMatrix<>(this).multiply(multiplier);
    }

    @Override
    public Matrix<T> scale(T scaleFactor) {
        return new ParametricMatrix<>(rows(), columns(),
                (row, column) -> (T) valueAt(row, column).multiply(scaleFactor));
    }

    @Override
    public int hashCode() {
        return Objects.hash(clazz, Arrays.hashCode(diagBlocks));
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof JordanMatrix) {
            JordanMatrix<? extends Numeric> that = (JordanMatrix<? extends Numeric>) obj;
            if (that.diagBlocks.length != this.diagBlocks.length) return false;
            for (int k = 0; k < diagBlocks.length; k++) {
                if (!that.diagBlocks[k].equals(this.diagBlocks[k])) return false;
            }
            return true;
        } else if (obj instanceof Matrix) {
            Matrix<? extends Numeric> that = (Matrix<? extends Numeric>) obj;
            if (that.rows() != this.rows() || that.columns() != this.columns()) return false;
            // this is lazy, but does leverage the logic of BasicMatrix
            return new BasicMatrix<>(this).equals(obj);
        }
        return false;
    }

    @Override
    public String toString() {
        // U+2295 circle-plus (direct sum, Kronecker sum)
        // U+205F medium mathematical space
        return Arrays.stream(diagBlocks).map(JordanBlock::toString)
                .collect(Collectors.joining("\u205F\u2295\u205F"));
    }

    public class JordanBlock extends ParametricMatrix<T> {
        private final T lambda;

        public JordanBlock(T lambda, long n) {
            super(n, n, (row, column) -> {
                if (row.longValue() == column.longValue()) return lambda;
                if (row == column - 1L) return one;
                return zero;
            });
            this.lambda = lambda;
        }

        public T getLambda() { return lambda; }

        @Override
        public Matrix<? extends Numeric> inverse() {
            if (Zero.isZero(lambda)) throw new ArithmeticException("Jordan block cannot be inverted");
            final MathContext ctx = lambda.getMathContext();
            BasicMatrix<Numeric> result = new BasicMatrix<>();
            for (long row = 0L; row < rows(); row++) {
                Numeric[] rowValues = new Numeric[(int) columns()];
                for (int col = 0; col < row; col++) {
                    rowValues[col] = zero;
                }
                // now compute the remainder of the row
                for (long col = row; col < columns(); col++) {
                    long n = col - row + 1L;
                    Numeric intermediate = lambda instanceof ComplexType ?
                            MathUtils.computeIntegerExponent((ComplexType) lambda, n, ctx) :
                            MathUtils.computeIntegerExponent(lambda, new IntegerImpl(BigInteger.valueOf(n)));
                    intermediate = intermediate.inverse();
                    if (n % 2L == 0L) intermediate = intermediate.negate();
                    rowValues[(int) col] = intermediate;
                }
                result.append(new ArrayRowVector<>(rowValues));
            }
            return result;
        }

        @Override
        public T trace() {
            IntegerType n = new IntegerImpl(BigInteger.valueOf(rows())) {
                @Override
                public MathContext getMathContext() {
                    return lambda.getMathContext();
                }
            };
            try {
                return (T) lambda.multiply(n).coerceTo(clazz);
            } catch (CoercionException e) {
                throw new IllegalStateException("While computing trace", e);
            }
        }

        @Override
        public T determinant() {
            IntegerType n = new IntegerImpl(BigInteger.valueOf(rows()));
            Numeric intermediate = lambda instanceof ComplexType ?
                    MathUtils.computeIntegerExponent((ComplexType) lambda, n) :
                    MathUtils.computeIntegerExponent(lambda, n);
            try {
                return (T) intermediate.coerceTo(clazz);
            } catch (CoercionException e) {
                throw new IllegalStateException("Intermediate determinant value: " + intermediate, e);
            }
        }

        @Override
        public boolean equals(Object o) {
            // instanceof will not work here because JordanBlock is generic
            if (JordanBlock.class.isInstance(o)) {
                JordanBlock block = (JordanBlock) o;
                return block.getLambda().equals(lambda) &&
                        block.columns() == this.columns();
            }
            return super.equals(o);
        }

        @Override
        public int hashCode() {
            // the only values that define the Jordan block are the
            // lambda value and n (the dimension of this square matrix)
            return Objects.hash(lambda, this.columns());
        }

        @Override
        public String toString() {
            // U+1D43D = mathematical italic capital J
            return "\uD835\uDC3D" + UnicodeTextEffects.numericSubscript((int) rows())
                    + "(" + lambda + ")";
        }
    }
}
