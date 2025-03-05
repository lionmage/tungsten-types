/*
 * The MIT License
 *
 * Copyright © 2025 Robert Poole <Tarquin.AZ@gmail.com>.
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
 *
 */

package tungsten.types.set.impl;

import tungsten.types.Set;
import tungsten.types.util.CombiningIterator;

import java.util.Iterator;
import java.util.Objects;
import java.util.Spliterator;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * An implementation of {@link Set} that combines two other sets
 * in a union.  Note that elements of this set must implement {@link Comparable}.
 *
 * @param <T> the type of the elements of this set, as well as the type of
 *           the contributing sets' elements
 * @author Robert Poole, <a href="mailto:tarquin@alum.mit.edu">MIT alumni e-mail</a> or
 *   <a href="mailto:Tarquin.AZ@gmail.com">Gmail</a>
 * @since 0.6
 */
public class UnionSet<T extends Comparable<? super T>> implements Set<T> {
    private static final String IMMUTABLE_SET = "This aggregate set is immutable";
    private final Set<T> set1, set2;

    /**
     * Construct a set that is the union of the two given sets.
     * @param set1 the first set in the union
     * @param set2 the second set in the union
     */
    public UnionSet(Set<T> set1, Set<T> set2) {
        if (set1 == null || set2 == null) {
            throw new IllegalArgumentException("Set arguments must be non-null");
        }
        this.set1 = set1;
        this.set2 = set2;
    }

    @Override
    public long cardinality() {
        if (set1.cardinality() == -1L || set2.cardinality() == -1L) {
            return -1L;
        }
        if (set1.cardinality() == 0L && set2.cardinality() == 0L) {
            return 0L;
        }
        return StreamSupport.stream(this.spliterator(), false).count();
    }

    @Override
    public boolean countable() {
        return set1.countable() && set2.countable();
    }

    @Override
    public boolean contains(T element) {
        return set1.contains(element) || set2.contains(element);
    }

    @Override
    public void append(T element) {
        throw new UnsupportedOperationException(IMMUTABLE_SET);
    }

    @Override
    public void remove(T element) {
        throw new UnsupportedOperationException(IMMUTABLE_SET);
    }

    @Override
    public Set<T> union(Set<T> other) {
        if (other.cardinality() == 0L) return this;
        return new UnionSet<>(this, other);
    }

    /**
     * Compute the intersection of this set with the given set.
     * <ul>
     * <li>If {@code other} is a finite, non-empty set, then the result is
     * a finite {@link Set} that is mutable.</li>
     * <li>If {@code other} is empty, the result is the {@link EmptySet}.</li>
     * <li>Otherwise, the result is an infinite set that is immutable.</li>
     * </ul>
     *
     * @param other the {@link Set} with which to compute the intersection
     * @return a {@link Set} representing the intersection of this set with {@code other}
     */
    @Override
    public Set<T> intersection(Set<T> other) {
        if (other.cardinality() == 0L) return EmptySet.getInstance();
        if (other.countable() && other.cardinality() > 0L) {
            final TreeSet<T> result = StreamSupport.stream(other.spliterator(), true).filter(this::contains)
                    .collect(Collectors.toCollection(TreeSet::new));
            return new Set<>() {
                @Override
                public long cardinality() {
                    return result.size();
                }

                @Override
                public boolean countable() {
                    return true;
                }

                @Override
                public boolean contains(T element) {
                    return result.contains(element);
                }

                @Override
                public void append(T element) {
                    result.add(element);
                }

                @Override
                public void remove(T element) {
                    boolean success = result.remove(element);
                    if (!success) {
                        Logger.getLogger(UnionSet.class.getName()).log(Level.WARNING,
                                "Attempt to remove element {0} from set {1} failed.",
                                new Object[] {element, result});
                    }
                }

                @Override
                public Set<T> union(Set<T> other2) {
                    if (other.equals(other2) ||
                            (other.cardinality() == other2.cardinality() && other.difference(other2).cardinality() == 0L)) {
                        return UnionSet.this;
                    }
                    return new UnionSet<>(this, other2);
                }

                @Override
                public Set<T> intersection(Set<T> other2) {
                    if (other2.cardinality() == 0L) return EmptySet.getInstance();
                    if (result.stream().noneMatch(other2::contains)) return EmptySet.getInstance();
                    if (result.stream().allMatch(other2::contains)) return this;
                    if (other2.cardinality() > 0L && other2.cardinality() <= result.size() &&
                            StreamSupport.stream(other2.spliterator(), true).allMatch(result::contains)) return other2;
                    return other2.intersection(this);
                }

                @Override
                public Set<T> difference(Set<T> other2) {
                    // (A ∩ B) - C = (A - C) ∩ (B - C)
                    return UnionSet.this.difference(other2).intersection(other.difference(other2));
                }

                @Override
                public Iterator<T> iterator() {
                    return result.iterator();
                }

                @Override
                public Spliterator<T> spliterator() {
                    return result.spliterator();
                }

                @Override
                public String toString() {
                    return result.stream().map(Object::toString).collect(Collectors.joining(", ", "{", "}"));
                }
            };
        }
        // default strategy is to construct a new UnionSet that represents the intersection
        // (A ∪ B) ∩ C = (A ∩ C) ∪ (B ∩ C)
        return new UnionSet<>(set1.intersection(other), set2.intersection(other));
    }

    @Override
    public Set<T> difference(Set<T> other) {
        // (A ∪ B) - C = (A - C) ∪ (B - C)
        return new UnionSet<>(set1.difference(other), set2.difference(other));
    }

    @Override
    public Iterator<T> iterator() {
        return new CombiningIterator<>(set1.iterator(), set2.iterator());
    }

    private static final int TOSTRING_LIMIT = 15;

    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder();
        buf.append("UnionSet: {");
        Iterator<T> iter = this.iterator();
        int idx = 0;
        while (iter.hasNext()) {
            buf.append(iter.next()).append(", ");
            if (++idx > TOSTRING_LIMIT) break;  // only include the first TOSTRING_LIMIT elements if the set is bigger
        }
        if (idx < TOSTRING_LIMIT) {
            buf.setLength(buf.length() - 2); // erase the final comma and space
        } else {
            buf.append('\u2026'); // append an ellipsis to indicate that there are more elements
        }
        buf.append('}');
        return buf.toString();
    }

    @Override
    public int hashCode() {
        return Objects.hash(set1, set2);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof Set) {
            if (obj instanceof UnionSet) {
                UnionSet<?> that = (UnionSet<?>) obj;
                return set1.equals(that.set1) && set2.equals(that.set2);
            }
            Set<T> other = (Set<T>) obj;
            if (other.countable() != this.countable()) return false;
            if (other.cardinality() == 0L && this.cardinality() == 0L) return true;
            if (other.cardinality() > 0L && this.cardinality() == other.cardinality()) {
                return StreamSupport.stream(other.spliterator(), true).allMatch(this::contains);
            }
        }
        return false;
    }

    @Override
    public boolean isOfType(Class<?> clazz) {
        // in case set1 doesn't report an accurate type
        return set1.isOfType(clazz) || set2.isOfType(clazz);
    }
}
