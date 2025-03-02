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

package tungsten.types.util;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * An implementation of {@link Iterator} that combines the output of two other
 * {@link Iterator}s.  The elements returned from the wrapped iterators must
 * be comparable, and it is assumed that these iterators return their elements
 * in sorted order (least to highest).  If the contributing iterators return
 * values in any other order, the behavior of this iterator is not guaranteed.
 *
 * @param <T> the type of the elements returned by this iterator
 * @author Robert Poole, <a href="mailto:tarquin@alum.mit.edu">MIT alumni e-mail</a> or
 *   <a href="mailto:Tarquin.AZ@gmail.com">Gmail</a>
 */
public class CombiningIterator <T extends Comparable<? super T>> implements Iterator<T> {
    private final Iterator<T> iter1;
    private final Iterator<T> iter2;
    private final TreeSet<T> cache = new TreeSet<>();

    /**
     * Construct a {@code CombiningIterator} from the two given {@code Iterator}s.
     * @param first  the first iterator to be combined
     * @param second the second iterator to be combined
     */
    public CombiningIterator(Iterator<T> first, Iterator<T> second) {
        this.iter1 = first;
        this.iter2 = second;
    }

    @Override
    public boolean hasNext() {
        return iter1.hasNext() || iter2.hasNext() || !cache.isEmpty();
    }

    @Override
    public T next() {
        if (iter1.hasNext() && iter2.hasNext()) {
            // choose which one gets obtained next
            T val1 = iter1.next();
            T val2 = iter2.next();
            if (cache.isEmpty()) {
                return findLowestAndCacheOther(val1, val2);
            }
            T top = cache.first();
            // if we got this far, we have something in the cache that is important
            if (top.compareTo(val1) < 0 && top.compareTo(val2) < 0) {
                cache.add(val1);
                cache.add(val2);
                cache.remove(top);
                return top;
            } else {
                return findLowestAndCacheOther(val1, val2);
            }
        } else if (iter1.hasNext()) {
            T val = iter1.next();
            return findLowestVsCache(val);
        } else if (iter2.hasNext()) {
            T val = iter2.next();
            return findLowestVsCache(val);
        } else if (!cache.isEmpty()) {
            return cache.pollFirst();
        }
        throw new NoSuchElementException("Parent iterators have both been exhausted");
    }

    private T findLowestVsCache(T val) {
        if (!cache.isEmpty()) {
            T top = cache.first();
            if (top.compareTo(val) < 0) {
                cache.add(val);
                cache.remove(top);
                return top;
            } else if (top.compareTo(val) == 0) {
                Logger.getLogger(CombiningIterator.class.getName()).log(Level.FINE,
                        "Encountered duplicate element {0} during iteration.", val);
                // remove duplicates
                cache.remove(top);
            }
        }
        return val;
    }

    private T findLowestAndCacheOther(T val1, T val2) {
        int comparison = val1.compareTo(val2);
        if (comparison < 0) {
            cache.add(val2);
            return val1;
        } else if (comparison > 0) {
            cache.add(val1);
            return val2;
        } else {
            Logger.getLogger(CombiningIterator.class.getName()).log(Level.FINE,
                    "Encountered duplicate element {0} during iteration.", val2);
            // currently we're dropping duplicates
            return val1;
        }
    }
}
