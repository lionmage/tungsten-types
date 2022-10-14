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
import tungsten.types.numerics.RationalType;
import tungsten.types.numerics.impl.ExactZero;
import tungsten.types.numerics.impl.RationalImpl;
import tungsten.types.numerics.impl.Zero;
import tungsten.types.vector.ColumnVector;
import tungsten.types.vector.RowVector;
import tungsten.types.vector.impl.ArrayColumnVector;
import tungsten.types.vector.impl.ArrayRowVector;

import java.lang.reflect.Array;
import java.lang.reflect.ParameterizedType;
import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * This class provides a restricted, read-only view into the supplied {@link Matrix}.
 * It is a lightweight façade and is especially suitable for use with large
 * matrices that may not be backed by rapid storage (i.e., may not reside in memory).
 *
 * @author Robert Poole <Tarquin.AZ@gmail.com>
 * @param <T> the {@link Numeric} subtype for the elements of this matrix
 */
public class SubMatrix<T extends Numeric> implements Matrix<T> {
    private final Matrix<T> original;
    private long startRow, endRow;
    private long startColumn, endColumn;
    private final List<Long> removedRows = new ArrayList<>();
    private final List<Long> removedColumns = new ArrayList<>();
    // cache of tests for upper/lower triangularity
    private Boolean upperTriangular;
    private Boolean lowerTriangular;
    private final Lock ltLock = new ReentrantLock();
    private final Lock utLock = new ReentrantLock();
    
    public SubMatrix(Matrix<T> original) {
        this.original = original;
        startRow = 0L;
        endRow = original.rows() - 1L; // bounds are inclusive
        startColumn = 0L;
        endColumn = original.columns() - 1L;
    }
    
    public SubMatrix(Matrix<T> original, long row1, long column1, long row2, long column2) {
        if (row1 < 0L || row1 >= original.rows() || row2 < 0L || row2 >= original.rows()) {
            throw new IllegalArgumentException("Row indices must be within range.");
        }
        if (column1 < 0L || column1 >= original.columns() || column2 < 0L || column2 >= original.columns()) {
            throw new IllegalArgumentException("Column indices must be within range.");
        }
        if (row1 > row2) throw new IllegalArgumentException("row1 must be \u2264 row2");
        if (column1 > column2) throw new IllegalArgumentException("column1 must be \u2264 column2");
        this.original = original;
        this.startRow = row1;
        this.endRow   = row2;
        this.startColumn = column1;
        this.endColumn   = column2;
    }

    @Override
    public long columns() {
        return internalColumns() - removedColumns.size();
    }

    @Override
    public long rows() {
        return internalRows() - removedRows.size();
    }
    
    @Override
    public boolean isUpperTriangular() {
        utLock.lock();
        try {
            if (upperTriangular != null) return upperTriangular.booleanValue();
            // cache this for posterity
            upperTriangular = Matrix.super.isUpperTriangular();
            return upperTriangular.booleanValue();
        } finally {
            utLock.unlock();
        }
    }
    
    @Override
    public boolean isLowerTriangular() {
        ltLock.lock();
        try {
            if (lowerTriangular != null) return lowerTriangular.booleanValue();
            lowerTriangular = Matrix.super.isLowerTriangular();
            return lowerTriangular.booleanValue();
        } finally {
            ltLock.unlock();
        }
    }
    
    protected long internalRows() {
        return endRow - startRow + 1L;
    }
    
    protected long internalColumns() {
        return endColumn - startColumn + 1L;
    }
    
    public void removeRow(long row) {
        if (row < 0L || row >= internalRows()) throw new IndexOutOfBoundsException("Row index " + row + " out of bounds");
        geometryChanged();
        if (row == 0L) {
            final AtomicLong removedCount = new AtomicLong(1L);
            // this is cheaper than tracking a removed row, but is irreversible
            while (removedRows.contains(++startRow)) { // incrementally move the start bound inward
                removedRows.remove(startRow); // eat up any adjacent rows that were marked as removed
                removedCount.incrementAndGet();
            }
            // shift all indices
            removedRows.replaceAll(val -> val - removedCount.longValue());
            return;
        } else if (row == internalRows() - 1L) {
            while (removedRows.contains(--endRow)) {
                removedRows.remove(endRow);
            }
            return;
        }
        if (!removedRows.contains(row)) removedRows.add(row);
    }
    
