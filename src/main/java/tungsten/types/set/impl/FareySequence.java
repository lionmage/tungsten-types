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
import tungsten.types.exceptions.CoercionException;
import tungsten.types.numerics.IntegerType;
import tungsten.types.numerics.RationalType;
import tungsten.types.numerics.impl.IntegerImpl;
import tungsten.types.numerics.impl.RationalImpl;
import tungsten.types.util.OptionalOperations;
import tungsten.types.util.UnicodeTextEffects;

import java.math.BigInteger;
import java.math.MathContext;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * A {@code Set} that contains a Farey sequence of some order n,
 * denoted F<sub>n</sub>. Farey sequences appear in places such as
 * fractals &mdash; for example,
 * <a href="https://gauss.math.yale.edu/fractals/MandelSet/MandelCombinatorics/FareySeq/FareySeq.html">the
 * Mandelbrot set</a>.  They are also related to continued fractions &mdash; neighbors in a
 * Farey sequence have closely related continued fraction representations.
 * @author Robert Poole, <a href="mailto:tarquin@alum.mit.edu">MIT alumni e-mail</a>
 *   or <a href="mailto:Tarquin.AZ@gmail.com">Gmail</a>
 * @since 0.6
 * @see <a href="https://en.wikipedia.org/wiki/Farey_sequence">the Wikipedia article</a>
 * @see <a href="https://mathworld.wolfram.com/FareySequence.html">the Wolfram MathWorld article</a>
 */
public class FareySequence implements Set<RationalType> {
    private MathContext mctx;
    private final SortedSet<RationalType> members = new TreeSet<>();
    private final long count;
    private final long order;

    /**
     * Construct a Farey sequence of a given order whose members have
     * the given {@code MathContext}.
     * @param n    the order of the Farey sequence, must be &ge;&nbsp;1
     * @param mctx the {@code MathContext} for all the rational elements of this {@code Set}
     */
    public FareySequence(long n, MathContext mctx) {
        if (n < 1L) throw new IllegalArgumentException("Farey sequence must be at least of order 1");
        order = n;
        this.mctx = mctx;

        long a = 0L;
        long b = 1L;
        long c = 1L;
        long d = n;
        members.add(new RationalImpl(a, b, mctx));
        long elementCount = 1L;
        while (c >= 0L && c <= n) {
            final long k = (n + b) / d;
            a = c;
            b = d;
            c = k * c - a;
            d = k * d - b;
            members.add(new RationalImpl(a, b, mctx));
            elementCount++;
        }
        count = elementCount;
    }

    private FareySequence(Collection<RationalType> source, long order) {
        this.order = order;
        members.addAll(source);
        count = source.stream().count();
    }

    /**
     * Obtain the order of this Farey sequence.
     * @return the order of this sequence
     */
    public long order() {
        return order;
    }

    @Override
    public long cardinality() {
        return count;
    }

    @Override
    public boolean countable() {
        return true;
    }

    @Override
    public boolean contains(RationalType element) {
        return members.contains(element);
    }

    @Override
    public void append(RationalType element) {
        throw new UnsupportedOperationException("Cannot append to a Farey sequence");
    }

    @Override
    public void remove(RationalType element) {
        throw new UnsupportedOperationException("Cannot remove elements from a Farey sequence");
    }

    @Override
    public Set<RationalType> union(Set<RationalType> other) {
        if (other.cardinality() == 0L) return this;
        if (other.countable() && other.cardinality() > 0L) {
            SortedSet<RationalType> all = new TreeSet<>(members);
            other.forEach(all::add);
            try {
                return new NumericSet(all).coerceTo(RationalType.class);
            } catch (CoercionException e) {
                throw new IllegalStateException("While computing Set union", e);
            }
        }
        // last ditch effort
        return other.union(this);
    }

    @Override
    public Set<RationalType> intersection(Set<RationalType> other) {
        NumericSet result = new NumericSet();
        for (RationalType value : members) {
            if (other.contains(value)) result.append(value);
        }
        if (result.cardinality() == 0L) return EmptySet.getInstance();
        try {
            return result.coerceTo(RationalType.class);
        } catch (CoercionException e) {
            throw new IllegalStateException("While computing Set intersection", e);
        }
    }

