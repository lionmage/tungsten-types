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
import tungsten.types.numerics.RealType;
import tungsten.types.numerics.impl.ExactZero;
import tungsten.types.numerics.impl.One;
import tungsten.types.numerics.impl.Zero;
import tungsten.types.util.ClassTools;
import tungsten.types.util.MathUtils;
import tungsten.types.vector.ColumnVector;
import tungsten.types.vector.RowVector;
import tungsten.types.vector.impl.ArrayColumnVector;
import tungsten.types.vector.impl.ArrayRowVector;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A {@link Matrix} implementation that stores its internal values in
 * a columnar format.
 *
 * @author Robert Poole <a href="mailto:Tarquin.AZ@gmail.com">Tarquin.AZ@gmail.com</a>
 * @param <T> the {@link Numeric} type for the elements of this matrix
 */
@Columnar
public class ColumnarMatrix<T extends Numeric> implements Matrix<T> {
    private final List<ColumnVector<T>> columns = new ArrayList<>();

    /**
     * Default constructor. Apprend operations will initially skip
     * dimension checks.
     */
    public ColumnarMatrix() {
    }

    /**
     * Copy constructor.
     * @param source the matrix to copy
     */
    public ColumnarMatrix(Matrix<T> source) {
        for (long column = 0L; column < source.columns(); column++) {
            columns.add(source.getColumn(column).copy());
        }
    }

    /**
     * Construct a matrix from a two-dimensional array of elements.
     * @param source the array of source elements
     * @apiNote The source array is in row-major format, as is the
     *   convention for Java arrays.
     */
    public ColumnarMatrix(T[][] source) {
        final int columns = source[0].length;
        for (int column = 0; column < columns; column++) {
            append(extractColumn(source, column));
        }
    }

    /**
     * Construct a matrix from a {@code List} of column vectors.
     * @param source the column vectors to append, in sequence
     */
    public ColumnarMatrix(List<ColumnVector<T>> source) {
        source.forEach(this::append);
    }
    
    private ColumnVector<T> extractColumn(T[][] source, int column) {
        final int rows = source.length;
        final Class<? extends Numeric> clazz = source[0][0].getClass();
        T[] temp = (T[]) Array.newInstance(ClassTools.getInterfaceTypeFor(clazz), rows);
        for (int i = 0; i < rows; i++) temp[i] = source[i][column];
        return new ArrayColumnVector<>(temp);
    }

    @Override
    public long columns() {
        return columns.size();
    }

    @Override
    public long rows() {
        if (columns.isEmpty()) return 0L;
        return columns.get(0).length();  // length of the first column vector
    }

    @Override
    public T valueAt(long row, long column) {
        return columns.get((int) column).elementAt(row);
    }