    public void removeColumm(long column) {
        if (column < 0L || column >= internalColumns()) throw new IndexOutOfBoundsException("Column index " + column + " out of bounds");
        geometryChanged();
        if (column == 0L) {
            final AtomicLong removedCount = new AtomicLong(1L);
            // this is cheaper than tracking a removed row, but is irreversible
            while (removedColumns.contains(++startColumn)) { // incrementally move the start bound inward
                removedColumns.remove(startColumn); // eat up any adjacent rows that were marked as removed
                removedCount.incrementAndGet();
            }
            // shift all indices
            removedColumns.replaceAll(val -> val - removedCount.longValue());
            return;
        } else if (column == internalColumns() - 1L) {
            while (removedColumns.contains(--endColumn)) {
                removedColumns.remove(endColumn);
            }
            return;
        }
        if (!removedColumns.contains(column)) removedColumns.add(column);
    }
    
    private void geometryChanged() {
        ltLock.lock();
        utLock.lock();
        try {
            lowerTriangular = null;
            upperTriangular = null;
        } finally {
            ltLock.unlock();
            utLock.unlock();
        }
    }
    
    private long computeRowIndex(long row) {
        long result = startRow + row;
        final AtomicLong intermediateRow = new AtomicLong(result);
        final List<Long> localRemovedRows = new ArrayList<>(removedRows);
        long rowsToBeRemoved = localRemovedRows.stream().filter(x -> x <= intermediateRow.get()).count();
        while (localRemovedRows.removeIf(x -> x <= intermediateRow.get())) {
            result = intermediateRow.addAndGet(rowsToBeRemoved);
            // compute the next number of rows to be decimated
            rowsToBeRemoved = localRemovedRows.stream().filter(x -> x <= intermediateRow.get()).count();
        }
        if (result >= original.rows()) {
            throw new IndexOutOfBoundsException(String.format("Provided row index %d maps to %d in the underlying matrix, whoch only has %d rows.",
                    row, result, original.rows()));
        }
        return result;
    }
    
    private long computeColumnIndex(long column) {
        long result = startColumn + column;
        final AtomicLong intermediateColumn = new AtomicLong(result);
        final List<Long> localRemovedColumns = new ArrayList<>(removedColumns);
        long columnsToBeRemoved = localRemovedColumns.stream().filter(x -> x <= intermediateColumn.get()).count();
        while (localRemovedColumns.removeIf(x -> x <= intermediateColumn.get())) {
            result = intermediateColumn.addAndGet(columnsToBeRemoved);
            // compute the next number of columns to be decimated
            columnsToBeRemoved = localRemovedColumns.stream().filter(x -> x <= intermediateColumn.get()).count();
        }
        if (result >= original.columns()) {
            throw new IndexOutOfBoundsException(String.format("Provided column index %d maps to %d in the underlying matrix, whoch only has %d columns.",
                    column, result, original.columns()));
        }
        return result;
    }

    @Override
    public T valueAt(long row, long column) {
        if (row < 0L || row >= rows()) throw new IndexOutOfBoundsException("Row parameter is out of range.");
        if (column < 0L || column >= columns()) throw new IndexOutOfBoundsException("Column parameter is out of range.");
        return original.valueAt(computeRowIndex(row), computeColumnIndex(column));
    }