    @Override
    public Set<RationalType> difference(Set<RationalType> other) {
        SortedSet<RationalType> diff = new TreeSet<>(members);
        diff.removeIf(other::contains);
        if (diff.isEmpty()) return EmptySet.getInstance();
        NumericSet result = new NumericSet(diff);
        try {
            return result.coerceTo(RationalType.class);
        } catch (CoercionException e) {
            throw new IllegalStateException("While computing Set difference", e);
        }
    }

    private RationalType mediant(RationalType first, RationalType second) {
        IntegerType num = (IntegerType) first.numerator().add(second.numerator());
        IntegerType denom = (IntegerType) first.denominator().add(second.denominator());
        return new RationalImpl(num, denom, mctx).reduce();
    }

    /**
     * Updates this {@code Set}'s members to use the given {@code MathContext}
     * @param ctx a valid {@code MathContext}
     */
    public void setMathContext(MathContext ctx) {
        this.mctx = ctx;
        members.forEach(element -> OptionalOperations.setMathContext(element, ctx));
    }

    @Override
    public Iterator<RationalType> iterator() {
        return members.iterator();
    }

    @Override
    public void forEach(Consumer<? super RationalType> action) {
        members.forEach(action);
    }

    @Override
    public Spliterator<RationalType> spliterator() {
        final RationalType half = new RationalImpl(BigInteger.ONE, BigInteger.TWO, mctx);

        /*
         This Spliterator is simple, and will delegate to other Spliterator
         implementations beyond the base level.
         */
        return new Spliterator<>() {
            private SortedSet<RationalType> inner = new TreeSet<>(members);
            private long remaining = count;

            @Override
            public boolean tryAdvance(Consumer<? super RationalType> action) {
                if (inner.isEmpty()) return false;
                RationalType head = inner.first();
                if (head != null) {
                    action.andThen(inner::remove).accept(head);
                    remaining--;
                    return true;
                }
                return false;
            }

            @Override
            public Spliterator<RationalType> trySplit() {
                if (order < 3L) return null;
                SortedSet<RationalType> head = inner.headSet(half);
                int headSize = head.size();
                remaining -= headSize < Integer.MAX_VALUE ? headSize : head.stream().count();
                inner = inner.tailSet(half);
                return head.spliterator();
            }

            @Override
            public long estimateSize() {
                return remaining;
            }

            @Override
            public int characteristics() {
                return SIZED | DISTINCT | IMMUTABLE | ORDERED | SORTED | SUBSIZED | NONNULL;
            }
        };
    }

    @Override
    public boolean isOfType(Class<?> clazz) {
        return RationalType.class.isAssignableFrom(clazz);
    }

    @Override
    public int hashCode() {
        return Objects.hash(members, mctx, order);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof Set) {
            if (obj instanceof FareySequence) {
                FareySequence farey = (FareySequence) obj;
                return this.order == farey.order();
            }
            Set<?> that = (Set<?>) obj;
            if (!that.isOfType(RationalType.class)) return false;
            if (this.cardinality() != that.cardinality()) return false;
            return this.difference((Set<RationalType>) that).cardinality() == 0L;
        }
        return false;
    }

    @Override
    public String toString() {
        String prefix = "F" + UnicodeTextEffects.numericSubscript((int) order) + " = {";
        return members.stream().map(RationalType::toString)
                .collect(Collectors.joining(",\u2009", prefix, "}"));
    }

    /*
     Groovy-compliant operations below.
     */

    /**
     * For F<sub>n</sub>, generates F<sub>n+1</sub>.
     * @return the Farey sequence with an order that is 1 greater than {@code this}
     */
    public FareySequence next() {
        final IntegerType n = new IntegerImpl(BigInteger.valueOf(count));
        final List<RationalType> updated = new LinkedList<>(members);
        RationalType prev = null;
        for (RationalType current : members) {
            if (prev != null) {
                IntegerType denomSum = (IntegerType) prev.denominator().add(current.denominator());
                if (denomSum.compareTo(n) <= 0) {
                    // we don't need to worry about inserting this into the correct spot
                    // the constructor below will be adding this List to a SortedSet
                    updated.add(mediant(prev, current));
                }
            }
            prev = current;
        }
        FareySequence result = new FareySequence(updated, order + 1L);
        result.setMathContext(mctx);
        return result;
    }

    /**
     * For F<sub>n</sub>, generates F<sub>n&minus;1</sub>.
     * @return the Farey sequence with an order that is 1 less than {@code this}
     */
    public FareySequence previous() {
        if (order == 1) throw new NoSuchElementException("F\u2081 has no previous set");
        return new FareySequence(order - 1, mctx);
    }
}
