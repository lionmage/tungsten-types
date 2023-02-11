package tungsten.types.set.impl;
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

import tungsten.types.Numeric;
import tungsten.types.Set;
import tungsten.types.exceptions.CoercionException;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.StreamSupport;

/**
 * For when you have a {@link Set} of one {@link Numeric} subtype, but you need
 * the elements to be of a different subtype.
 * @param <T> the type of the set being wrapped
 * @param <R> the type of this view of the wrapped set
 */
public class CoercionWrapperSet<T extends Numeric, R extends Numeric> implements Set<R> {
    private final Class<R> clazz;
    private final Class<T> origClazz;
    private final Set<T> original;

    public CoercionWrapperSet(Set<T> wrapped, Class<T> originType, Class<R> targetType) {
        this.clazz = targetType;
        this.original = wrapped;
        this.origClazz = originType;
    }

    public CoercionWrapperSet(Set<T> wrapped, Class<R> targetType) {
        this.clazz = targetType;
        this.original = wrapped;
        // if wrapped is not empty, inspect its first element to figure out its elements' type
        this.origClazz = (Class<T>) wrapped.iterator().next().getClass();
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
        if (other.cardinality() == 0L) return this;
        if (this.cardinality() == 0L) return other;
        // if A ⊃ B, return A
        if (StreamSupport.stream(other.spliterator(), true).allMatch(this::contains) &&
                StreamSupport.stream(this.spliterator(), true).anyMatch(e -> !other.contains(e))) return this;
        // if B ⊃ A, return B
        if (StreamSupport.stream(this.spliterator(), true).allMatch(other::contains) &&
                StreamSupport.stream(other.spliterator(), true).anyMatch(e -> !this.contains(e))) return other;
        return other.union(this);
    }

    @Override
    public Set<R> intersection(Set<R> other) {
        if (other.cardinality() == 0L || this.cardinality() == 0L) return EmptySet.getInstance();
        if (StreamSupport.stream(other.spliterator(), true).allMatch(this::contains)) return this;
        if (StreamSupport.stream(this.spliterator(), true).allMatch(other::contains)) return other;
        return other.intersection(this);
    }

    @Override
    public Set<R> difference(Set<R> other) {
        if (other.cardinality() == 0L) return this;
        if (this.cardinality() == 0L) return EmptySet.getInstance();
        if (StreamSupport.stream(other.spliterator(), true).allMatch(this::contains) &&
                StreamSupport.stream(this.spliterator(), true).allMatch(other::contains)) return EmptySet.getInstance();
        if (this.cardinality() > 0L) {
            NumericSet difference = new NumericSet();
            StreamSupport.stream(this.spliterator(), true).dropWhile(other::contains).forEach(difference::append);
            try {
                return difference.coerceTo(clazz);
            } catch (CoercionException e) {
                Logger.getLogger(CoercionWrapperSet.class.getName()).log(Level.SEVERE,
                        "Result from set difference {} could not be coerced to {}.",
                        new Object[] {difference, clazz.getTypeName()});
                throw new ArithmeticException("Problem computing set difference: " + e.getMessage());
            }
        }
        throw new UnsupportedOperationException("Case not implemented yet");
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
