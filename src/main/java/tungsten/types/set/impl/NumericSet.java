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
package tungsten.types.set.impl;

import tungsten.types.Numeric;
import tungsten.types.Set;
import tungsten.types.exceptions.CoercionException;
import tungsten.types.numerics.ComplexType;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Implementation of {@link Set} for {@link Numeric} values.  If created using
 * the no-args constructor, this {@link Set} will attempt to preserve ordering
 * equivalent to the insertion order.  (This is useful if building a set of
 * {@link ComplexType} values, where insertion order may be critical but the
 * values themselves have no natural ordering.)  If the copy constructor is
 * used, there are no ordering guarantees.
 *
 * @author Robert Poole <a href="mailto:Tarquin.AZ@gmail.com">Tarquin.AZ@gmail.com</a>
 */
public class NumericSet implements Set<Numeric> {
    private final java.util.Set<Numeric> internal;

    /**
     * A standard no-args constructor.
     * The resulting {@code NumericSet} is empty but appendable.
     */
    public NumericSet() {
        internal = new LinkedHashSet<>();
    }

    /**
     * A constructor which takes any collection of {@code Numeric}
     * objects.
     * @param c the collection to ingest
     */
    public NumericSet(Collection<? extends Numeric> c) {
        if (c instanceof java.util.Set) {
            // no need for extra object copying in this case
            internal = (java.util.Set<Numeric>) c;
        } else {
            internal = new HashSet<>();
            internal.addAll(c);
        }
    }

    @Override
    public long cardinality() {
        return internal.size();
    }

    @Override
    public boolean countable() {
        return true;
    }

    @Override
    public boolean contains(Numeric element) {
        return internal.contains(element);
    }

    @Override
    public void append(Numeric element) {
        if (!internal.add(element)) {
            Logger.getLogger(NumericSet.class.getName()).log(Level.FINER,
                    "Attempted to append {0}, but set already contains this value.", element);
        }
    }

    @Override
    public void remove(Numeric element) {
        if (!internal.remove(element)) {
            Logger.getLogger(NumericSet.class.getName()).log(Level.FINER,
                    "Attempted to remove {0}, but set does not contain this value.", element);
        }
    }

    @Override
    public Set<Numeric> union(Set<Numeric> other) {
        if (other.cardinality() == -1L) {
            return other.union(this);
        } else if (other.cardinality() == 0L) {
            return this;
        }
        HashSet<Numeric> union = new HashSet<>(internal);
        
        Iterator<Numeric> iter = other.iterator();
        while (iter.hasNext()) {
            union.add(iter.next());
        }
        
        return new NumericSet(union);
    }

    @Override
    public Set<Numeric> intersection(Set<Numeric> other) {
        if (other.cardinality() == 0L) return EmptySet.getInstance();
        HashSet<Numeric> intersec = new HashSet<>();
        
        for (Numeric element : internal) {
            // only store values that exist in this and the other
            if (other.contains(element)) {
                intersec.add(element);
            }
        }
        if (intersec.isEmpty()) return EmptySet.getInstance();
        
        return new NumericSet(intersec);
    }

    @Override
    public Set<Numeric> difference(Set<Numeric> other) {
        HashSet<Numeric> diff = new HashSet<>(internal); // create a copy
        diff.removeIf(other::contains);
        if (diff.isEmpty()) return EmptySet.getInstance();
        
        return new NumericSet(diff);
    }
    