    @Override
    public T determinant() {
        if (rows() != columns()) throw new ArithmeticException("Determinant only applies to square matrices.");
        if (rows() == 2L) {
            T a = valueAt(0L, 0L);
            T b = valueAt(0L, 1L);
            T c = valueAt(1L, 0L);
            T d = valueAt(1L, 1L);
            return (T) a.multiply(d).subtract(c.multiply(b));
        }
        if (rows() > 4L && isTriangular()) {
            Numeric accum = valueAt(0L, 0L);
            for (long index = 1L; index < rows(); index++) {
                accum = accum.multiply(valueAt(index, index));
                if (Zero.isZero(accum)) break;
            }
            return (T) accum;
        }
        
        RowVector<T> firstRow = this.getRow(0L);
        SubMatrix<T> intermediate = this.duplicate();
        intermediate.removeRow(0L);
        Numeric accum = ExactZero.getInstance(valueAt(0L, 0L).getMathContext());
        for (long column = 0L; column < columns(); column++) {
            Numeric coeff = firstRow.elementAt(column);
            if (column % 2L == 1L) coeff = coeff.negate(); // alternate sign of the coefficient
            SubMatrix<T> sub = intermediate.duplicate();
            sub.removeColumm(column);
            accum = accum.add(coeff.multiply(sub.determinant()));
        }
        return (T) accum;
    }

    @Override
    public Matrix<? extends Numeric> inverse() {
        final T det = this.determinant();
        if (Zero.isZero(det)) throw new ArithmeticException("This submatrix is singular.");
        
        final Numeric scale = det.inverse();
        List<FutureTask<T>> subtasks = new LinkedList<>();
        BasicMatrix<Numeric> result = new BasicMatrix<>();
        ExecutorService executor = Executors.newCachedThreadPool();
        for (long row = 0L; row < rows(); row++) {
            for (long column = 0L; column < columns(); column++) {
                FutureTask<T> task = computeCofactor(row, column);
                subtasks.add(task);
                executor.submit(task);
            }
            // copy the results into a row vector
            RowVector<Numeric> rowVec = new ArrayRowVector<>(subtasks.stream().map(task -> {
                try {
                    return task.get();
                } catch (InterruptedException | ExecutionException ex) {
                    Logger.getLogger(SubMatrix.class.getName()).log(Level.SEVERE, "Cofactor computation interrupted.", ex);
                    throw new IllegalStateException(ex);
                }
            }).collect(Collectors.toList()));
            result.append(rowVec.scale(scale));
            // and clear out the subtasks list for the next row
            subtasks.clear();
        }
        executor.shutdownNow();
        return result.transpose();  // adjoint scaled by 1/det
    }
    
    private FutureTask<T> computeCofactor(long row, long column) {
        SubMatrix<T> sub = this.duplicate();
        sub.removeRow(row);
        sub.removeColumm(column);
        return new FutureTask<>(() -> {
            T intermediate = sub.determinant();
            if ((row + column) % 2L == 1L) intermediate = (T) intermediate.negate();
            return intermediate;
        });
    }

    @Override
    public Matrix<T> add(Matrix<T> addend) {
        if (addend.rows() != this.rows() || addend.columns() != this.columns()) {
            throw new ArithmeticException("Addend must have the same dimensions as this matrix.");
        }
        BasicMatrix<T> sum = new BasicMatrix<>();
        for (long index = 0L; index < rows(); index++) {
            sum.append(getRow(index).add((Vector<T>) addend.getRow(index)));
        }
        return sum;
    }

