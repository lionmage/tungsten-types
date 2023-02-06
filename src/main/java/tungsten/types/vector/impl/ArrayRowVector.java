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

public class ArrayRowVector<T extends Numeric> extends RowVector<T> {
    private T[] elementArray;
    private Class<T> elementType = (Class<T>) ((Class) ((ParameterizedType) getClass()
            .getGenericSuperclass()).getActualTypeArguments()[0]);

    @SafeVarargs
    public ArrayRowVector(T... elements) {
        this.elementArray = elements;
        if (elements != null && elements.length > 0) {
            if (elementType == null) elementType = (Class<T>) elements[0].getClass();
            setMathContext(elements[0].getMathContext());
        }
    }

    public ArrayRowVector(List<T> elementList) {
        if (elementList.size() > 0 && elementType == null) {
            elementType = (Class<T>) elementList.get(0).getClass();
        }
        this.elementArray = (T[]) Array.newInstance(elementType, elementList.size());
        elementList.toArray(elementArray);
        setMathContext(elementArray[0].getMathContext());
    }

    public ArrayRowVector(Vector<T> source) {
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
    public ColumnVector<T> getColumn(long column) {
        if (column < 0L || column > length() - 1L) {
            throw new IndexOutOfBoundsException("Element " + column + " does not exist");
        }
        T[] result = (T[]) Array.newInstance(elementType, 1);
        result[0] = elementArray[(int) column];
        return new ArrayColumnVector<>(result);
    }

    @Override
    public long length() {
        return this.elementArray.length;
    }

    @Override
    public T elementAt(long position) {
        return elementArray[(int) position];
    }

    @Override
    public void setElementAt(T element, long position) {
        if (position < 0L || position > length() - 1L) {
            throw new IndexOutOfBoundsException("No element at " + position);
        }
        elementArray[(int) position] = element;
    }

    @Override
    public void append(T element) {
        T[] updated = (T[]) Array.newInstance(elementType, elementArray == null ? 1 : elementArray.length + 1);
        if (elementArray != null && elementArray.length > 0) {
            System.arraycopy(elementArray, 0, updated, 0, elementArray.length);
            updated[elementArray.length] = element;
        } else {
            updated[0] = element;
        }
        elementArray = updated;
    }

    @Override
    public RowVector<T> add(Vector<T> addend) {
        if (addend.length() != this.length()) throw new ArithmeticException("Cannot add vectors of different lengths");
        if (addend instanceof ColumnVector) throw new ArithmeticException("Cannot add a row vector to a column vector");

        T[] sumArray = (T[]) Array.newInstance(elementType, elementArray.length);
        try {
            for (int i = 0; i < elementArray.length; i++) {
                sumArray[i] = (T) elementArray[i].add(addend.elementAt(i)).coerceTo(elementType);
            }
            return new ArrayRowVector<>(sumArray);
        } catch (CoercionException ce) {
            Logger.getLogger(ArrayRowVector.class.getName()).log(Level.SEVERE, "Unable to compute sum of row vectors.", ce);
            throw new ArithmeticException("Cannot add row vectors.");
        }
    }

    @Override
    public RowVector<T> negate() {
        T[] negArray = Arrays.stream(elementArray).map(Numeric::negate)
                .map(elementType::cast).toArray(size -> (T[]) Array.newInstance(elementType, size));
        return new ArrayRowVector<>(negArray);
    }

    @Override
    public RowVector<T> scale(T factor) {
        T[] scaledArray = (T[]) Array.newInstance(elementType, elementArray.length);
        try {
            for (int i = 0; i < elementArray.length; i++) {
                scaledArray[i] = (T) elementArray[i].multiply(factor).coerceTo(elementType);
            }
            return new ArrayRowVector<>(scaledArray);
        } catch (CoercionException ce) {
            Logger.getLogger(ArrayRowVector.class.getName()).log(Level.SEVERE, "Unable to compute scaled row vector.", ce);
            throw new ArithmeticException("Cannot scale row vector.");
        }
    }

    @Override
    public ColumnVector<T> transpose() {
        return new ArrayColumnVector<>(elementArray);
    }

    @Override
    public Stream<T> stream() {
        return Arrays.stream(elementArray);
    }

    @Override
    public Matrix<T> add(Matrix<T> addend) {
        if (addend.rows() != rows() || addend.columns() != columns()) {
            throw new ArithmeticException("Dimension mismatch for single-row matrix");
        }
        T[] result = (T[]) Array.newInstance(elementType, elementArray.length);
        try {
            for (long index = 0L; index < elementArray.length; index++) {
                    result[(int) index] = (T) elementArray[(int) index].add(addend.valueAt(0L, index)).coerceTo(elementType);
            }
        } catch (CoercionException ex) {
            throw new ArithmeticException("Unable to coerce matrix element to type " +
                    elementType.getTypeName() + " during matrix addition");
        }
        return new ArrayRowVector<>(result);
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 53 * hash + Arrays.deepHashCode(this.elementArray);
        hash = 53 * hash + Objects.hashCode(getMathContext());
        return hash;
    }
}
