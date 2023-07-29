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
import tungsten.types.numerics.RealType;
import tungsten.types.numerics.Sign;
import tungsten.types.numerics.impl.ExactZero;
import tungsten.types.numerics.impl.RealImpl;
import tungsten.types.numerics.impl.Zero;
import tungsten.types.util.OptionalOperations;
import tungsten.types.vector.ColumnVector;
import tungsten.types.vector.RowVector;
import tungsten.types.vector.impl.ListColumnVector;
import tungsten.types.vector.impl.ListRowVector;
import tungsten.types.vector.impl.ZeroVector;

import java.math.BigDecimal;
import java.math.MathContext;

/**
 * A representation of the zero matrix, of which all elements
 * are zero (0).
 *
 * @author Robert Poole <a href="mailto:Tarquin.AZ@gmail.com">Tarquin.AZ@gmail.com</a>
 */
public class ZeroMatrix extends ParametricMatrix<Numeric> {
    private final MathContext mctx;
    
    public ZeroMatrix(long size, MathContext mctx) {
        super(size, size, (row, column) -> ExactZero.getInstance(mctx));
        this.mctx = mctx;
    }
    
    public ZeroMatrix(long rows, long columns, MathContext mctx) {
        super(rows, columns, (row, column) -> ExactZero.getInstance(mctx));
        this.mctx = mctx;
    }
    
    @Override
    public RowVector<Numeric> getRow(long row) {
        return new ListRowVector<>(ZeroVector.getInstance(this.columns(), mctx));
    }
    
    @Override
    public ColumnVector<Numeric> getColumn(long column) {
        return new ListColumnVector<>(ZeroVector.getInstance(this.rows(), mctx));
    }
    
    @Override
    public Matrix<Numeric> add(Matrix<Numeric> addend) {
        if (addend.rows() != this.rows() || addend.columns() != this.columns()) {
            throw new ArithmeticException("Dimensional mismatch with addend");
        }
        return addend;
    }
    
    @Override
    public Matrix<Numeric> multiply(Matrix<Numeric> multiplier) {
        if (this.columns() != multiplier.rows()) {
            throw new ArithmeticException("Multiplier must have the same number of rows as this matrix has columns");
        }
        return new ZeroMatrix(this.rows(), multiplier.columns(), mctx);
    }
    
    @Override
    public Numeric determinant() {
        if (columns() != rows()) throw new ArithmeticException("Cannot compute determinant of a non-square matrix");
        return ExactZero.getInstance(mctx);
    }
    
    @Override
    public Numeric trace() {
        if (columns() != rows()) throw new ArithmeticException("Cannot compute trace of a non-square matrix");
        return ExactZero.getInstance(mctx);
    }

    @Override
    public RealType norm() {
        // norm is the same for both max and Frobenius in this case
        return new RealImpl(BigDecimal.ZERO, mctx);
    }

    public static boolean isZeroMatrix(Matrix<? extends Numeric> matrix) {
        if (matrix instanceof ZeroMatrix) return true;  // short circuit test
        if (matrix.rows() == matrix.columns()) {
            // square matrices give us some shortcuts — first check the diagonal
            for (long idx = 0L; idx < matrix.rows(); idx++) {
                if (!Zero.isZero(matrix.valueAt(idx, idx))) return false;
            }
            // if the diagonal is all 0, check the upper & lower triangularity
            return  matrix.isLowerTriangular() && matrix.isUpperTriangular();
        }
        // non-square matrices get the more laborious version
        for (long row = 0L; row < matrix.rows(); row++) {
            for (long column = 0L; column < matrix.columns(); column++) {
                if (!Zero.isZero(matrix.valueAt(row, column))) return false;
            }
        }
        return true;
    }

    private static final String INVERSE_ERROR = "Cannot take the inverse of the zero matrix";

    @Override
    public Matrix<? extends Numeric> pow(Numeric n) {
        if (rows() != columns()) {
            throw new ArithmeticException("Cannot raise a non-square matrix to a power");
        }
        if (OptionalOperations.sign(n) == Sign.NEGATIVE) {
            throw new ArithmeticException(INVERSE_ERROR);
        }
        if (Zero.isZero(n)) return new IdentityMatrix(rows(), mctx);
        return this; // Zⁿ = Z for n != 0
    }

    @Override
    public Matrix<? extends Numeric> inverse() {
        // the zero matrix is never invertible
        throw new ArithmeticException(INVERSE_ERROR);
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof ZeroMatrix) {
            ZeroMatrix that = (ZeroMatrix) o;
            if (this == that) return true;
            return this.rows() == that.rows() && this.columns() == that.columns();
        }
        return super.equals(o);
    }
}
