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
import tungsten.types.Vector;
import tungsten.types.annotations.Columnar;
import tungsten.types.exceptions.CoercionException;
import tungsten.types.numerics.NumericHierarchy;
import tungsten.types.numerics.impl.ExactZero;
import tungsten.types.numerics.impl.One;
import tungsten.types.numerics.impl.Zero;
import tungsten.types.util.ClassTools;
import tungsten.types.vector.ColumnVector;
import tungsten.types.vector.RowVector;
import tungsten.types.vector.impl.ArrayColumnVector;
import tungsten.types.vector.impl.ArrayRowVector;

import java.lang.reflect.Array;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Basic concrete implementation of {@link Matrix}.
 *
 * @author Robert Poole <a href="mailto:Tarquin.AZ@gmail.com">Tarquin.AZ@gmail.com</a>
 * @param <T> the numeric type of this matrix
 */
public class BasicMatrix<T extends Numeric> implements Matrix<T> {
    private List<RowVector<T>> rows = new ArrayList<>();
    private final Map<Long, ColumnVector<T>> columnCache = new HashMap<>();

    /**
     * Default constructor.  Initial append operations will skip
     * the dimension check.
     */
    public BasicMatrix() {
    }
    
    /**
     * Construct a matrix from a 2D array.
     * Note that this constructor assumes the first array index
     * is the row, and the second array index is the column.
     * @param source a two-dimensional array
     */
    public BasicMatrix(T[][] source) {
        for (T[] rowArray : source) {
            append(rowArray);
        }
    }

    /**
     * Construct a matrix from a {@code List} of row vectors.
     * @param rows a {@code List} containing one or more row vectors
     */
    public BasicMatrix(List<RowVector<T>> rows) {
        this.rows = rows;
    }
    
    /**
     * Copy constructor.
     * @param source the matrix to copy
     */
    public BasicMatrix(Matrix<T> source) {
        for (long row = 0L; row < source.rows(); row++) {
            append(source.getRow(row).copy());
        }
    }

    @Override
    public long columns() {
        if (!rows.isEmpty()) {
            return rows.get(0).columns();
        }
        throw new IllegalStateException("This matrix has no rows");
    }

    @Override
    public long rows() {
        return rows.size();
    }

    @Override
    public T valueAt(long row, long column) {
        if (row < 0L || row >= rows() || column < 0L || column >= columns()) {
            throw new IndexOutOfBoundsException("Row and column indices must be within bounds");
        }
        return rows.get((int) row).elementAt((int) column);
    }

    /**
     * Given a row and column, set the element at that location
     * to the given value.
     * @param value  the value to set
     * @param row    the row index &ge;&nbsp;0
     * @param column the column index &ge;&nbsp;0
     */
    public void setValueAt(T value, long row, long column) {
        RowVector<T> currentRow = this.getRow(row);
        currentRow.setElementAt(value, column);
        if (columnCache.containsKey(column)) {
            columnCache.get(column).setElementAt(value, row);
        }
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
        
        final Class<T> clazz = rows.get(0).getElementType();
        try {
            if (rows() > 4L && isTriangular()) {
                // the above relies on short-circuit evaluation so that we
                // only check for triangularity for > 4x4 matrices
                // this little check could save us a lot of calculations
                Numeric accum = valueAt(0L, 0L);
                for (long index = 1L; index < rows(); index++) {
                    accum = accum.multiply(valueAt(index, index));
                }
                return (T) accum.coerceTo(clazz);
            }
            else {
                RowVector<T> firstRow = this.getRow(0L);
                BasicMatrix<T> intermediate = this.removeRow(0L);
                Numeric accum = ExactZero.getInstance(valueAt(0L, 0L).getMathContext());
                for (long column = 0L; column < columns(); column++) {
                    Numeric coeff = firstRow.elementAt(column);
                    if (column % 2L == 1L) coeff = coeff.negate(); // alternate sign of the coefficient
                    BasicMatrix<T> subMatrix = intermediate.removeColumn(column);
                    accum = accum.add(coeff.multiply(subMatrix.determinant()));
                }
                return (T) accum.coerceTo(clazz);
            }
        } catch (CoercionException ex) {
            Logger.getLogger(BasicMatrix.class.getName()).log(Level.SEVERE, "Coercion failed while computing determinant.", ex);
            throw new ArithmeticException("While computing determinant: " + ex.getMessage());
        }
    }

