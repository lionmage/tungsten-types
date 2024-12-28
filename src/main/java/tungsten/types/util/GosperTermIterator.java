/*
 * The MIT License
 *
 * Copyright © 2024 Robert Poole <Tarquin.AZ@gmail.com>.
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
import java.util.Objects;

/**
 * An {@code Iterator} intended to consume the terms
 * returned by two source iterators, performing various
 * arithmetic functions using <a href="https://perl.plover.com/classes/cftalk/INFO/gosper.html">Gosper's algorithm</a>
 * and returning terms of the result.
 * @author Robert Poole, <a href="mailto:tarquin@alum.mit.edu">MIT alumni e-mail</a>
 * @see <a href="https://github.com/themadcreator/gosper-java/blob/master/src/org/numerics/continuedfranctionlong/GosperLongTermIterator.java">a sample
 *   implementation of Gosper's algorithm</a>
 * @since 0.5
 */
public class GosperTermIterator implements Iterator<Long> {
    private static class StateVector {
        private final long a, b, c, d, e, f, g, h;

        private StateVector(long a, long b, long c, long d,
                            long e, long f, long g, long h) {
            this.a = a;
            this.b = b;
            this.c = c;
            this.d = d;
            this.e = e;
            this.f = f;
            this.g = g;
            this.h = h;
        }

        @Override
        public int hashCode() {
            return Objects.hash(a, b, c, d, e, f, g, h);
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof StateVector) {
                StateVector that = (StateVector) obj;
                return this.a == that.a &&
                        this.b == that.b &&
                        this.c == that.c &&
                        this.d == that.d &&
                        this.e == that.e &&
                        this.f == that.f &&
                        this.g == that.g &&
                        this.h == that.h;
            }
            return false;
        }

        @Override
        public String toString() {
            StringBuilder buf = new StringBuilder();
            buf.append('\u27E8')
                    .append(a).append(",\u2009")
                    .append(b).append(",\u2009")
                    .append(c).append(",\u2009")
                    .append(d).append(";\u2009") // segregate into groups of 4
                    .append(e).append(",\u2009")
                    .append(f).append(",\u2009")
                    .append(g).append(",\u2009")
                    .append(h).append(",\u2009")
                    .append('\u27E9');
            return buf.toString();
        }
    }

    private final Iterator<Long> x;
    private final Iterator<Long> y;
    private StateVector state;

    private GosperTermIterator(Iterator<Long> x, Iterator<Long> y, StateVector state) {
        this.x = x;
        this.y = y;
        this.state = state;
    }

    /**
     * Create an {@code Iterator} to perform the addition of two continued fractions.
     * @param x the iterator over the terms of the first operand
     * @param y the iterator over the terms of the second operand
     * @return the sum of {@code x} and {@code y} as an iterator over terms
     */
    public static Iterator<Long> add(Iterator<Long> x, Iterator<Long> y) {
        final StateVector s = new StateVector(
                0L, 1L, 1L, 0L,
                1L, 0L, 0L, 0L);
        return new GosperTermIterator(x, y, s);
    }

    /**
     * Create an {@code Iterator} to subtract two continued fractions.
     * @param x the iterator over the terms of the first operand
     * @param y the iterator over the terms of the second operand
     * @return the difference of {@code x} and {@code y} as an iterator over terms
     */
    public static Iterator<Long> subtract(Iterator<Long> x, Iterator<Long> y) {
        final StateVector s = new StateVector(
                0L, 1L, -1L, 0L,
                1L, 0L,  0L, 0L);
        return new GosperTermIterator(x, y, s);
    }

    /**
     * Create an {@code Iterator} to perform the multiplication of two continued fractions.
     * @param x the iterator over the terms of the first operand
     * @param y the iterator over the terms of the second operand
     * @return the product of {@code x} and {@code y} as an iterator over terms
     */
    public static Iterator<Long> multiply(Iterator<Long> x, Iterator<Long> y) {
        final StateVector s = new StateVector(
                0L, 0L, 0L, 1L,
                1L, 0L, 0L, 0L);
        return new GosperTermIterator(x, y, s);
    }

    /**
     * Create an {@code Iterator} to divide two continued fractions.
     * @param x the iterator over the terms of the first operand
     * @param y the iterator over the terms of the second operand
     * @return the quotient {@code x}&#x2215;{@code y} as an iterator over terms
     */
    public static Iterator<Long> divide(Iterator<Long> x, Iterator<Long> y) {
        final StateVector s = new StateVector(
                0L, 1L, 0L, 0L,
                0L, 0L, 1L, 0L);
        return new GosperTermIterator(x, y, s);
    }

    private static Long absDifference(Long a, Long b) {
        if (a == null) return b == null ? 0L : null;
        if (b == null) return null;
        return Math.abs(a - b);
    }

    private static Long divide(Long num, Long den) {
        if (den == 0L) return null;
        return num / den;
    }

    private static int compare(Long a, Long b) {
        if (a == null) return b == null ? 0 : 1;
        if (b == null) return -1;
        return a.compareTo(b);
    }

    @Override
    public boolean hasNext() {
        return !isDone();
    }

    @Override
    public Long next() {
        while (true) {
            if (isDone()) return null;

            final Long r = getAgreeingR();
            if (r != null) {
                outputStateR(r);
                return r;
            } else if (preferInputX()) {
                inputX();
            } else {
                inputY();
            }
        }
    }

    private boolean isDone() {
        // simplified, and without unnecessary division
        return state.e == 0L && state.f == 0L && state.g == 0L && state.h == 0L;
    }

    private void outputStateR(long r) {
        state = new StateVector(
                state.e, state.f, state.g, state.h,
                state.a - state.e * r, state.b - state.f * r, state.c - state.g * r, state.d - state.h * r);
    }

    private void inputX() {
        Long p;
        if (!x.hasNext() || (p = x.next()) == null) {
            state = new StateVector(
                    state.b, state.b, state.d, state.d,
                    state.f, state.f, state.h, state.h);
        } else {
            state = new StateVector(
                    state.b, state.a + state.b * p, state.d, state.c + state.d * p,
                    state.f, state.e + state.f * p, state.h, state.g + state.h * p);
        }
    }

    private void inputY() {
        Long q;
        if (!y.hasNext() || (q = y.next()) == null) {
            state = new StateVector(
                    state.c, state.d, state.c, state.d,
                    state.g, state.h, state.g, state.h);
        } else {
            state = new StateVector(
                    state.c, state.d, state.a + state.c * q, state.b + state.d * q,
                    state.g, state.h, state.e + state.g * q, state.f + state.h * q);
        }
    }

    private Long getAgreeingR() {
        final Long n0 = divide(state.a, state.e);
        final Long n1 = divide(state.b, state.f);
        final Long n2 = divide(state.c, state.g);
        final Long n3 = divide(state.d, state.h);
        if (compare(n0, n1) != 0 || compare(n1, n2) != 0 || compare(n2, n3) != 0) return null;
        return n0;
    }

    private boolean preferInputX() {
        final Long bf = divide(state.b, state.f);
        final Long ae = divide(state.a, state.e);
        final Long cg = divide(state.c, state.g);
        return compare(absDifference(bf, ae), absDifference(cg, ae)) > 0;
    }
}
