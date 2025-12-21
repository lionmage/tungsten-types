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

package tungsten.types.functions.indexed;

import tungsten.types.Range;
import tungsten.types.numerics.IntegerType;
import tungsten.types.numerics.impl.IntegerImpl;

import java.math.BigInteger;
import java.util.Comparator;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Spliterator;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * A range of integers that supports iteration and parallel stream operations.
 * @author Robert Poole, <a href="mailto:tarquin@alum.mit.edu">MIT alumni e-mail</a> or
 *   <a href="mailto:Tarquin.AZ+Tungsten@gmail.com">Gmail</a>
 * @since 0.8
 */
public class IndexRange extends Range<IntegerType> implements Iterable<IntegerType> {
    private static final IntegerType ONE = new IntegerImpl(BigInteger.ONE, true);
    private static final IntegerType MIN_SPLIT = new IntegerImpl(BigInteger.valueOf(5L), true);

    /**
     * Create an index range from bounds.
     * @param lower       the lower bound of the index range, inclusive
     * @param upper       the upper bound of the index range
     * @param upperClosed if {@code true}, treat the upper bound as inclusive, otherwise
     *                    treat it as exclusive
     */
    public IndexRange(IntegerType lower, IntegerType upper, boolean upperClosed) {
        super(lower, BoundType.INCLUSIVE, upper, upperClosed ? BoundType.INCLUSIVE : BoundType.EXCLUSIVE);
    }

    /**
     * Convenience constructor that takes primitive {@code long} values.
     * The upper and lower bounds are inclusive.
     * @param lower the lower bound
     * @param upper the upper bound
     */
    public IndexRange(long lower, long upper) {
        this(lower, upper, true);
    }

    /**
     * Convenience constructor that takes primitive {@code long} values
     * and an argument that determines whether the upper bound is open or closed.
     * @param lower       the lower bound, inclusive
     * @param upper       the upper bound
     * @param upperClosed if {@code true}, treat the upper bound as closed (inclusive)
     */
    public IndexRange(long lower, long upper, boolean upperClosed) {
        super(new IntegerImpl(BigInteger.valueOf(lower)),
                BoundType.INCLUSIVE,
                new IntegerImpl(BigInteger.valueOf(upper)),
                upperClosed ? BoundType.INCLUSIVE : BoundType.EXCLUSIVE);
    }

    @Override
    public Iterator<IntegerType> iterator() {
        final IntegerType initial = this.isLowerClosed() ?
                getLowerBound() : (IntegerType) getLowerBound().add(ONE);
        final IntegerType last = this.isUpperClosed() ?
                getUpperBound() : (IntegerType) getUpperBound().subtract(ONE);

        return new Iterator<>() {
            private IntegerType current = initial;

            @Override
            public boolean hasNext() {
                return current.compareTo(last) <= 0;
            }

            @Override
            public IntegerType next() {
                if (current.compareTo(last) > 0) throw new NoSuchElementException("No more elements in range");
                IntegerType value = current;
                current = (IntegerType) current.add(ONE);
                return value;
            }
        };
    }

    @Override
    public Spliterator<IntegerType> spliterator() {
        final IntegerType limit = isUpperClosed() ? getUpperBound() : (IntegerType) getUpperBound().subtract(ONE);

        return new Spliterator<>() {
            IntegerType current = isLowerClosed() ? getLowerBound() : (IntegerType) getLowerBound().add(ONE);

            @Override
            public boolean tryAdvance(Consumer<? super IntegerType> action) {
                if (current.compareTo(limit) > 0) return false;
                action.accept(current);
                current = (IntegerType) current.add(ONE);
                return true;
            }

            @Override
            public Spliterator<IntegerType> trySplit() {
                IntegerType span = (IntegerType) limit.subtract(current);
                if (span.compareTo(MIN_SPLIT) <= 0) return null;
                // using .divide(TWO).add(current) could result in a RationalType result, which would cause
                // a ClassCastException
                IntegerType midway = (IntegerType) span.rightShift(ONE).add(current);
                IndexRange subrange = new IndexRange(current, midway, true);
                current = (IntegerType) midway.add(ONE);
                return subrange.spliterator();
            }

            @Override
            public long estimateSize() {
                BigInteger delta = limit.asBigInteger().subtract(current.asBigInteger());
                try {
                    return delta.longValueExact() + 1L;
                } catch (ArithmeticException aex) {
                    return Long.MAX_VALUE;
                }
            }

            @Override
            public int characteristics() {
                return ORDERED | DISTINCT | SORTED | SIZED | IMMUTABLE;
            }

            @Override
            public Comparator<? super IntegerType> getComparator() {
                // returning null because the elements in this range are in natural order
                return null;
            }
        };
    }

    /**
     * Obtain a {@code Stream} of sequential values from this range.
     * @return the stream of values, returned in ascending order
     */
    public Stream<IntegerType> stream() {
        return StreamSupport.stream(this.spliterator(), false);
    }

    /**
     * Obtain a parallel {@code Stream} of the values represented by this range.
     * This is suitable for sums and products that can be constructed in a
     * non-deterministic order.
     * @return the parallel stream of values
     */
    public Stream<IntegerType> parallelStream() {
        return StreamSupport.stream(this.spliterator(), true);
    }
}
