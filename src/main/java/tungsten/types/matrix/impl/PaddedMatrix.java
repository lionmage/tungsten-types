package tungsten.types.matrix.impl;
/*
 * The MIT License
 *
 * Copyright © 2023 Robert Poole <Tarquin.AZ@gmail.com>.
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


import tungsten.types.Matrix;
import tungsten.types.Numeric;
import tungsten.types.numerics.impl.One;
import tungsten.types.numerics.impl.Zero;
import tungsten.types.util.ClassTools;
import tungsten.types.vector.ColumnVector;
import tungsten.types.vector.RowVector;
import tungsten.types.vector.impl.ArrayColumnVector;
import tungsten.types.vector.impl.ArrayRowVector;
import tungsten.types.vector.impl.ListColumnVector;
import tungsten.types.vector.impl.ListRowVector;

import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

/**
 * Given a source {@link Matrix}, this class creates a new, larger
 * {@link Matrix} with additional rows and/or columns. The extra cells
 * are populated with a padding value supplied to the constructor.
 *
 * @param <T> the {@link Numeric} subtype of this matrix
 * @author Robert Poole
 */
public class PaddedMatrix<T extends Numeric> extends ParametricMatrix<T> {
    public static final long VECTOR_SIZE_THRESHOLD = 10L;
    final T padValue;
    final Matrix<T> source;

    /**
     * Construct a new matrix from the given matrix with the provided number
     * of rows and columns, using the provided pad value to populate cells
     * that do not exist in the original.
     *
     * @param original the original (source) matrix
     * @param rows     the number of rows for the newly created matrix
     * @param columns  the number of columns for the newly created matrix
     * @param padWith  the value with which to pad cells not part of {@code original}
     */
    public PaddedMatrix(Matrix<T> original, long rows, long columns, T padWith) {
        super(rows, columns, (row, column) -> {
            if (row < 0L || row >= rows) throw new IndexOutOfBoundsException("row value out of bounds: " + row);
            if (column < 0L || column >= columns) throw new IndexOutOfBoundsException("column value out of bounds: " + column);
            if (row < original.rows() && column < original.columns()) {
                return original.valueAt(row, column);
            }
            return padWith;
        });
        if (rows < original.rows() || columns < original.columns()) {
            throw new IllegalArgumentException("Dimensions must equal or exceed those of the original matrix");
        }
        padValue = padWith;
        source = original;
    }

    @Override
    public T determinant() {
        if (this.columns() == source.columns() && this.rows() == source.rows()) {
            // degenerate case where this matrix is identical to the source
            return source.determinant();
        }
        if (this.columns() == this.rows() && source.columns() == source.rows()) {
            // the original matrix was square, and so is this one
            if (Zero.isZero(padValue)) {
                // the lower right corner of this matrix is a zero matrix, which has
                // a determinant of 0, and the determinant of this matrix is the
                // product of the determinant of original matrix with that of the
                // zero matrix, making the overall determinant 0 as well
                return padValue;
            } else if (One.isUnity(padValue)) {
                // padding with 1s around the original matrix does not change the determinant
                return source.determinant();
            }
        }
        return super.determinant();
    }

    private static final long MAX_INT = Integer.MAX_VALUE;

    @Override
    public RowVector<T> getRow(long row) {
        if (row >= source.rows() && row < this.rows()) {
            if (this.columns() > VECTOR_SIZE_THRESHOLD) {
                // we shouldn't be wasting an array for something like this
                if (this.columns() > MAX_INT) {
                    // we need to iteratively build a LinkedList that has the
                    // correct number of elements we need since the total number
                    // is greater than a single call to Collections.nCopies can provide
                    long columnCount = this.columns();
                    List<T> elements = new LinkedList<>();
                    do {
                        int takeN = columnCount > MAX_INT ? Integer.MAX_VALUE : (int) (columnCount % MAX_INT);
                        elements.addAll(Collections.nCopies(takeN, padValue));
                    } while ((columnCount -= MAX_INT) > 0L);
                    return new ListRowVector<>(elements);
                }
                // this is more efficient, however, since the List returned by
                // Collections.nCopies() itself only holds a single reference to the
                // data element provided, and therefore it's tiny with fast
                // synthetic accessor methods
                return new ListRowVector<>(Collections.nCopies((int) this.columns(), padValue));
            }
            T[] elements = (T[]) Array.newInstance(ClassTools.getInterfaceTypeFor(padValue.getClass()), (int) this.columns());
            Arrays.fill(elements, padValue);
            return new ArrayRowVector<>(elements);
        }
        // we still need to account for rows with index < source.rows() that may have been right-padded
        // this could actually have been taken care of by super.getRow(), but this is more efficient
        if (source.columns() < columns()) {
            ListRowVector<T> aggregate = new ListRowVector<>(source.getRow(row));
            for (long k = source.columns(); k < columns(); k++) aggregate.append(padValue);
            return aggregate;
        }
        return super.getRow(row);
    }

    @Override
    public ColumnVector<T> getColumn(long column) {
        if (column >= source.columns() && column < this.columns()) {
            if (this.rows() > VECTOR_SIZE_THRESHOLD) {
                if (this.rows() > MAX_INT) {
                    long rowCount = this.rows();
                    List<T> elements = new LinkedList<>();
                    do {
                        int takeN = rowCount > MAX_INT ? Integer.MAX_VALUE : (int) (rowCount % MAX_INT);
                        elements.addAll(Collections.nCopies(takeN, padValue));
                    } while ((rowCount -= MAX_INT) > 0L);
                    return new ListColumnVector<>(elements);
                }
                return new ListColumnVector<>(Collections.nCopies((int) this.rows(), padValue));
            }
            T[] elements = (T[]) Array.newInstance(ClassTools.getInterfaceTypeFor(padValue.getClass()), (int) this.rows());
            Arrays.fill(elements, padValue);
            return new ArrayColumnVector<>(elements);
        }
        // we still need to account for columns with index < source.columns() that may have been padded
        // this could actually have been taken care of by super.getColumn(), but this is more efficient
        if (source.rows() < rows()) {
            ListColumnVector<T> aggregate = new ListColumnVector<>(source.getColumn(column));
            for (long k = source.rows(); k < rows(); k++) aggregate.append(padValue);
            return aggregate;
        }
        return super.getColumn(column);
    }
}