    @Override
    public Matrix<T> add(Matrix<T> addend) {
        if (this.rows() != addend.rows() || this.columns() != addend.columns()) {
            throw new ArithmeticException("Addend must match dimensions of matrix");
        }
        if (addend.getClass().isAnnotationPresent(Columnar.class) &&
                (long) columnCache.size() > columns() / 3L) {
            ColumnarMatrix<T> cresult = new ColumnarMatrix<>();
            for (long column = 0L; column < columns(); column++) {
                ColumnVector<T> colsum = this.getColumn(column).add((Vector<T>) addend.getColumn(column));
                cresult.append(colsum);
            }
            return cresult;
        }
        BasicMatrix<T> result = new BasicMatrix<>();
        for (long row = 0L; row < rows(); row++) {
            // casting to Vector<T> to avoid ambiguity over which add() method to use
            RowVector<T> rowsum = this.getRow(row).add((Vector<T>) addend.getRow(row));
            result.append(rowsum);
        }
        return result;
    }

    @Override
    public Matrix<T> multiply(Matrix<T> multiplier) {
        if (rows.isEmpty()) throw new IllegalStateException("Cannot multiply an empty matrix");
        if (this.columns() != multiplier.rows()) {
            throw new ArithmeticException("Multiplier must have the same number of rows as this matrix has columns");
        }
        final Class<T> clazz = (Class<T>) ClassTools.getInterfaceTypeFor(this.valueAt(0L, 0L).getClass());
        T[][] temp = (T[][]) Array.newInstance(clazz, (int) this.rows(), (int) multiplier.columns());
        for (long row = 0L; row < rows(); row++) {
            RowVector<T> rowvec = this.getRow(row);
            for (long column = 0L; column < multiplier.columns(); column++) {
                temp[(int) row][(int) column] = rowvec.dotProduct(multiplier.getColumn(column));
            }
        }
        return new BasicMatrix<>(temp);
    }
    
    @Override
    public RowVector<T> getRow(long row) {
        if (row < 0L || row >= rows()) {
            throw new IndexOutOfBoundsException("Row index is out of range 0\u2013" + (rows() - 1L));
        }
        return rows.get((int) row);
    }
    
    @Override
    public ColumnVector<T> getColumn(long column) {
        if (columnCache.containsKey(column)) {
            return columnCache.get(column);
        }
        if (rows.isEmpty() || column >= columns()) throw new IndexOutOfBoundsException(column + " is not a valid column index");
        Class<T> clazz = (Class<T>) ClassTools.getInterfaceTypeFor(valueAt(0L, column).getClass());
        T[] temp = (T[]) Array.newInstance(clazz, (int) rows());
        for (int j = 0; j < rows(); j++) {
            temp[j] = valueAt(j, column);
        }
        ColumnVector<T> result = new ArrayColumnVector<>(temp);
        columnCache.put(column, result);
        return result;
    }
    
    @Override
    public Matrix<T> transpose() {
        ColumnarMatrix<T> result = new ColumnarMatrix<>();
        rows.stream().map(RowVector::transpose).forEachOrdered(result::append);
        return result;
    }
    
    /**
     * Append a row to this matrix.
     * Note that this operation is not thread safe!
     * @param row a row vector representing the new row to append
     */
    public final void append(RowVector<T> row) {
        if (rows.isEmpty() || row.columns() == this.columns()) {
            rows.add(row);
            // invalidate the column cache
            if (!columnCache.isEmpty()) columnCache.clear();
        } else {
            throw new IllegalArgumentException("Expected a row vector with " + this.columns() +
                    " columns, but received one with " + row.columns() + " instead");
        }
    }
    
    /**
     * Convenience method for internal methods and subclasses to manipulate
     * this matrix by appending a row.  Useful for when arrays are being
     * generated in intermediate computational steps, e.g. for speed.
     * This is not a thread safe operation.
     * 
     * @param row an array of type T
     */
    protected void append(T[] row) {
        append(new ArrayRowVector<>(row));
    }
    
    /**
     * Append a column to this matrix.
     * Note that this is not a thread safe operation!
     * @param column a column vector representing the new column to append
     */
    public void append(ColumnVector<T> column) {
        if (rows.isEmpty()) throw new UnsupportedOperationException("Appending a column to an empty BasicMatrix is not supported");
        if (column.rows() != this.rows()) {
            throw new IllegalArgumentException("Column vector has wrong number of elements");
        }
        columnCache.put(columns(), column);  // cache the column before updating the rows

        for (long rowidx = 0; rowidx < this.rows(); rowidx++) {
            rows.get((int) rowidx).append(column.elementAt(rowidx));
        }
    }
    
    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder();
        buf.append("[\n");
        rows.forEach(rowvec -> {
            buf.append("\u00A0\u00A0").append(rowvec.toString()).append('\n');
        });
        buf.append("\u00A0]");
        return buf.toString();
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
        
