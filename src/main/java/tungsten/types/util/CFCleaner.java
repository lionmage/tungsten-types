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

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * An iterator that takes in a sequence of {@code Long} values from a
 * source (e.g., a {@link tungsten.types.numerics.impl.ContinuedFraction} or
 * a {@link GosperTermIterator}) and sanitizes them to conform to certain
 * rules about the terms of a simple continued fraction:
 * <ul>
 *     <li>Only the first term a<sub>0</sub> may contain a 0 or negative value</li>
 *     <li>Zeroes should be annealed by the standard rules (by dropping, or dropping and summing, terms)</li>
 *     <li>Negative values can be cured through a simple transformation and insertion of a term</li>
 *     <li>If the last term is a 1, it can be folded into the previous term by addition</li>
 * </ul>
 * This iterator wraps the source iterator and by necessity does look-ahead as well
 * as look-behind (to correct any previously consumed terms).  The reference to the source
 * iterator is released if there are no more terms that can be read from source.
 * @see <a href="https://r-knott.surrey.ac.uk/fibonacci/CFintro.html#section14.2.2">Section 14.2.2</a>
 *   of <em>An Introduction to Continued Fractions</em>
 * @see <a href="https://medium.com/@omer.kasdarma/the-curious-world-of-simple-continued-fractions-part-i-3e4bba93db5f">this
 *   article</a> which articulates the basic rules for handling 0 terms
 * @author Robert Poole, <a href="mailto:tarquin@alum.mit.edu">MIT alumni e-mail</a> or
 *   <a href="mailto:Tarquin.AZ@gmail.com">Gmail</a>
 * @since 0.6
 */
public class CFCleaner implements Iterator<Long> {
    private Iterator<Long> source;
    private final Deque<Long> deque = new ArrayDeque<>(5);
    private long k = -1L;
    private boolean negateOnRead = false;

    /**
     * Instantiate this iterator using the given source.
     * @param source the source iterator to be consumed
     */
    public CFCleaner(Iterator<Long> source) {
        this.source = source;
    }

    @Override
    public boolean hasNext() {
        return (source != null && source.hasNext()) || !deque.isEmpty();
    }

    @Override
    public Long next() {
        if (source == null) {
            return deque.poll();
        }
        while (source.hasNext()) {
            Long peek = getSourceTerm();
            if (peek == null) break;
            if (peek == 0L && k > 0L) {
                int zeroCount = 1;
                // seek ahead until we find a non-zero value
                do {
                    peek = getSourceTerm();
                    if (peek == null) break;
                    if (peek == 0L) zeroCount++;
                } while (source.hasNext() && peek == 0L);
                // if peek is null, just break out of this outer loop
                if (peek == null) break;
                // even case, drop the zeroes
                if (zeroCount % 2 == 0) deque.addLast(peek);
                else {
                    // odd case, sum a and b where ...a, 0, ..., 0, b...
                    Long prev = deque.removeLast();
                    deque.addLast(prev + peek);
                }
            } else if (peek < 0L && k > 0L) {
                Long prev = deque.removeLast();
                deque.addAll(List.of(prev - 1L, 1L, -peek - 1L));
                negateOnRead = !negateOnRead;
            } else if (peek == 1L && !source.hasNext()) {
                // fold a trailing 1 into the last term
                Long prev = deque.removeLast();
                deque.addLast(prev + 1L);
            } else {
                deque.addLast(peek);
            }
            if (deque.size() > 2) break;
        }
        if (source != null && !source.hasNext()) {
            Logger.getLogger(CFCleaner.class.getName()).log(Level.FINE,
                    "Source exhausted after {0}th term read.", k);
            source = null;
        }
        return deque.poll();
    }

    private Long getSourceTerm() {
        if (source == null) return null;
        Long term = source.next();
        if (term != null) {
            k++;
            return negateOnRead ? -term : term;
        }
        return null;
    }

    @Override
    public void forEachRemaining(Consumer<? super Long> action) {
        if (source == null) {
            deque.forEach(action);
            deque.clear();  // clear all remaining elements from the queue after calling forEach() on them
        } else {
            Iterator.super.forEachRemaining(action);
        }
    }
}
