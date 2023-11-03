package tungsten.types;
/*
 * The MIT License
  *
 * Copyright © 2018 Robert Poole <Tarquin.AZ@gmail.com>.
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

import tungsten.types.set.impl.EmptySet;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.StreamSupport;

/**
 * Represents a set of objects, e.g. numeric or symbolic.
 * Note: putting some kind of bounds on {@link T} should be considered.
 *
 * @author Robert Poole <a href="mailto:Tarquin.AZ@gmail.com">Tarquin.AZ@gmail.com</a>
 * @param <T> the type of elements in this set
 */
public interface Set<T> extends Iterable<T> {
    /**
     * Returns the cardinality (size) of this set.  If this is an
     * infinite set, returns -1.
     * @return the cardinality of a finite set, or -1 for an infinite set
     */
    long cardinality();

    /**
     * Returns true if this set is countable &mdash; i.e., whether
     * it consists of discrete elements. Note that a set can be
     * countable yet still be infinite.
     *
     * @return true if this set is countable
     */
    boolean countable();

    /**
     * Determines whether this set contains the given element.
     *
     * @param element the element to test for
     * @return true if this set contains the given element
     */
    boolean contains(T element);

    /**
     * Optional operation.  Append the given element to this set.
     * If the element is already a member of this set, no update occurs.
     *
     * @param element the element to append to this set
     * @throws UnsupportedOperationException if this set does not support insertion
     */
    void append(T element);

    /**
     * Optional operation. Remove the given element from this set.
     * If the element was not a member of this set, no update occurs.
     *
     * @param element the element to remove
     * @throws UnsupportedOperationException if this set does not support removal
     * @throws NoSuchElementException if this set does not contain {@code element}
     */
    void remove(T element);

    /**
     * Compute {@code this} &cup; {@code other}. This operation
     * <strong>must not</strong> modify this set, nor the operand.
     * The result may be a newly created {@link Set} instance, a reference
     * to {@code this} or {@code other}, or an immutable singleton
     * object such as {@link EmptySet}.  As such, clients of this method
     * should never make assumptions about the mutability or general
     * implementation of the result, and should treat the result as
     * read-only.
     *
     * @param other the other set with which to compute the union
     * @return the union of this set and {@code other}
     */
    Set<T> union(Set<T> other);

    /**
     * Compute {@code this} &cap; {@code other}. This operation
     * <strong>must not</strong> modify this set, nor the operand.
     * The result may be a newly created {@link Set} instance, a reference
     * to {@code this} or {@code other}, or an immutable singleton
     * object such as {@link EmptySet}.  As such, clients of this method
     * should never make assumptions about the mutability or general
     * implementation of the result, and should treat the result as
     * read-only.
     *
     * @param other the other set with which to compute the intersection
     * @return the intersection of this set and {@code other}
     */
    Set<T> intersection(Set<T> other);

    /**
     * Compute {@code this} &minus; {@code other}, also sometimes denoted
     * {@code this}&nbsp;&#x2216;&nbsp;{@code other}. The result is the set of all
     * elements from this set that do not exist in {@code other}.  This
     * operation <strong>must not</strong> modify this set, nor the operand.
     * No guarantees are made as to the actual implementation of the result,
     * and in general it should be considered read-only.
     *
     * @param other the other set with which to compute the difference
     * @return the difference between this set and {@code other}
     */
    Set<T> difference(Set<T> other);

    /**
     * Variable-arity method to generate a {@link Set} from the argument list
     * (or supplied array).  The supplied elements <strong>must not</strong> contain any duplicates.
     *
     * @param elements an array of elements for inclusion in this set
     * @return a representation of the supplied elements as a finite-size {@link Set}
     * @throws IllegalArgumentException if {@code elements} contains any duplicate items
     * @param <T> the type of the elements
     */
    @SafeVarargs
    static <T> Set<T> of(T... elements) {
        if (elements.length == 0) return EmptySet.getInstance();
        if (hasDuplicates(elements)) throw new IllegalArgumentException("Cannot construct a Set with duplicate elements.");
        final Class<T> elementType = (Class<T>) elements.getClass().getComponentType();

        return new Set<>() {
            @Override
            public long cardinality() {
                return elements.length;
            }

            @Override
            public boolean countable() {
                return true;
            }

            @Override
            public boolean contains(T element) {
                return Arrays.stream(elements).anyMatch(element::equals);
            }

            @Override
            public void append(T element) {
                throw new UnsupportedOperationException("Set is immutable");
            }

            @Override
            public void remove(T element) {
                throw new UnsupportedOperationException("Set is immutable");
            }

            @Override
            public Set<T> union(Set<T> other) {
                if (other.cardinality() == 0L) return this;
                if (other.countable() && other.cardinality() > 0L) {
                    HashSet<T> union = new HashSet<>();
                    Arrays.stream(elements).forEach(union::add);
                    StreamSupport.stream(other.spliterator(), false).forEach(union::add);
                    return Set.of(union.toArray(this::createNewArray));
                }
                // right now, I don't see a better way to do this without creating yet another anonymous class
                return other.union(this);
            }

            @Override
            public Set<T> intersection(Set<T> other) {
                if (other.cardinality() == 0L) return EmptySet.getInstance();
                T[] intersection = Arrays.stream(elements).filter(other::contains).toArray(this::createNewArray);
                return Set.of(intersection);
            }

            @Override
            public Set<T> difference(Set<T> other) {
                if (other.cardinality() == 0L) return this;
                T[] difference = Arrays.stream(elements).dropWhile(other::contains).toArray(this::createNewArray);
                return Set.of(difference);
            }

            @Override
            public Iterator<T> iterator() {
                return Arrays.stream(elements).iterator();
            }

            @Override
            public Spliterator<T> spliterator() {
                return Arrays.spliterator(elements);
            }

            private T[] createNewArray(int size) {
                final Class<T[]> elementArrayType = (Class<T[]>) elements.getClass();
                try {
                    Constructor<T[]> constructor = elementArrayType.getConstructor(int.class);
                    return constructor.newInstance(size);
                } catch (NoSuchMethodException e) {
                    Logger.getLogger(Set.class.getName()).log(Level.SEVERE,
                            "Cannot dynamically obtain array constructor for type {0}", elementType);
                    throw new IllegalStateException(e);
                } catch (InstantiationException | InvocationTargetException | IllegalAccessException e) {
                    Logger.getLogger(Set.class.getName()).log(Level.SEVERE,
                            "While dynamically instantiating an array of " + elementType.getTypeName(), e);
                    throw new IllegalStateException(e);
                }
            }

            @Override
            public int hashCode() {
                return Objects.hash(elementType, Arrays.hashCode(elements));
            }

            @Override
            public boolean equals(Object obj) {
                if (obj instanceof Set) {
                    Set<?> that = (Set<?>) obj;
                    if (that.countable() && this.cardinality() == that.cardinality()) {
                        return StreamSupport.stream(that.spliterator(), true).map(elementType::cast).allMatch(this::contains);
                    }
                }
                return false;
            }
        };
    }

    @SafeVarargs
    private static <T> boolean hasDuplicates(T... elements) {
        HashSet<T> cache = new HashSet<>();
        return !Arrays.stream(elements).allMatch(cache::add);
    }
}
