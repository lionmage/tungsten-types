package tungsten.types.set.impl;

import tungsten.types.Numeric;
import tungsten.types.Set;
import tungsten.types.exceptions.CoercionException;

import java.util.Iterator;
import java.util.NoSuchElementException;

public class CoercionWrapperSet<T extends Numeric, R extends Numeric> implements Set<R> {
    private Class<R> clazz;
    private Class<T> origClazz;
    private Set<T> original;

    public CoercionWrapperSet(Set<T> wrapped, Class<T> originType, Class<R> targetType) {
        this.clazz = targetType;
        this.original = wrapped;
        this.origClazz = originType;
    }

    @Override
    public long cardinality() {
        return original.cardinality();
    }

    @Override
    public boolean countable() {
        return original.countable();
    }

    @Override
    public boolean contains(R element) {
        try {
            T origElement = (T) element.coerceTo(origClazz);
            return original.contains(origElement);
        } catch (CoercionException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public void append(R element) {
        throw new UnsupportedOperationException("Append is not supported for this view");
    }

    @Override
    public void remove(R element) {
        throw new UnsupportedOperationException("Remove is not supported for this view");
    }

    @Override
    public Set<R> union(Set<R> other) {
        throw new UnsupportedOperationException("Must be implemented");
    }

    @Override
    public Set<R> intersection(Set<R> other) {
        throw new UnsupportedOperationException("Must be implemented");
    }

    @Override
    public Set<R> difference(Set<R> other) {
        throw new UnsupportedOperationException("Must be implemented");
    }

    @Override
    public Iterator<R> iterator() {
        Iterator<T> origIter = original.iterator();
        return new Iterator<R>() {
            @Override
            public boolean hasNext() {
                return origIter.hasNext();
            }

            @Override
            public R next() {
                try {
                    T nextVal = origIter.next();
                    return (R) nextVal.coerceTo(clazz);
                } catch (CoercionException e) {
                    throw new NoSuchElementException("Iteration failed due to failed coercion: " + e.getMessage());
                }
            }
        };
    }
}
