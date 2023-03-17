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
import tungsten.types.numerics.impl.ExactZero;
import tungsten.types.numerics.impl.IntegerImpl;
import tungsten.types.numerics.impl.One;

import java.math.BigInteger;
import java.math.MathContext;
import java.util.Objects;

/**
 * An Identity matrix (&#x1D7D9;) representation.
 * Note that, for most operations, the limitations of Java
 * array sizes do not apply; you can have an Identity matrix
 * with up to {@link Long#MAX_VALUE} columns and rows.
 *
 * @author Robert Poole <a href="mailto:Tarquin.AZ@gmail.com">Tarquin.AZ@gmail.com</a>
 */
public class IdentityMatrix implements Matrix<Numeric> {
    private final MathContext mctx;
    private final long elementCount;

    public IdentityMatrix(long size, MathContext mctx) {
        this.mctx = mctx;
        this.elementCount = size;
    }
    
    @Override
    public Numeric valueAt(long row, long column) {
        if (row < 0L || row >= elementCount || column < 0L || column >= elementCount) {
            throw new IndexOutOfBoundsException("Row and column indices must be between 0 and " +
                    (elementCount - 1L) + ", inclusive");
        }
        if (row == column) return One.getInstance(mctx);
        return ExactZero.getInstance(mctx);
    }
    
    @Override
    public long columns() { return elementCount; }
    
    @Override
    public long rows() { return elementCount; }
    
    @Override
    public Numeric determinant() {
        return One.getInstance(mctx);
    }
    
    @Override
    public Numeric trace() {
        // this could be any Numeric subtype, really, but IntegerImpl has less overhead
        return new IntegerImpl(BigInteger.valueOf(elementCount));
    }
    
    @Override
    public Matrix<Numeric> multiply(Matrix<Numeric> multiplier) {
        if (elementCount != multiplier.rows()) {
            throw new ArithmeticException("The multiplier must have the same number of rows as this matrix has columns.");
        }
        return multiplier;  // IA = A
    }

    @Override
    public Matrix<Numeric> scale(Numeric scaleFactor) {
        // TODO find a more sensible cutover value
        if (elementCount > (long) Integer.MAX_VALUE) {
            return new ParametricMatrix<>(elementCount, elementCount, (row, column) -> {
               if (row.longValue() == column.longValue()) return scaleFactor;
               return ExactZero.getInstance(mctx);
            });
        }
        Numeric[] elements = new Numeric[(int) elementCount];
        for (int idx = 0; idx < (int) elementCount; idx++) {
            elements[idx] = scaleFactor;
        }
        return new DiagonalMatrix<>(elements);
    }

    @Override
    public Matrix<Numeric> add(Matrix<Numeric> addend) {
        if (addend.rows() != this.rows() || addend.columns() != this.columns()) {
            throw new ArithmeticException("Addend must match dimensions of this diagonal matrix");
        }

        final Numeric one = One.getInstance(mctx);
        
        BasicMatrix<Numeric> result = new BasicMatrix<>(addend);
        for (long idx = 0L; idx < elementCount; idx++) {
            // TODO determine which order of addition would be more efficient
            Numeric sum = one.add(addend.valueAt(idx, idx));
            result.setValueAt(sum, idx, idx);
        }
        return result;
    }
    
    @Override
    public IdentityMatrix inverse() {
        // the identity matrix is its own inverse
        return this;
    }

    @Override
    public Matrix<Numeric> transpose() {
        return this;  // the Identity matrix is its own transpose
    }

    @Override
    public Matrix<? extends Numeric> pow(Numeric n) {
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof IdentityMatrix) {
            IdentityMatrix that = (IdentityMatrix) o;
            return this.elementCount == that.elementCount;
        }
        // OK, test the elements to see if we're equivalent to an Identity matrix
        if (o instanceof Matrix) {
            final Matrix<? extends Numeric> that = (Matrix<? extends Numeric>) o;
            if (elementCount != that.rows() || elementCount != that.columns()) return false;

            // the diagonal should be all 1 values
            for (long idx = 0L; idx < elementCount; idx++) {
                if (!One.isUnity(that.valueAt(idx, idx))) return false;
            }
            // NOTE: Matrix.isTriangular() only tells you whether a matrix is
            // upper OR lower triangular, which is insufficient for our purposes here.
            return that.isUpperTriangular() && that.isLowerTriangular(); // Identity matrix must be upper and lower triangular
        }
        return false;
    }

    @Override
    public boolean isUpperTriangular() {
        return true;
    }

    @Override
    public boolean isLowerTriangular() {
        return true;
    }

    @Override
    public boolean isTriangular() {
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 5;
        hash = 31 * hash + Objects.hashCode(this.mctx);
        hash = 31 * hash + (int) (this.elementCount ^ (this.elementCount >>> 32));
        return hash;
    }
    
    @Override
    public String toString() {
        // return the symbol for identity matrix with the size (diagonal element count)
        return "\uD835\uDFD9[" + elementCount + "]";  // surrogate pair for 1D7D9
    }
}
