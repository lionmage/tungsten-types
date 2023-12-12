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

import tungsten.types.Set;

import java.util.Collections;
import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * The empty set, &empty;.
 *
 * @author Robert Poole <a href="mailto:Tarquin.AZ+Tungsten@gmail.com">Tarquin.AZ@gmail.com</a>
 */
public class EmptySet implements Set<Object> {
    private EmptySet() {}
    
    private static final EmptySet instance = new EmptySet();

    @SuppressWarnings("unchecked")
    public static <T> Set<T> getInstance() { return (Set<T>) instance; }

    @Override
    public long cardinality() {
        return 0L;
    }

    @Override
    public boolean countable() {
        return true;
    }

    @Override
    public boolean contains(Object element) {
        return false;
    }

    @Override
    public void append(Object element) {
        throw new UnsupportedOperationException("Cannot append to singleton empty set");
    }

    @Override
    public void remove(Object element) {
        throw new NoSuchElementException("EmptySet has no elements to remove");
    }

    @Override
    public Set<Object> union(Set<Object> other) {
        return other;
    }

    @Override
    public Set<Object> intersection(Set<Object> other) {
        return this;
    }

    @Override
    public Set<Object> difference(Set<Object> other) {
        return this;
    }

    @Override
    public Iterator<Object> iterator() {
        return Collections.emptyIterator();
    }

    @Override
    public int hashCode() {
        return 0;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof Set) {
            Set<?> that = (Set<?>) obj;
            return that.cardinality() == 0L;
        }
        return false;
    }

    @Override
    public String toString() {
        return "\u2205";
    }
}