    @Override
    public T determinant() {
        if (rows() != columns()) {
            throw new ArithmeticException("Can only compute determinant for a square matrix");
        }
        if (columns() == 1L) { // 1x1 matrix
            return valueAt(0L, 0L);
        }
        if (columns() == 2L) {
            T a = valueAt(0L, 0L);
            T b = valueAt(0L, 1L);
            T c = valueAt(1L, 0L);
            T d = valueAt(1L, 1L);
            return (T) a.multiply(d).subtract(c.multiply(b));  // should not require coercion here
        }
        
        final Class<T> clazz = columns.get(0).getElementType();
        try {
            // do not mess with the short circuit evaluation!
            if (columns() > 4L && isTriangular()) {
                Numeric accum = valueAt(0L, 0L);
                for (long index = 1L; index < rows(); index++) {
                    accum = accum.multiply(valueAt(index, index));
                }
                return (T) accum.coerceTo(clazz);
            }
            else {
                // A column-friendly version of the recursive algorithm.
                ColumnVector<T> firstColumn = columns.get(0);
                ColumnarMatrix<T> intermediate = this.removeColumn(0L);
                Numeric accum = ExactZero.getInstance(valueAt(0L, 0L).getMathContext());
                for (long row = 0L; row < rows(); row++) {
                    Numeric coeff = firstColumn.elementAt(row);
                    if (row % 2L == 1L) coeff = coeff.negate(); // alternate sign of the coefficient
                    ColumnarMatrix<T> subMatrix = intermediate.removeRow(row);
                    accum = accum.add(coeff.multiply(subMatrix.determinant()));
                }
                return (T) accum.coerceTo(clazz);
            }
        } catch (CoercionException ex) {
            Logger.getLogger(ColumnarMatrix.class.getName()).log(Level.SEVERE, "Coercion failed computing determinant.", ex);
            throw new ArithmeticException("While converting determinant result: " + ex.getMessage());
        }
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
        if (columns() == 1L) {
            return new SingletonMatrix<>(valueAt(0L, 0L).inverse());
        } else if (columns() == 2L) {
            final Numeric scale = det.inverse();
            T a = valueAt(0L, 0L);
            T b = valueAt(0L, 1L);
            T c = valueAt(1L, 0L);
            T d = valueAt(1L, 1L);
            ColumnarMatrix<Numeric> result = new ColumnarMatrix<>();
            result.append(new ArrayColumnVector<>(d, c.negate()).scale(scale));
            result.append(new ArrayColumnVector<>(b.negate(), a).scale(scale));
            return result;
        }

        // otherwise recursively compute this using the adjoint
        final Matrix<T> adjoint = this.adjoint();
        ColumnarMatrix<Numeric> byAdjoint = new ColumnarMatrix<>();
        final Numeric factor = det.inverse();
        for (long column = 0L; column < adjoint.columns(); column++) {
            byAdjoint.append(((ColumnVector<Numeric>) adjoint.getColumn(column)).scale(factor));
        }
        return byAdjoint;
    }

    @Override
    public Matrix<T> transpose() {
        BasicMatrix<T> result = new BasicMatrix<>();
        columns.stream().map(ColumnVector::transpose).forEachOrdered(result::append);
        return result;
    }

    @Override
    public Matrix<T> add(Matrix<T> addend) {
        if (addend.columns() != columns() || addend.rows() != rows()) {
            throw new ArithmeticException("Cannot add matrices of different dimensions.");
        }
        
        ColumnarMatrix<T> result = new ColumnarMatrix<>();
        for (long column = 0L; column < addend.columns(); column++) {
            result.append(addend.getColumn(column).add((Vector<T>) getColumn(column)));
        }
        return result;
    }

    @Override
    public Matrix<T> multiply(Matrix<T> multiplier) {
        if (this.columns() != multiplier.rows()) {
            throw new ArithmeticException("Multiplier must have the same number of rows as this matrix has columns");
        }
        if (multiplier instanceof DiagonalMatrix) {
            DiagonalMatrix<T> other = (DiagonalMatrix<T>) multiplier;
            ColumnarMatrix<T> result = new ColumnarMatrix<>();
            for (long column = 0L; column < columns(); column++) {
                result.append(getColumn(column).scale(other.valueAt(column, column)));
            }
            return result;
        }
        final Class<T> clazz = columns.get(0).getElementType();
        T[][] temp = (T[][]) Array.newInstance(clazz, (int) this.rows(), (int) multiplier.columns());
        for (long row = 0L; row < rows(); row++) {
            RowVector<T> rowvec = this.getRow(row);
            for (long column = 0L; column < multiplier.columns(); column++) {
                temp[(int) row][(int) column] = rowvec.dotProduct(multiplier.getColumn(column));
            }
        }
        return new ColumnarMatrix<>(temp);
    }

    @Override
    public ColumnVector<T> getColumn(long column) {
        return columns.get((int) column);
    }

    /**
     * Append a column vector to this matrix.
     * @param column the column vector to append
     */
    public final void append(ColumnVector<T> column) {
        if (columns.isEmpty() || column.length() == this.rows()) {
            columns.add(column);
        } else {
            throw new IllegalArgumentException("Column vector must have " + this.rows() + " elements");
        }
    }

