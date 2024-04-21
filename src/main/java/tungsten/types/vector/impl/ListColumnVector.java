package tungsten.types.vector.impl;

import tungsten.types.Matrix;
import tungsten.types.Numeric;
import tungsten.types.Vector;
import tungsten.types.exceptions.CoercionException;
import tungsten.types.util.ClassTools;
import tungsten.types.util.OptionalOperations;
import tungsten.types.vector.ColumnVector;
import tungsten.types.vector.RowVector;

import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
import java.util.stream.Stream;

public class ListColumnVector<T extends Numeric> extends ColumnVector<T> {
    public static final long RANDOM_ACCESS_THRESHOLD = 1_000L;
    private final List<T> elements;
    private transient long elementCount = -1L;
    private final ReadWriteLock rwl = new ReentrantReadWriteLock();

    public ListColumnVector() {
        elements = new LinkedList<>();
        elementCount = 0L;
    }

    public ListColumnVector(Class<T> clazz) {
        super(clazz);
        elements = new LinkedList<>();
        elementCount = 0L;
    }

    public ListColumnVector(List<T> source) {
        super((Class<T>) ClassTools.getInterfaceTypeFor(ClassTools.getBaseTypeFor(source)));
        elements = source;
    }

    /**
     * A copy constructor.
     * @param source the {@link ColumnVector} to duplicate
     */
    public ListColumnVector(ColumnVector<T> source) {
        super(source.getElementType());
        if (source.length() < RANDOM_ACCESS_THRESHOLD) {
            elements = new ArrayList<>();
        } else {
            elements = new LinkedList<>();
        }
        source.stream().forEachOrdered(elements::add);
    }

    public ListColumnVector(Vector<T> source) {
        super(source.getElementType());
        if (source.length() < RANDOM_ACCESS_THRESHOLD) {
            elements = new ArrayList<>();
        } else {
            elements = new LinkedList<>();
        }
        LongStream.range(0L, source.length()).mapToObj(source::elementAt).forEachOrdered(elements::add);
    }

    @Override
    public long length() {
        if (elementCount < 0L) {
            if (elements instanceof RandomAccess) {
                elementCount = elements.size();
            } else {
                Lock lock = rwl.readLock();
                lock.lock();
                try {
                    elementCount = elements.stream().count();
                } finally {
                    lock.unlock();
                }
            }
        }
        return elementCount;
    }

    @Override
    public T elementAt(long position) {
        Lock lock = rwl.readLock();
        lock.lock();
        try {
            if (elements instanceof RandomAccess) {
                return elements.get((int) position);
            }
            return elements.stream().skip(position).findFirst().orElseThrow();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void setElementAt(T element, long position) {
        Lock lock = rwl.writeLock();
        lock.lock();
        try {
            if (position > (long) elements.size()) {
                final T zero = OptionalOperations.dynamicInstantiate(getElementType(), 0d);
                int count = (int) position - elements.size();
                elements.addAll(Collections.nCopies(count, zero));
                assert position == (long) elements.size();
            }
            if (position == (long) elements.size()) {
                elements.add(element);
                return;
            } else if (elements instanceof RandomAccess) {
                elements.set((int) position, element);
                return;
            }
            int startIndex = position < (long) Integer.MAX_VALUE ? (int) position : Integer.MAX_VALUE;
            ListIterator<T> iter = elements.listIterator(startIndex);
            if ((long) startIndex < position) {
                long currIdx = startIndex;
                while (currIdx < position) {
                    iter.next();
                    currIdx++;
                }
            }
            iter.set(element);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void append(T element) {
        Lock lock = rwl.writeLock();
        lock.lock();
        try {
            boolean success = elements.add(element);
            // the IDE says success should always be true, but the general contract for
            // Collection says otherwise, and some subclasses/implementations of List
            // may likewise refuse to append an element
            // besides which, if add() throws an exception, the next line of code won't
            // execute no matter what!
            if (success && elementCount >= 0L) elementCount++;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public ColumnVector<T> add(Vector<T> addend) {
        if (addend.length() != this.length()) throw new IllegalArgumentException("Vectors must be of the same length");
        Lock lock = rwl.readLock();
        lock.lock();
        try {
            LinkedList<T> result = new LinkedList<>();
            for (long k = 0L; k < addend.length(); k++) {
                T sum = (T) elementAt(k).add(addend.elementAt(k)).coerceTo(getElementType());
                result.add(sum);
            }
            return new ListColumnVector<>(result);
        } catch (CoercionException e) {
            throw new IllegalStateException("While adding vectors", e);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public ColumnVector<T> negate() {
        Lock lock = rwl.readLock();
        lock.lock();
        try {
            LinkedList<T> result = new LinkedList<>();
            elements.stream().map(Numeric::negate).map(getElementType()::cast).forEachOrdered(result::add);
            return new ListColumnVector<>(result);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public ColumnVector<T> scale(T factor) {
        Lock lock = rwl.readLock();
        lock.lock();
        try {
            LinkedList<T> result = new LinkedList<>();
            elements.stream().map(x -> x.multiply(factor)).map(getElementType()::cast).forEachOrdered(result::add);
            return new ListColumnVector<>(result);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public RowVector<T> transpose() {
        return new ListRowVector<>(elements);
    }

    @Override
    public Matrix<T> add(Matrix<T> addend) {
        if (addend.columns() != 1L || addend.rows() != length()) {
            throw new IllegalArgumentException("Matrices must have equal dimensions");
        }
        return add((Vector<T>) addend.getColumn(0L));
    }

    /**
     * Obtain an in-order {@link Stream} of the
     * elements in this column vector. For concurrency
     * safety, this method obtains a read lock
     * internally and does not relinquish that lock
     * until the stream is closed.  It is thus
     * not recommended to obtain a stream if
     * concurrent write operations are expected.
     * @return a stream of this column vector's elements
     */
    @Override
    public Stream<T> stream() {
        final Lock lock = rwl.readLock();
        lock.lock();
        return elements.stream().onClose(lock::unlock);
    }

    @Override
    public ColumnVector<T> copy() {
        final List<T> elementsCopy;
        final Lock lock = rwl.readLock();
        lock.lock();
        try {
            if (elements instanceof RandomAccess) {
                elementsCopy = new ArrayList<>(elements);
            } else {
                elementsCopy = new LinkedList<>(elements);
            }
        } finally {
            lock.unlock();
        }
        return new ListColumnVector<>(elementsCopy);
    }

    @Override
    public ColumnVector<T> trimTo(long rows) {
        if (rows >= this.rows()) return this;
        final Lock lock = rwl.readLock();
        lock.lock();
        try {
            if (elements instanceof RandomAccess) {
                return new ListColumnVector<>(elements.subList(0, (int) rows));
            }
            return new ListColumnVector<>((List<T>) elements.stream().limit(rows).collect(Collectors.toCollection(LinkedList::new)));
        } finally {
            lock.unlock();
        }
    }

    @Override
    public int hashCode() {
        return Objects.hash(elements, rwl, getMathContext());
    }
}