    /**
     * Coerce the elements of the parent set to type {@code T} and insert
     * them into a {@link Set<T>}, which is returned to the caller.
     * If the elements of the coerced type have a natural ordering, the
     * resulting set will be sorted according to that ordering, and the
     * {@link Set<T>#iterator() } will return elements in that order.
     * Otherwise, the returned set will attempt to preserve the insertion
     * order of the parent set.
     * @param <T> the desired subtype of {@link Numeric}
     * @param clazz the {@link Class} representing the desired subtype
     * @return an unmodifiable {@link Set} representing the elements of the parent set
     * @throws CoercionException if any elements in the parent set cannot be coerced to {@code T}
     */
    public <T extends Numeric> Set<T> coerceTo(Class<T> clazz) throws CoercionException {
        final java.util.Set<T> innerSet = Comparable.class.isAssignableFrom(clazz) ? new TreeSet<>() : new LinkedHashSet<>();
        for (Numeric element : internal) {
            if (!element.isCoercibleTo(clazz)) {
                throw new CoercionException("Element of NumericSet cannot be coerced to target type", element.getClass(), clazz);
            }
            innerSet.add((T) element.coerceTo(clazz));
        }
        
        return new Set<>() {
            private final java.util.Set<T> elements = Collections.unmodifiableSet(innerSet);

            @Override
            public long cardinality() {
                return elements.size();
            }

            @Override
            public boolean countable() {
                return true;
            }

            @Override
            public boolean contains(T element) {
                return elements.contains(element);
            }

            @Override
            public void append(T element) {
                throw new UnsupportedOperationException("Cannot modify this view");
            }

            @Override
            public void remove(T element) {
                throw new UnsupportedOperationException("Cannot modify this view");
            }

            @Override
            public Set<T> union(Set<T> other) {
                if (other.countable() && other.cardinality() > 0L) {
                    LinkedHashSet<T> temp = new LinkedHashSet<>(elements);
                    other.forEach(temp::add);
                    try {
                        return new NumericSet(temp).coerceTo(clazz);
                    } catch (CoercionException e) {
                        throw new IllegalStateException("While computing union", e);
                    }
                }
                if (Comparable.class.isAssignableFrom(clazz)) {
                    try {
                        Constructor<UnionSet> constructor = UnionSet.class.getConstructor(Set.class, Set.class);
                        return constructor.newInstance(this, other);
                    } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
                        Logger.getLogger(NumericSet.class.getName()).log(Level.SEVERE,
                                "While dynamically instantiating UnionSet", e);
                        throw new IllegalStateException("Fatal failure (should not have gotten here)", e);
                    } catch (InstantiationException e) {
                        throw new IllegalStateException("Unable to dynamically instantiate UnionSet", e);
                    }
                }
                return other.union(this);
            }

            @Override
            public Set<T> intersection(Set<T> other) {
                java.util.Set<T> temp = elements.stream().filter(other::contains).collect(Collectors.toSet());
                try {
                    return new NumericSet(temp).coerceTo(clazz);
                } catch (CoercionException e) {
                    throw new IllegalStateException("While computing intersection", e);
                }
            }

            @Override
            public Set<T> difference(Set<T> other) {
                LinkedHashSet<T> temp = new LinkedHashSet<>(elements);
                temp.removeIf(other::contains);
                try {
                    return new NumericSet(temp).coerceTo(clazz);
                } catch (CoercionException e) {
                    throw new IllegalStateException("While computing difference", e);
                }
            }

            @Override
            public Iterator<T> iterator() {
                return elements.iterator();
            }

            @Override
            public Spliterator<T> spliterator() {
                return elements.spliterator();
            }

            @Override
            public boolean isOfType(Class<?> test) {
                return clazz.isAssignableFrom(test);
            }

            @Override
            public int hashCode() {
                return Objects.hash(elements, clazz);
            }

            @Override
            public boolean equals(Object obj) {
                if (obj instanceof Set) {
                    if (!((Set<?>) obj).isOfType(clazz)) return false;
                    Set<T> that = (Set<T>) obj;
                    if (that.countable() && that.cardinality() == this.cardinality()) {
                        return elements.parallelStream().allMatch(that::contains);
                    }
                }
                return false;
            }

            @Override
            public String toString() {
                // U+202F is a narrow no-break space
                String suffix = "}\u202F[" + clazz.getSimpleName() + "]";
                return elements.stream().map(T::toString).collect(Collectors.joining(",\u2009", "{", suffix));
            }
        };
    } 
    
    /**
     * Since ordering in sets isn't guaranteed (especially this implementation),
     * we really only need to expose a parallel {@link Stream}.
     * @return a parallel stream of this set's elements
     */
    public Stream<Numeric> parallelStream() {
        return internal.parallelStream();
    }

    @Override
    public Iterator<Numeric> iterator() {
        return internal.iterator();
    }

    @Override
    public Spliterator<Numeric> spliterator() {
        return internal.spliterator();
    }

    @Override
    public boolean isOfType(Class<?> clazz) {
        return Numeric.class.isAssignableFrom(clazz);
    }

    @Override
    public int hashCode() {
        return 7 * internal.hashCode() - 3;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof Set) {
            if (!((Set<?>) obj).isOfType(Numeric.class)) return false;
            Set<Numeric> that = (Set<Numeric>) obj;
            if (that.countable() && that.cardinality() == this.cardinality()) {
                return internal.parallelStream().allMatch(that::contains);
            }
        }
        return false;
    }

    @Override
    public String toString() {
        return internal.stream().map(Numeric::toString).collect(Collectors.joining(",\u2009", "{", "}"));
    }
}
