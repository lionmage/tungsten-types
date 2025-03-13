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
import tungsten.types.util.ClassTools;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.StreamSupport;

/**
 * For when you have a {@link Set} of one {@link Numeric} subtype, but you need
 * the elements to be of a different subtype.
 * @param <T> the type of the set being wrapped
 * @param <R> the type of this view of the wrapped set
 * @author Robert Poole, <a href="mailto:tarquin@alum.mit.edu">MIT alumni e-mail</a> or
 *   <a href="mailto:Tarquin.AZ@gmail.com">Gmail</a>
 */
public class CoercionWrapperSet<T extends Numeric, R extends Numeric> implements Set<R> {
    private static final String APPEND_UNSUPPORTED = "Append is not supported for this view";
    private static final String REMOVE_UNSUPPORTED = "Remove is not supported for this view";
    private final Class<R> clazz;
    private final Class<T> origClazz;
    private final Set<T> original;

    /**
     * Given a {@code Set} to be wrapped and the {@code Numeric} subtypes of
     * the source and target elements, construct a {@code CoercionWrapperSet}
     * which presents the elements of the source set as those of the target type.
     * @param wrapped    the source {@code Set} to be wrapped
     * @param originType the type of the elements in {@code wrapped}
     * @param targetType the desired type
     */
    public CoercionWrapperSet(Set<T> wrapped, Class<T> originType, Class<R> targetType) {
        this.clazz = targetType;
        this.original = wrapped;
        this.origClazz = originType;
    }

    /**
     * Constructor which infers the type of the elements of the original set from
     * the set itself. This implies that there is at least one element in the original
     * set, and that the original set returns {@link Set#countable()} {@code == true}.
     * If neither condition is true, consider using {@link #CoercionWrapperSet(Set, Class, Class)}
     * instead.
     * @param wrapped    the {@link Set} to be wrapped
     * @param targetType the {@link Numeric} subtype for the resulting {@link Set}
     */
    public CoercionWrapperSet(Set<T> wrapped, Class<R> targetType) {
        this.clazz = targetType;
        this.original = wrapped;
        // if wrapped is not empty, inspect its first element to figure out its elements' type
        this.origClazz = (Class<T>) ClassTools.getInterfaceTypeFor(wrapped.iterator().next().getClass());
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
            Logger.getLogger(CoercionWrapperSet.class.getName()).log(Level.WARNING,
                    "While testing for containment, provided key {0} could not be coerced to element type {1} of wrapped set.",
                    new Object[] { element, origClazz.getTypeName() });
            // if we can't coerce to the type of the wrapped set, then the key must not be contained by it
            return false;
        }
    }

    @Override
    public void append(R element) {
        throw new UnsupportedOperationException(APPEND_UNSUPPORTED);
    }

    @Override
    public void remove(R element) {
        throw new UnsupportedOperationException(REMOVE_UNSUPPORTED);
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
        // can't really put a UnionSet here unless we use reflection to instantiate one
        // we do that in NumericSet, and it's a pain... just ugly, to be avoided
        return other.union(this);
    }

    @Override
    public Set<R> intersection(Set<R> other) {
        if (other.cardinality() == 0L || this.cardinality() == 0L) return EmptySet.getInstance();
        if (other.cardinality() > 0L && StreamSupport.stream(other.spliterator(), true).allMatch(this::contains)) return other;
        if (this.cardinality() > 0L && StreamSupport.stream(this.spliterator(), true).allMatch(other::contains)) return this;
        if (this.cardinality() > 0L) {
            return finiteCountableIntersection(this, other);
        } else if (other.cardinality() > 0L) {
            return finiteCountableIntersection(other, this);
        }
        return other.intersection(this);
    }

    private Set<R> finiteCountableIntersection(Set<R> left, Set<R> right) {
        NumericSet intersection = new NumericSet();
        StreamSupport.stream(left.spliterator(), true)
                .filter(right::contains).forEach(intersection::append);
        if (intersection.cardinality() == 0L) return EmptySet.getInstance();
        try {
            return intersection.coerceTo(clazz);
        } catch (CoercionException e) {
            Logger.getLogger(CoercionWrapperSet.class.getName()).log(Level.SEVERE,
                    "Result from set intersection {0} could not be coerced to {1}.",
                    new Object[] {intersection, clazz.getTypeName()});
            throw new ArithmeticException("Problem computing set intersection: " + e.getMessage());
        }
    }

    @Override
    public Set<R> difference(Set<R> other) {
        if (other.cardinality() == 0L) return this;
        if (this.cardinality() == 0L) return EmptySet.getInstance();
        if (this.cardinality() > 0L) {
            NumericSet difference = new NumericSet();
            StreamSupport.stream(this.spliterator(), true)
                    .filter(element -> !other.contains(element)).forEach(difference::append);
            if (difference.cardinality() == 0L) return EmptySet.getInstance();
            try {
                return difference.coerceTo(clazz);
            } catch (CoercionException e) {
                Logger.getLogger(CoercionWrapperSet.class.getName()).log(Level.SEVERE,
                        "Result from set difference {0} could not be coerced to {1}.",
                        new Object[] {difference, clazz.getTypeName()});
                throw new ArithmeticException("Problem computing set difference: " + e.getMessage());
            }
        }
        if (other.equals(this)) return EmptySet.getInstance();
        // What we have left if we get to this point is an infinite set
        // which may or may not be countable.  This requires
        // a specialized class to handle.
        return new DiffSet<>(this, other);
    }

    @Override
    public Iterator<R> iterator() {
        final Iterator<T> origIter = original.iterator();

        return new Iterator<>() {
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        // the following code allows this set to be equal to any set identical to the
        // wrapped (original) set
        if (o instanceof Set && original.equals(o)) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CoercionWrapperSet<?, ?> that = (CoercionWrapperSet<?, ?>) o;
        final Class<? extends Numeric> fixedOrig = (Class<? extends Numeric>) ClassTools.getInterfaceTypeFor(origClazz);
        final Class<? extends Numeric> fixedOther = (Class<? extends Numeric>) ClassTools.getInterfaceTypeFor(that.origClazz);
        return clazz == that.clazz && fixedOrig == fixedOther && original.equals(that.original);
    }

    @Override
    public int hashCode() {
        return Objects.hash(clazz, origClazz, original);
    }
}