    /**
     * Multiply this matrix by {@code multiplier}. This implementation is
     * optimized for the case where both matrices are square and have the same
     * dimensions, and those dimensions are of
     * the form 2<sup>n</sup>&times;2<sup>n</sup>.  Note that this behavior
     * may result in slower behavior for smaller matrices, but should improve
     * as matrix dimensions increase due to parallel computation gains.
     * 
     * @param multiplier
     * @return 
     */
    @Override
    public Matrix<T> multiply(Matrix<T> multiplier) {
        if (this.columns() != multiplier.rows()) {
            throw new ArithmeticException("Multiplier must have the same number of rows as this matrix has columns.");
        }
        if (isDimensionPowerOf2(this.rows()) && isDimensionPowerOf2(multiplier.columns())
                && this.rows() == multiplier.columns()) {
            if (rows() == 1L) return new SingletonMatrix<>((T) this.valueAt(0L, 0L).multiply(multiplier.valueAt(0L, 0L)));
            ForkJoinPool commonPool = ForkJoinPool.commonPool();
            MatMultRecursiveTask task = new MatMultRecursiveTask(this, multiplier);
            return commonPool.invoke(task);
        }

        final Class<T> clazz = (Class<T>) ((Class) ((ParameterizedType) this.getClass()
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
    
    private boolean isDimensionPowerOf2(long dimension) {
        return BigInteger.valueOf(dimension).bitCount() == 1;
    }
    
    private class MatMultRecursiveTask extends RecursiveTask<Matrix<T>> {
        private final Matrix<T> A, B;
        long size;
        
        private MatMultRecursiveTask(Matrix<T> A, Matrix<T> B) {
            this.A = A;
            this.B = B;
            assert A.rows() == B.rows() && A.columns() == B.columns()
                    && A.rows() == A.columns();
            size = A.rows();
        }

        @Override
        protected Matrix<T> compute() {
            if (size == 1L) {
                return new SingletonMatrix<>((T) A.valueAt(0L, 0L).multiply(B.valueAt(0L, 0L)));
            }
            
            List<MatMultRecursiveTask> tasks = createSubTasks();
            ForkJoinTask.invokeAll(tasks);
            
            Iterator<MatMultRecursiveTask> iter = tasks.iterator();
            
            // note that these results rely on the subcalculations being added to the tasklist in a specific order
            Matrix<T> C00 = iter.next().join().add(iter.next().join());
            Matrix<T> C01 = iter.next().join().add(iter.next().join());
            Matrix<T> C10 = iter.next().join().add(iter.next().join());
            Matrix<T> C11 = iter.next().join().add(iter.next().join());
            
            return new AggregateMatrix<>(new Matrix[][] {{C00, C01}, {C10, C11}});
        }
        
        private List<MatMultRecursiveTask> createSubTasks() {
            long n = size >> 1L;
            
            SubMatrix<T> A00 = new SubMatrix<>(A, 0L, 0L, n - 1, n - 1);
            SubMatrix<T> A01 = new SubMatrix<>(A, 0L, n, n - 1, size - 1L);
            SubMatrix<T> A10 = new SubMatrix<>(A, n, 0L, size - 1L, n - 1L);
            SubMatrix<T> A11 = new SubMatrix<>(A, n, n, size - 1L, size - 1L);
            
            SubMatrix<T> B00 = new SubMatrix<>(B, 0L, 0L, n - 1, n - 1);
            SubMatrix<T> B01 = new SubMatrix<>(B, 0L, n, n - 1, size - 1L);
            SubMatrix<T> B10 = new SubMatrix<>(B, n, 0L, size - 1L, n - 1L);
            SubMatrix<T> B11 = new SubMatrix<>(B, n, n, size - 1L, size - 1L);

            return Arrays.asList(
                    new MatMultRecursiveTask(A00, B00),
                    new MatMultRecursiveTask(A01, B10),
                    
                    new MatMultRecursiveTask(A00, B01),
                    new MatMultRecursiveTask(A01, B11),
                    
                    new MatMultRecursiveTask(A10, B00),
                    new MatMultRecursiveTask(A11, B10),
                    
                    new MatMultRecursiveTask(A10, B01),
                    new MatMultRecursiveTask(A11, B11)
            );
        }
    }
        
    public SubMatrix<T> duplicate() {
        SubMatrix<T> dup = new SubMatrix<>(original, startRow, startColumn, endRow, endColumn);
        dup.removedRows.addAll(this.removedRows);
        dup.removedColumns.addAll(this.removedColumns);
        return dup;
    }

    @Override
    public RowVector<T> getRow(long row) {
        List<T> result = new ArrayList<>();
        original.getRow(computeRowIndex(row)).stream().skip(startColumn).limit(internalColumns()).forEachOrdered(result::add);
        removeFromList(result, removedColumns);
        // TODO fix this when we have List-based vectors
        return new ArrayRowVector<>(result);
    }

    @Override
    public ColumnVector<T> getColumn(long column) {
        List<T> result = new ArrayList<>();
        original.getColumn(computeColumnIndex(column)).stream().skip(startRow).limit(internalRows()).forEachOrdered(result::add);
        removeFromList(result, removedRows);
        // TODO fix this when we have List-based vectors
        return new ArrayColumnVector<>(result);
    }

    private void removeFromList(List<T> source, List<Long> indices) {
        indices.sort(Comparator.naturalOrder());  // make sure the biggest indices are at the end
        ListIterator<Long> iter = indices.listIterator(indices.size());
        while (iter.hasPrevious()) {  // work backwards through the list of indices and remove
            source.remove(iter.previous().intValue());
        }
    }
    
    protected SortedSet<Long> getRemovedRowIndices() {
        return new TreeSet<>(removedRows);
    }
    
    protected SortedSet<Long> getRemovedColumnIndices() {
        return new TreeSet<>(removedColumns);
    }
    
    protected void clearRemovedRows() {
        geometryChanged();
        removedRows.clear();
    }
    
    protected void clearRemovedColumns() {
        geometryChanged();
        removedColumns.clear();
    }
    
    @Override
    public boolean equals(Object o) {
        if (o instanceof Matrix) {
            if (o instanceof SubMatrix) {
                SubMatrix<?> other = (SubMatrix<?>) o;
                // test for referential equality for speed
                if (other.original != this.original) return false;
                return this.startRow == other.startRow &&
                        this.endRow  == other.endRow &&
                        this.startColumn == other.startColumn &&
                        this.endColumn   == other.endColumn &&
                        this.getRemovedRowIndices().equals(other.getRemovedRowIndices()) &&
                        this.getRemovedColumnIndices().equals(other.getRemovedColumnIndices());
            } else {
                Matrix<? extends Numeric> other = (Matrix<Numeric>) o;
                if (rows() != other.rows()) return false;
                for (long row = 0L; row < rows(); row++) {
                    if (!getRow(row).equals(other.getRow(row))) return false;
                }
                return true;
            }
        }
        return false;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 29 * hash + (int) (this.startRow ^ (this.startRow >>> 32));
        hash = 29 * hash + (int) (this.endRow ^ (this.endRow >>> 32));
        hash = 29 * hash + (int) (this.startColumn ^ (this.startColumn >>> 32));
        hash = 29 * hash + (int) (this.endColumn ^ (this.endColumn >>> 32));
        hash = 29 * hash + Objects.hashCode(this.removedRows);
        hash = 29 * hash + Objects.hashCode(this.removedColumns);
        return hash;
    }
    
    private static final RationalType scaleThreshold = new RationalImpl("1/3");

    @Override
    public Matrix<T> scale(T scaleFactor) {
        final RationalType ratioR = new RationalImpl(BigInteger.valueOf(this.rows()), BigInteger.valueOf(original.rows()));
        final RationalType ratioC = new RationalImpl(BigInteger.valueOf(this.columns()), BigInteger.valueOf(original.columns()));
        if (ratioR.compareTo(scaleThreshold) <= 0 || ratioC.compareTo(scaleThreshold) <= 0) {
            // if this submatrix is significantly smaller than the original,
            // copy only the relevant values to a smaller matrix and scale that
            return new BasicMatrix<>(this).scale(scaleFactor);
        }
        SubMatrix<T> scaled = new SubMatrix<>(original.scale(scaleFactor), startRow, startColumn, endRow, endColumn);
        scaled.removedRows.addAll(this.removedRows);
        scaled.removedColumns.addAll(this.removedColumns);
        return scaled;
    }
    
    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder();
        buf.append("[\n");
        for (long row = 0L; row < rows(); row++) {
            buf.append("\u00A0\u00A0").append(this.getRow(row)).append('\n');
        }
        buf.append("\u00A0]");
        return buf.toString();
    }
}
