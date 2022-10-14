package tungsten.types;/*
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

/**
 * Generalization of a {@link Set} which allows duplicate elements.
 * Note that the {@link #cardinality() } for a multiset includes
 * all of the duplicates of given elements in the count.
 *
 * @author Robert Poole <Tarquin.AZ@gmail.com>
 * @param <T> the type of elements belonging to this multiset
 */
public interface Multiset<T> extends Set<T> {
    /**
     * Returns the number of times that a given element occurs in this
     * multiset.
     * @param element the element to search for
     * @return a non-negative long integer value
     */
    public long multiplicity(T element);
    /**
     * Return a representation of this multiset as a {@link Set}.  The
     * resulting set has no duplicates.
     * @return the set equivalent to this multiset
     */
    public Set<T> asSet();
}
