package tungsten.types.set.impl;

import tungsten.types.Set;
import tungsten.types.util.CombiningIterator;

import java.util.Iterator;
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
 *           contributing sets' elements
 */
public class UnionSet<T extends Comparable<? super T>> implements Set<T> {
    private final Set<T> set1, set2;

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
        throw new UnsupportedOperationException("This aggregate set is immutable");
    }

    @Override
    public void remove(T element) {
        throw new UnsupportedOperationException("This aggregate set is immutable");
    }

    @Override
    public Set<T> union(Set<T> other) {
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
            final TreeSet<T> result = new TreeSet<>();
            StreamSupport.stream(other.spliterator(), true).filter(this::contains).forEach(result::add);
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
                                "Attempt to remove element {0} from set {1}.",
                                new Object[] {element, result});
                    }
                }

                @Override
                public Set<T> union(Set<T> other2) {
                    return new UnionSet<>(this, other2);
                }

                @Override
                public Set<T> intersection(Set<T> other2) {
                    if (other2.cardinality() == 0L) return EmptySet.getInstance();
                    if (result.stream().noneMatch(other2::contains)) return EmptySet.getInstance();
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
}