    /**
     * Given a column index, return a {@code Matrix} that is this matrix
     * with that column removed.
     * @param column the index of the column to remove
     * @return a matrix with column {@code column} removed
     */
    public ColumnarMatrix<T> removeColumn(long column) {
        if (column < 0L || column >= columns()) throw new IndexOutOfBoundsException("Column " + column + " does not exist");
        ColumnarMatrix<T> result = new ColumnarMatrix<>();
        for (long colidx = 0L; colidx < columns(); colidx++) {
            if (colidx == column) continue;
            result.append(getColumn(colidx));
        }
        return result;
    }

    /**
     * Given a row index, return a {@code Matrix} that is this matrix
     * with that row removed.
     * @param row the index of the row to remove
     * @return a matrix with row {@code row} removed
     */
    public ColumnarMatrix<T> removeRow(long row) {
        if (row < 0L || row >= rows()) throw new IndexOutOfBoundsException("Row " + row + " does not exist");
        ColumnarMatrix<T> result = new ColumnarMatrix<>();
        for (long colidx = 0L; colidx < columns(); colidx++) {
            result.append(removeElementAt(getColumn(colidx), row));
        }
        return result;
    }
    
    private ColumnVector<T> removeElementAt(ColumnVector<T> source, long index) {
        List<T> elementsToKeep = new ArrayList<>((int) source.length() - 1);
        for (long rowidx = 0L; rowidx < source.length(); rowidx++) {
            if (rowidx == index) continue;
            elementsToKeep.add(source.elementAt(rowidx));
        }
        return new ArrayColumnVector<>(elementsToKeep);
    }

    /**
     * Obtain a matrix with elements of the specified type.
     * Upconversion of types is supported; downconversion will result
     * in an exception.
     * @param clazz the desired target type
     * @return a new matrix with elements of type {@code clazz}
     * @param <R> the element type
     * @throws ArithmeticException if upconversion is not supported
     */
    public <R extends Numeric> Matrix<R> upconvert(Class<R> clazz) {
        // first, check to make sure we can do this -- ensure R is a wider type than T
        NumericHierarchy targetType = NumericHierarchy.forNumericType(clazz);
        NumericHierarchy currentType = NumericHierarchy.forNumericType(valueAt(0L, 0L).getClass());
        // if our elements are already of the requested type, just cast and return
        if (currentType == targetType) return (Matrix<R>) this;
        if (currentType != null && currentType.compareTo(targetType) > 0) {
            throw new ArithmeticException("Cannot upconvert elements of " + currentType + " to elements of " + targetType);
        }
        ColumnarMatrix<R> result = new ColumnarMatrix<>();
        for (long column = 0L; column < columns(); column++) {
            final ColumnVector<T> originalColumn = getColumn(column);
            R[] accum = (R[]) Array.newInstance(clazz, (int) originalColumn.length());
            for (long row = 0L; row < originalColumn.length(); row++) {
                try {
                    accum[(int) row] = (R) originalColumn.elementAt(row).coerceTo(clazz);
                } catch (CoercionException ex) {
                    Logger.getLogger(ColumnarMatrix.class.getName()).log(Level.SEVERE,
                            "Coercion failed while upconverting matrix element at " + row + ",\u2009" + column +
                                    " to " + clazz.getTypeName(), ex);
                    throw new ArithmeticException(String.format("While converting value %s to %s at %d, %d",
                            valueAt(row, column), clazz.getTypeName(), row, column));
                }
            }
            result.append(new ArrayColumnVector<>(accum));
        }
        return result;
    }

    /**
     * Return a matrix with row {@code row} and column {@code column}
     * removed.
     * 
     * @param row the row index
     * @param column the column index
     * @return the sub-matrix formed by row and column removal
     */
    public ColumnarMatrix<T> minor(long row, long column) {
        return this.removeColumn(column).removeRow(row);
    }

