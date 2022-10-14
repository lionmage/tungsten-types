package tungsten.types.vector.impl;

import tungsten.types.Matrix;
import tungsten.types.Numeric;
import tungsten.types.Vector;
import tungsten.types.exceptions.CoercionException;
import tungsten.types.vector.ColumnVector;
import tungsten.types.vector.RowVector;

import java.lang.reflect.Array;
import java.lang.reflect.ParameterizedType;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

public class ArrayColumnVector<T extends Numeric> extends ColumnVector<T> {
    private T[] elementArray;

    @SafeVarargs
    public ArrayColumnVector(T... elements) {
        elementArray = elements;
        setMathContext(elements[0].getMathContext());
    }

    public ArrayColumnVector(List<T> elementList) {
        final Class<T> clazz = (Class<T>) ((Class) ((ParameterizedType) getClass()
                .getGenericSuperclass()).getActualTypeArguments()[0]);
        this.elementArray = (T[]) Array.newInstance(clazz, elementList.size());
        elementList.toArray(elementArray);
        setMathContext(elementArray[0].getMathContext());
    }

    public ArrayColumnVector(Vector<T> source) {
        if (source.length() > (long) Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Vector is too large to fit into an array.");
        }
        final Class<T> clazz = (Class<T>) ((Class) ((ParameterizedType) source.getClass()
                .getGenericSuperclass()).getActualTypeArguments()[0]);
        this.elementArray = (T[]) Array.newInstance(clazz, (int) source.length());
        for (long index = 0L; index < source.length(); index++) {
            setElementAt(source.elementAt(index), index);
        }
        setMathContext(source.getMathContext());
    }

    @Override
    public RowVector<T> getRow(long row) {
        if (row < 0L || row > length() - 1L) {
            throw new IndexOutOfBoundsException("Element " + row + " does not exist.");
        }
        final Class<T> clazz = (Class<T>) ((Class) ((ParameterizedType) getClass()
                .getGenericSuperclass()).getActualTypeArguments()[0]);
        T[] result = (T[]) Array.newInstance(clazz, 1);
        result[0] = elementArray[(int) row];
        return new ArrayRowVector<T>(result);
    }

    @Override
    public long length() {
        return (long) elementArray.length;
    }

    @Override
    public T elementAt(long position) {
        if (position < 0L || position > length() - 1L) {
            throw new IllegalArgumentException("Index " + position + " out of range.");
        }
        return elementArray[(int) position];
    }

    @Override
    public void setElementAt(T element, long position) {
        if (position < 0L || position > length() - 1L) {
            throw new IllegalArgumentException("Index " + position + " out of range.");
        }
        elementArray[(int) position] = element;
    }

    @Override
    public void append(T element) {
        final Class<T> clazz = (Class<T>) ((Class) ((ParameterizedType) getClass()
                .getGenericSuperclass()).getActualTypeArguments()[0]);
        T[] updated = (T[]) Array.newInstance(clazz, elementArray.length + 1);
        System.arraycopy(elementArray, 0, updated, 0, elementArray.length);
        updated[elementArray.length] = element;
        elementArray = updated;
    }

    @Override
    public ColumnVector<T> add(Vector<T> addend) {
        if (addend.length() != this.length()) throw new ArithmeticException("Cannot add vectors of different lengths.");
        if (addend instanceof RowVector) throw new ArithmeticException("Cannot add a row vector to a column vector.");

        final Class<T> clazz = (Class<T>) ((Class) ((ParameterizedType) getClass()
                .getGenericSuperclass()).getActualTypeArguments()[0]);;
        T[] sumArray = (T[]) Array.newInstance(clazz, elementArray.length);
        try {
            for (int i = 0; i < elementArray.length; i++) {
                sumArray[i] = (T) elementArray[i].add(addend.elementAt((long) i)).coerceTo(clazz);
            }
            return new ArrayColumnVector<>(sumArray);
        } catch (CoercionException ce) {
            Logger.getLogger(ArrayRowVector.class.getName()).log(Level.SEVERE, "Unable to compute sum of row vectors.", ce);
            throw new ArithmeticException("Cannot add row vectors.");
        }
    }

    @Override
    public ColumnVector<T> negate() {
        final Class<T> clazz = (Class<T>) ((ParameterizedType) getClass()
                .getGenericSuperclass()).getActualTypeArguments()[0];
        T[] negArray = (T[]) Array.newInstance(clazz, elementArray.length);
        Arrays.stream(elementArray).map(Numeric::negate).toArray(size -> negArray);
        return new ArrayColumnVector<>(negArray);
    }

    @Override
    public ColumnVector<T> scale(T factor) {
        final Class<T> clazz = (Class<T>) ((Class) ((ParameterizedType) getClass()
                .getGenericSuperclass()).getActualTypeArguments()[0]);
        T[] scaledArray = (T[]) Array.newInstance(clazz, elementArray.length);
        try {
            for (int i = 0; i < elementArray.length; i++) {
                scaledArray[i] = (T) elementArray[i].multiply(factor).coerceTo(clazz);
            }
            return new ArrayColumnVector<>(scaledArray);
        } catch (CoercionException ce) {
            Logger.getLogger(ArrayRowVector.class.getName()).log(Level.SEVERE, "Unable to compute scaled column vector.", ce);
            throw new ArithmeticException("Cannot scale column vector.");
        }
    }

    @Override
    public RowVector<T> transpose() {
        return new ArrayRowVector<T>(elementArray);
    }

    @Override
    public Matrix<T> add(Matrix<T> addend) {
        if (addend.rows() != rows() || addend.columns() != columns()) {
            throw new ArithmeticException("Dimension mismatch for single-column matrix.");
        }
        final Class<T> clazz = (Class<T>) ((Class) ((ParameterizedType) getClass()
                .getGenericSuperclass()).getActualTypeArguments()[0]);
        T[] result = (T[]) Array.newInstance(clazz, elementArray.length);
        try {
            for (long index = 0L; index < elementArray.length; index++) {
                result[(int) index] = (T) elementArray[(int) index].add(addend.valueAt(index, 0L)).coerceTo(clazz);
            }
        } catch (CoercionException ex) {
            throw new ArithmeticException("Unable to coerce matrix element to type " +
                    clazz.getTypeName() + " during matrix addition.");
        }
        return new ArrayColumnVector<>(result);
    }

    @Override
    public Stream<T> stream() {
        return Arrays.stream(elementArray);
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 53 * hash + Arrays.deepHashCode(this.elementArray);
        hash = 53 * hash + Objects.hashCode(getMathContext());
        return hash;
    }
}
