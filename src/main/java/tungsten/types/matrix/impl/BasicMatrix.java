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
import tungsten.types.exceptions.CoercionException;
import tungsten.types.numerics.NumericHierarchy;
import tungsten.types.numerics.impl.ExactZero;
import tungsten.types.numerics.impl.Zero;
import tungsten.types.vector.ColumnVector;
import tungsten.types.vector.RowVector;
import tungsten.types.vector.impl.ArrayColumnVector;
import tungsten.types.vector.impl.ArrayRowVector;

import java.lang.reflect.Array;
import java.lang.reflect.ParameterizedType;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Basic concrete implementation of {@link Matrix}.
 *
 * @author Robert Poole <a href="mailto:Tarquin.AZ@gmail.com">Tarquin.AZ@gmail.com</a>
 * @param <T> the numeric type of this matrix
 */
public class BasicMatrix<T extends Numeric> implements Matrix<T> {
    private List<RowVector<T>> rows = new ArrayList<>();
    private final Map<Long, ColumnVector<T>> columnCache = new HashMap<>();
    
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
            append(new ArrayRowVector<>(rowArray));
        }
    }
    
    public BasicMatrix(List<RowVector<T>> rows) {
        this.rows = rows;
    }
    
    /**
     * Copy constructor.
     * @param source the matrix to copy
     */
    public BasicMatrix(Matrix<T> source) {
        for (long row = 0L; row < source.rows(); row++) {
            append(source.getRow(row));
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
            throw new ArithmeticException("Can only compute determinant for a square matrix.");
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
        
        final Class<T> clazz = (Class<T>) ((Class) ((ParameterizedType) getClass()
                .getGenericSuperclass()).getActualTypeArguments()[0]);
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
            Logger.getLogger(BasicMatrix.class.getName()).log(Level.SEVERE, "Coercion failed computing determinant.", ex);
            throw new ArithmeticException("While computing determinant: " + ex.getMessage());
        }
    }

    @Override
    public Matrix<T> add(Matrix<T> addend) {
        if (this.rows() != addend.rows() || this.columns() != addend.columns()) {
            throw new ArithmeticException("Addend must match dimensions of matrix");
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
        if (this.columns() != multiplier.rows()) {
            throw new ArithmeticException("Multiplier must have the same number of rows as this matrix has columns.");
        }
        final Class<T> clazz = (Class<T>) ((Class) ((ParameterizedType) getClass()
                .getGenericSuperclass()).getActualTypeArguments()[0]);
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
            throw new IndexOutOfBoundsException("Row index is out of range.");
        }
        return rows.get((int) row);
    }
    
    @Override
    public ColumnVector<T> getColumn(long column) {
        if (columnCache.containsKey(column)) {
            return columnCache.get(column);
        }
        Class<T> clazz = (Class<T>) ((Class) ((ParameterizedType) getClass()
                .getGenericSuperclass()).getActualTypeArguments()[0]);
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
                    " columns, but received one with " + row.columns() + " instead.");
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
        
        // otherwise recursively compute this using the adjoint
        final Matrix<T> adjoint = this.adjoint();
        BasicMatrix<Numeric> byAdjoint = new BasicMatrix<>();
        for (long row = 0L; row < adjoint.rows(); row++) {
            byAdjoint.append(((RowVector<Numeric>) adjoint.getRow(row)).scale(det.inverse()));
        }
        return byAdjoint;
    }
    
    public <R extends Numeric> Matrix<R> upconvert(Class<R> clazz) {
        // first, check to make sure we can do this -- ensure R is a wider type than T
        NumericHierarchy targetType = NumericHierarchy.forNumericType(clazz);
        Class<T> currentClazz = (Class<T>) ((Class) ((ParameterizedType) getClass()
                .getGenericSuperclass()).getActualTypeArguments()[0]);
        NumericHierarchy currentType = NumericHierarchy.forNumericType(currentClazz);
        // if our elements are already of the requested type, just cast and return
        if (currentType == targetType) return (Matrix<R>) this;
        if (currentType.compareTo(targetType) > 0) {
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
    
    public BasicMatrix<T> removeRow(long row) {
        ArrayList<RowVector<T>> result = new ArrayList<>(rows);
        result.remove((int) row);
        return new BasicMatrix<>(result);
    }
    
    public BasicMatrix<T> removeColumn(long column) {
        Class<T> clazz = (Class<T>) ((Class) ((ParameterizedType) getClass()
                .getGenericSuperclass()).getActualTypeArguments()[0]);
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
    
    public BasicMatrix<T> cofactor() {
        Class<T> clazz = (Class<T>) ((Class) ((ParameterizedType) getClass()
                .getGenericSuperclass()).getActualTypeArguments()[0]);
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
    
    public Matrix<T> adjoint() {
        return cofactor().transpose();
    }
    
    public Matrix<T> exchangeRows(long row1, long row2) {
        if (row1 < 0L || row1 >= rows()) throw new IndexOutOfBoundsException("row1 must be within bounds 0 - " + (rows() - 1L));
        if (row2 < 0L || row2 >= rows()) throw new IndexOutOfBoundsException("row2 must be within bounds 0 - " + (rows() - 1L));
        if (row1 == row2) return this; // NO-OP
        
        final ArrayList<RowVector<T>> result = new ArrayList<>(this.rows);
        Collections.swap(result, (int) row1, (int) row2);
        return new BasicMatrix<>(result);
    }
    
    public Matrix<T> exchangeColumns(long column1, long column2) {
        if (column1 < 0L || column1 >= columns()) throw new IndexOutOfBoundsException("column1 must be within bounds 0 - " + (columns() - 1L));
        if (column2 < 0L || column2 >= columns()) throw new IndexOutOfBoundsException("column2 must be within bounds 0 - " + (columns() - 1L));
        if (column1 == column2) return this; // NO-OP
        
        final ArrayList<RowVector<T>> result = new ArrayList<>();
        this.rows.forEach(rowVec -> {
            ArrayList<T> row = new ArrayList<>((int) rowVec.length());
            for (long column = 0L; column < columns(); column++) {
                if (column == column1) row.add(rowVec.elementAt(column2));
                else if (column == column2) row.add(rowVec.elementAt(column1));
                else row.add(rowVec.elementAt(column));
            }
            result.add(new ArrayRowVector<>(row));  // TODO this might be better with a List-based RowVector
        });
        
        return new BasicMatrix<>(result);
    }

    @Override
    public Matrix<T> scale(T scaleFactor) {
        BasicMatrix<T> scaled = new BasicMatrix<>();
        
        rows.stream().map(rowVec -> rowVec.scale(scaleFactor)).forEach(scaled::append);
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
