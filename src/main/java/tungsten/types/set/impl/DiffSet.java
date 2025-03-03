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

import java.util.Iterator;
import java.util.stream.StreamSupport;

/**
 * Given two sets A and B, compute the difference A&minus;B.
 * @param <T> the type of this set's elements
 */
public class DiffSet<T> implements Set<T> {
    private static final String IMMUTABLE_VIEW = "Difference set view is immutable";
    private final Set<T> left;
    private final Set<T> right;

    /**
     * Construct a set that is the difference between two given sets A&minus;B.
     * @param lhs the left-hand side of the difference, denoted A
     * @param rhs the right-hand side of the difference, denoted B
     */
    public DiffSet(Set<T> lhs, Set<T> rhs) {
        if (lhs == null || rhs == null) {
            throw new IllegalArgumentException("Set arguments must be non-null");
        }
        this.left = lhs;
        this.right = rhs;
    }

    @Override
    public long cardinality() {
        // we shouldn't have to check right.cardinality() here
        if (left.cardinality() == -1L) return -1L;
        return StreamSupport.stream(this.spliterator(), false).count();
    }

    @Override
    public boolean countable() {
        return left.countable();
    }

    @Override
    public boolean contains(T element) {
        return left.contains(element) && !right.contains(element);
    }

    @Override
    public void append(T element) {
        throw new UnsupportedOperationException(IMMUTABLE_VIEW);
    }

    @Override
    public void remove(T element) {
        throw new UnsupportedOperationException(IMMUTABLE_VIEW);
    }

    @Override
    public Set<T> union(Set<T> other) {
        if (right.equals(other)) return left;
        // (A - B) ∪ C = A - (B - C) if C ⊆ A
        if (left.intersection(other).equals(other)) {
            return left.difference(right.difference(other));
        }
        return other.union(this);
    }

    @Override
    public Set<T> intersection(Set<T> other) {
        if (right.equals(other)) return EmptySet.getInstance();
        if (other.cardinality() > 0L) {
            return other.difference(other.difference(this));
        }
        return this.difference(this.difference(other));
    }

    @Override
    public Set<T> difference(Set<T> other) {
        if (other.cardinality() == 0L || other.equals(right)) return this;
        return left.difference(other).difference(right.difference(other));
    }

    @Override
    public boolean isOfType(Class<?> clazz) {
        return left.isOfType(clazz);
    }

    @Override
    public Iterator<T> iterator() {
        return StreamSupport.stream(left.spliterator(), false)
                .dropWhile(right::contains).iterator();
    }
}
