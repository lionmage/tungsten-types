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
 */

package tungsten.types.matrix.impl;

import tungsten.types.Matrix;
import tungsten.types.Numeric;
import tungsten.types.annotations.Columnar;
import tungsten.types.exceptions.CoercionException;
import tungsten.types.numerics.ComplexType;
import tungsten.types.vector.ColumnVector;
import tungsten.types.vector.RowVector;
import tungsten.types.vector.impl.ArrayColumnVector;
import tungsten.types.vector.impl.ArrayRowVector;

/**
 * An adapter to take any {@link Matrix} and wrap it in a
 * {@link ComplexType} view.  This class delegates to most
 * of the wrapped matrix's methods and does simple type
 * coercion.
 * @author Robert Poole, <a href="mailto:Tarquin.AZ@gmail.com">Gmail address</a>
 * @see BasicMatrix#upconvert(Class)
 * @since 0.4
 */
public class ComplexMatrixAdapter implements Matrix<ComplexType> {
    private final Matrix<? extends Numeric> original;

    public ComplexMatrixAdapter(Matrix<? extends Numeric> M) {
        this.original = M;
    }

    @Override
    public long columns() {
        return original.columns();
    }

    @Override
    public long rows() {
        return original.rows();
    }

    @Override
    public ComplexType valueAt(long row, long column) {
        try {
            return (ComplexType) original.valueAt(row, column).coerceTo(ComplexType.class);
        } catch (CoercionException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public ComplexType determinant() {
        try {
            return (ComplexType) original.determinant().coerceTo(ComplexType.class);
        } catch (CoercionException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public Matrix<ComplexType> inverse() {
        return new ComplexMatrixAdapter(original.inverse());
    }

    @Override
    public boolean isUpperTriangular() {
        return original.isUpperTriangular();
    }

    @Override
    public boolean isLowerTriangular() {
        return original.isLowerTriangular();
    }

    @Override
    public ComplexType trace() {
        try {
            return (ComplexType) original.trace().coerceTo(ComplexType.class);
        } catch (CoercionException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public Matrix<ComplexType> transpose() {
        return new ComplexMatrixAdapter(original.transpose());
    }

    @Override
    public Matrix<ComplexType> add(Matrix<ComplexType> addend) {
        if (addend.rows() != original.rows() || addend.columns() != original.columns()) {
            throw new IllegalArgumentException("Dimensional mismatch");
        }
        if (!(addend instanceof ComplexMatrixAdapter) &&
                addend.getClass().isAnnotationPresent(Columnar.class) == original.getClass().isAnnotationPresent(Columnar.class)) {
            // addition is commutative, at least
            return addend.add(this);
        }
        return new ParametricMatrix<>(original.rows(), original.columns(), (row, col) -> {
            try {
                return (ComplexType) original.valueAt(row, col).add(addend.valueAt(row, col))
                        .coerceTo(ComplexType.class);
            } catch (CoercionException e) {
                throw new IllegalStateException("While coercing value of sum to complex at row=" +
                        row + ", column=" + col, e);
            }
        });
    }

    @Override
    public Matrix<ComplexType> multiply(Matrix<ComplexType> multiplier) {
        if (original.columns() != multiplier.rows()) {
            throw new IllegalArgumentException("Dimensional mismatch");
        }
        ComplexType[][] temp = new ComplexType[(int) this.rows()][(int) multiplier.columns()];
        for (long row = 0L; row < rows(); row++) {
            RowVector<ComplexType> rowvec = this.getRow(row);
            for (long column = 0L; column < multiplier.columns(); column++) {
                temp[(int) row][(int) column] = rowvec.dotProduct(multiplier.getColumn(column));
            }
        }
        return new BasicMatrix<>(temp);
    }

    @Override
    public Matrix<ComplexType> scale(ComplexType scaleFactor) {
        return new ParametricMatrix<>(original.rows(), original.columns(), (row, col) -> {
            try {
                return (ComplexType) original.valueAt(row, col).multiply(scaleFactor).coerceTo(ComplexType.class);
            } catch (CoercionException e) {
                throw new IllegalStateException("While coercing scaled value to complex at row=" +
                        row + ", column=" + col, e);
            }
        });
    }

    @Override
    public RowVector<ComplexType> getRow(long row) {
        RowVector<? extends Numeric> origRow = original.getRow(row);
        if (ComplexType.class.isAssignableFrom(origRow.getElementType())) {
            return (RowVector<ComplexType>) origRow;
        }
        RowVector<ComplexType> nuRow = new ArrayRowVector<>(ComplexType.class, origRow.length());
        try {
            for (long idx = 0L; idx < origRow.length(); idx++) {
                ComplexType converted = (ComplexType) origRow.elementAt(idx).coerceTo(ComplexType.class);
                nuRow.setElementAt(converted, idx);
            }
        } catch (CoercionException ce) {
            throw new IllegalStateException("While assembling a complex row vector", ce);
        }
        return nuRow;
    }

    @Override
    public ColumnVector<ComplexType> getColumn(long column) {
        ColumnVector<? extends Numeric> origColumn = original.getColumn(column);
        if (ComplexType.class.isAssignableFrom(origColumn.getElementType())) {
            return (ColumnVector<ComplexType>) origColumn;
        }
        ColumnVector<ComplexType> nuCol = new ArrayColumnVector<>(ComplexType.class, origColumn.length());
        try {
            for (long idx = 0L; idx < origColumn.length(); idx++) {
                ComplexType converted = (ComplexType) origColumn.elementAt(idx).coerceTo(ComplexType.class);
                nuCol.setElementAt(converted, idx);
            }
        } catch (CoercionException ce) {
            throw new IllegalStateException("While assembling a complex column vector", ce);
        }
        return nuCol;
    }
}