        // otherwise compute this using the adjoint
        final Matrix<T> adjoint = this.adjoint();
        final Numeric detInv = det.inverse();
        BasicMatrix<Numeric> byAdjoint = new BasicMatrix<>();
        for (long row = 0L; row < adjoint.rows(); row++) {
            byAdjoint.append(((RowVector<Numeric>) adjoint.getRow(row)).scale(detInv));
        }
        return byAdjoint;
    }

    /**
     * Convert this matrix to a matrix of the given numeric type.
     * Upconversions are explicitly permitted, whereas downconversions
     * will result in an exception.
     * @param clazz the desired target type
     * @return a new matrix with elements of type {@code clazz}
     * @param <R> the element type
     * @throws ArithmeticException if upconversion is not supported
     */
    public <R extends Numeric> Matrix<R> upconvert(Class<R> clazz) {
        // first, check to make sure we can do this -- ensure R is a wider type than T
        NumericHierarchy targetType = NumericHierarchy.forNumericType(clazz);
        final Class<T> currentClazz = rows.get(0).getElementType();
        NumericHierarchy currentType = NumericHierarchy.forNumericType(currentClazz);
        // if our elements are already of the requested type, just cast and return
        if (currentType == targetType) return (Matrix<R>) this;
        if (currentType != null && currentType.compareTo(targetType) > 0) {
            throw new ArithmeticException("Cannot upconvert elements of " + currentType + " to elements of " + targetType);
        }
        BasicMatrix<R> result = new BasicMatrix<>();
        for (long row = 0L; row < rows(); row++) {
            R[] accum = (R[]) Array.newInstance(clazz, (int) columns());
            for (long column = 0L; column < columns(); column++) {
                try {
                    accum[(int) column] = (R) valueAt(row, column).coerceTo(clazz);
                } catch (CoercionException ex) {
                    Logger.getLogger(BasicMatrix.class.getName()).log(Level.SEVERE,
                            "Coercion failed while upconverting matrix to " + clazz.getTypeName(), ex);
                    throw new ArithmeticException(String.format("While converting value %s to %s at %d, %d",
                            valueAt(row, column), clazz.getTypeName(), row, column));
                }
            }
            result.append(accum);
        }
        return result;
    }

    /**
     * Generate a matrix from this matrix with the given row removed.
     * @param row the index of the row to remove
     * @return the resulting matrix
     */
    public BasicMatrix<T> removeRow(long row) {
        ArrayList<RowVector<T>> result = new ArrayList<>(rows);
        result.remove((int) row);
        return new BasicMatrix<>(result);
    }

    /**
     * Generate a matrix from this matrix with the given column removed.
     * @param column the index of the row to remove
     * @return the resulting matrix
     */
    public BasicMatrix<T> removeColumn(long column) {
        final Class<T> clazz = rows.get(0).getElementType();
        ArrayList<RowVector<T>> result = new ArrayList<>();
        for (RowVector<T> row : rows) {
            int arrIdx = 0;
            T[] backingArray = (T[]) Array.newInstance(clazz, (int) columns() - 1);
            for (long c = 0L; c < row.columns(); c++) {
                if (c == column) continue;
                backingArray[arrIdx++] = row.elementAt(c);
            }
            RowVector<T> updatedRow = new ArrayRowVector<>(backingArray);
            result.add(updatedRow);
        }
        return new BasicMatrix<>(result);
    }

    /**
     * Given a row vector and an index, replace the existing row with
     * the given vector.
     * @param rowIndex the index of the row to update
     * @param row      the updated row
     */
    public void updateRow(long rowIndex, RowVector<T> row) {
        if (rowIndex < 0L || rowIndex >= rows()) throw new IndexOutOfBoundsException("Row " + rowIndex + " does not exist");
        if (row.length() != columns()) throw new IllegalArgumentException("Provided RowVector must match column dimension");
        rows.set((int) rowIndex, row);
        columnCache.clear();
    }

    /**
     * Given a column vector and an index, replace the existing column with
     * the given vector.
     * @param colIndex the index of the column to update
     * @param column   the updated column
     */
    public void updateColumn(long colIndex, ColumnVector<T> column) {
        if (colIndex < 0L || colIndex >= columns()) throw new IndexOutOfBoundsException("Column " + colIndex + " does not exist");
        if (column.length() != rows()) throw new IllegalArgumentException("Provided ColumnVector must match row dimension");
        for (long rowIdx = 0L; rowIdx < rows(); rowIdx++) {
            setValueAt(column.elementAt(rowIdx), rowIdx, colIndex);
        }
        columnCache.put(colIndex, column);
    }

    /**
     * Return a matrix with row {@code row} and column {@code column}
     * removed.
     * 
     * @param row the row index
     * @param column the column index
     * @return the sub-matrix formed by row and column removal
     */
    public BasicMatrix<T> minor(long row, long column) {
        return this.removeRow(row).removeColumn(column);
    }

    /**
     * Compute the cofactor matrix of this matrix.
     * The cofactor matrix is formed from the cofactors of the original matrix.
     * @return the cofactor matrix
     * @see <a href="https://en.wikipedia.org/wiki/Minor_(linear_algebra)#Applications_of_minors_and_cofactors">the
     *   Wikipedia article</a>
     */
    public BasicMatrix<T> cofactor() {
        final Class<T> clazz = rows.get(0).getElementType();
        T[][] result = (T[][]) Array.newInstance(clazz, (int) this.rows(), (int) this.columns());
        for (long row = 0L; row < rows(); row++) {
            for (long column = 0L; column < columns(); column++) {
                T intermediate = minor(row, column).determinant();
                if ((row + column) % 2L == 1L) intermediate = (T) intermediate.negate();
                result[(int) row][(int) column] = intermediate;
            }
        }
        return new BasicMatrix<>(result);
    }

    /**
     * Compute the adjoint, which is the transpose of
     * the cofactor matrix for this matrix.
     * @return the adjoint matrix
     */
    public Matrix<T> adjoint() {
        return cofactor().transpose();
    }

    /**
     * Exchange two rows of this matrix.
     * @param row1 the first row
     * @param row2 the second row
     */
    public void exchangeRows(long row1, long row2) {
        if (row1 < 0L || row1 >= rows()) throw new IndexOutOfBoundsException("row1 must be within bounds 0 - " + (rows() - 1L));
        if (row2 < 0L || row2 >= rows()) throw new IndexOutOfBoundsException("row2 must be within bounds 0 - " + (rows() - 1L));
        if (row1 == row2) return; // NO-OP
        
        Collections.swap(this.rows, (int) row1, (int) row2);
        columnCache.clear();
    }

    /**
     * Exchange two columns of this matrix.
     * @param column1 the first column
     * @param column2 the second column
     */
    public void exchangeColumns(long column1, long column2) {
        if (column1 < 0L || column1 >= columns()) throw new IndexOutOfBoundsException("column1 must be within bounds 0 - " + (columns() - 1L));
        if (column2 < 0L || column2 >= columns()) throw new IndexOutOfBoundsException("column2 must be within bounds 0 - " + (columns() - 1L));
        if (column1 == column2) return; // NO-OP

        for (int rowIdx = 0; rowIdx < rows.size(); rowIdx++) {
            List<T> elements = rows.get(rowIdx).stream().collect(Collectors.toCollection(ArrayList::new));
            Collections.swap(elements, (int) column1, (int) column2);
            rows.set(rowIdx, new ArrayRowVector<>(elements));
        }
        // swap elements in the column cache if both or either of the keys are mapped
        if (columnCache.containsKey(column1) && columnCache.containsKey(column2)) {
            ColumnVector<T> first = columnCache.get(column1);
            columnCache.put(column1, columnCache.get(column2));
            columnCache.put(column2, first);
        } else if (columnCache.containsKey(column1)) {
            columnCache.put(column2, columnCache.get(column1));
            columnCache.remove(column1);
        } else if (columnCache.containsKey(column2)) {
            columnCache.put(column1, columnCache.get(column2));
            columnCache.remove(column2);
        }
    }

    @Override
    public Matrix<T> scale(T scaleFactor) {
        if (One.isUnity(scaleFactor)) return this;
        BasicMatrix<T> scaled = new BasicMatrix<>();
        
        rows.stream().map(rowVec -> rowVec.scale(scaleFactor)).forEachOrdered(scaled::append);
        return scaled;
    }
    
    @Override
    public boolean equals(Object o) {
        if (o instanceof Matrix) {
            Matrix<? extends Numeric> that = (Matrix<? extends Numeric>) o;
            if (rows() != that.rows()) return false;
            for (long row = 0L; row < rows(); row++) {
                if (!getRow(row).equals(that.getRow(row))) return false;
            }
            return true;
        }
        return false;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 71 * hash + Objects.hashCode(this.rows);
        return hash;
    }

    /*
     Methods necessary for Groovy operator overloading follow.
     */

    public void leftShift(RowVector<T> row) {
        this.append(row);
    }
    public void leftShift(ColumnVector<T> column) {
        this.append(column);
    }
}
