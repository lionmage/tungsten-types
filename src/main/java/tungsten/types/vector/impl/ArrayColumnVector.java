package tungsten.types.vector.impl;

import tungsten.types.Matrix;
import tungsten.types.Numeric;
import tungsten.types.Vector;
import tungsten.types.exceptions.CoercionException;
import tungsten.types.util.MathUtils;
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
    private Class<T> elementType = (Class<T>) ((Class) ((ParameterizedType) getClass()
            .getGenericSuperclass()).getActualTypeArguments()[0]);

    @SafeVarargs
    public ArrayColumnVector(T... elements) {
        elementArray = elements;
        if (elements != null && elements.length > 0) {
            if (elementType == null) elementType = (Class<T>) elements[0].getClass();
            setMathContext(MathUtils.inferMathContext(List.of(elements)));
        }
    }

    public ArrayColumnVector(List<T> elementList) {
        if (elementList.size() > 0 && elementType == null) {
            elementType = (Class<T>) elementList.get(0).getClass();
        }
        this.elementArray = (T[]) Array.newInstance(elementType, elementList.size());
        elementList.toArray(elementArray);
        setMathContext(MathUtils.inferMathContext(elementList));
    }

    public ArrayColumnVector(Vector<T> source) {
        if (source.length() > (long) Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Vector is too large to fit into an array");
        }
        if (source.length() > 0L && elementType == null) {
            elementType = (Class<T>) source.elementAt(0L).getClass();
        }
        this.elementArray = (T[]) Array.newInstance(elementType, (int) source.length());
        for (long index = 0L; index < source.length(); index++) {
            setElementAt(source.elementAt(index), index);
        }
        setMathContext(source.getMathContext());
    }

    @Override
    public RowVector<T> getRow(long row) {
        if (row < 0L || row > length() - 1L) {
            throw new IndexOutOfBoundsException("Element " + row + " does not exist");
        }
        T[] result = (T[]) Array.newInstance(elementType, 1);
        result[0] = elementArray[(int) row];
        return new ArrayRowVector<>(result);
    }

    @Override
    public long length() {
        return elementArray.length;
    }

    @Override
    public T elementAt(long position) {
        final long upperLimit = length() - 1L;
        if (position < 0L || position > upperLimit) {
            throw new IndexOutOfBoundsException("Index " + position + " out of range 0\u2013" + upperLimit);
        }
        return elementArray[(int) position];
    }

    @Override
    public void setElementAt(T element, long position) {
        final long upperLimit = length() - 1L;
        if (position < 0L || position > upperLimit) {
            throw new IndexOutOfBoundsException("Index " + position + " out of range 0\u2013" + upperLimit);
        }
        elementArray[(int) position] = element;
    }

    @Override
    public void append(T element) {
        T[] updated = (T[]) Array.newInstance(elementType, elementArray.length + 1);
        System.arraycopy(elementArray, 0, updated, 0, elementArray.length);
        updated[elementArray.length] = element;
        elementArray = updated;
    }

    @Override
    public ColumnVector<T> add(Vector<T> addend) {
        if (addend.length() != this.length()) throw new ArithmeticException("Cannot add vectors of different lengths");
        if (addend instanceof RowVector) throw new ArithmeticException("Cannot add a row vector to a column vector");

        T[] sumArray = (T[]) Array.newInstance(elementType, elementArray.length);
        try {
            for (int i = 0; i < elementArray.length; i++) {
                sumArray[i] = (T) elementArray[i].add(addend.elementAt(i)).coerceTo(elementType);
            }
            return new ArrayColumnVector<>(sumArray);
        } catch (CoercionException ce) {
            Logger.getLogger(ArrayRowVector.class.getName()).log(Level.SEVERE, "Unable to compute sum of column vectors.", ce);
            throw new ArithmeticException("Cannot add column vectors");
        }
    }

    @Override
    public ColumnVector<T> negate() {
        T[] negArray = Arrays.stream(elementArray).map(Numeric::negate)
                .map(elementType::cast).toArray(size -> (T[]) Array.newInstance(elementType, size));
        return new ArrayColumnVector<>(negArray);
    }

    @Override
    public ColumnVector<T> scale(T factor) {
        T[] scaledArray = (T[]) Array.newInstance(elementType, elementArray.length);
        try {
            for (int i = 0; i < elementArray.length; i++) {
                scaledArray[i] = (T) elementArray[i].multiply(factor).coerceTo(elementType);
            }
            return new ArrayColumnVector<>(scaledArray);
        } catch (CoercionException ce) {
            Logger.getLogger(ArrayRowVector.class.getName()).log(Level.SEVERE, "Unable to compute scaled column vector.", ce);
            throw new ArithmeticException("Cannot scale column vector");
        }
    }

    @Override
    public RowVector<T> transpose() {
        return new ArrayRowVector<T>(elementArray);
    }

    @Override
    public Matrix<T> add(Matrix<T> addend) {
        if (addend.rows() != rows() || addend.columns() != columns()) {
            throw new ArithmeticException("Dimension mismatch for single-column matrix");
        }
        T[] result = (T[]) Array.newInstance(elementType, elementArray.length);
        try {
            for (long index = 0L; index < elementArray.length; index++) {
                result[(int) index] = (T) elementArray[(int) index].add(addend.valueAt(index, 0L)).coerceTo(elementType);
            }
        } catch (CoercionException ex) {
            throw new ArithmeticException("Unable to coerce matrix element to type " +
                    elementType.getTypeName() + " during matrix addition");
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