    /**
     * Compute the cofactor matrix for this matrix.
     * @return the cofactor matrix
     * @see BasicMatrix#cofactor()
     */
    public ColumnarMatrix<T> cofactor() {
        final Class<T> clazz = columns.get(0).getElementType();
        T[][] result = (T[][]) Array.newInstance(clazz, (int) this.rows(), columns.size());
        for (long row = 0L; row < rows(); row++) {
            for (long column = 0L; column < columns(); column++) {
                T intermediate = minor(row, column).determinant();
                if ((row + column) % 2L == 1L) intermediate = (T) intermediate.negate();
                result[(int) row][(int) column] = intermediate;
            }
        }
        return new ColumnarMatrix<>(result);
    }

    /**
     * Compute the adjoint matrix for this matrix.
     * @return the adjoint matrix
     */
    public Matrix<T> adjoint() {
        return cofactor().transpose();
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
        
        Collections.swap(this.columns, (int) column1, (int) column2);
    }

    /**
     * Exchange two rows of this matrix.
     * Note that this is not an efficient operation since this matrix is column major.
     * @param row1 the first row
     * @param row2 the second row
     */
    public void exchangeRows(long row1, long row2) {
        if (row1 < 0L || row1 >= rows()) throw new IndexOutOfBoundsException("row1 must be within bounds 0 - " + (rows() - 1L));
        if (row2 < 0L || row2 >= rows()) throw new IndexOutOfBoundsException("row2 must be within bounds 0 - " + (rows() - 1L));
        if (row1 == row2) return; // NO-OP

        for (int colIdx = 0; colIdx < columns.size(); colIdx++) {
            List<T> elements = columns.get(colIdx).stream().collect(Collectors.toCollection(ArrayList::new));
            Collections.swap(elements, (int) row1, (int) row2);
            columns.set(colIdx, new ArrayColumnVector<>(elements));
        }
    }
    
    @Override
    public boolean isUpperTriangular() {
        long skipRows = 1L;
        boolean hasNonZero = false;
        for (ColumnVector<T> column : columns.subList(0, columns.size() - 1)) {
            hasNonZero = column.stream().skip(skipRows++).anyMatch(x -> !Zero.isZero(x));
            if (hasNonZero) break;
        }
        return !hasNonZero;
    }
    
    @Override
    public boolean isLowerTriangular() {
        long endRow = 1L;
        boolean hasNonZero = false;
        for (ColumnVector<T> column : columns.subList(1, columns.size())) {
            hasNonZero = column.stream().limit(endRow++).anyMatch(x -> !Zero.isZero(x));
            if (hasNonZero) break;
        }
        return !hasNonZero;
    }

    @Override
    public Matrix<T> scale(T scaleFactor) {
        if (One.isUnity(scaleFactor)) return this;
        ColumnarMatrix<T> scaled = new ColumnarMatrix<>();
        columns.stream().map(colVec -> colVec.scale(scaleFactor)).forEach(scaled::append);
        return scaled;
    }

    @Override
    public RowVector<T> getRow(long row) {
        final Class<T> clazz = columns.get(0).getElementType();
        T[] result = (T[]) Array.newInstance(clazz, columns.size());
        for (long col = 0L; col < columns(); col++) {
            result[(int) col] = getColumn(col).elementAt(row);
        }
        return new ArrayRowVector<>(result);
    }

    @Override
    public RealType norm() {
        final Stream<Numeric> allValues = columns.stream().flatMap(ColumnVector::stream)
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


    @Override
    public boolean equals(Object o) {
        if (o instanceof Matrix) {
            Matrix<? extends Numeric> that = (Matrix<? extends Numeric>) o;
            if (columns() != that.columns()) return false;
            for (long column = 0L; column < columns(); column++) {
                if (!getColumn(column).equals(that.getColumn(column))) return false;
            }
            return true;
        }
        return false;
    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 97 * hash + Objects.hashCode(this.columns);
        return hash;
    }

    /*
     Methods necessary for Groovy operator overloading follow.
     */
    public void leftShift(RowVector<T> row) {
        if (row.length() != columns()) throw new IllegalArgumentException("Row vector does not match the width of this matrix");
        for (int column = 0; column < columns.size(); column++) {
            columns.get(column).append(row.elementAt(column));
        }
    }
    public void leftShift(ColumnVector<T> column) {
        this.append(column);
    }
}
