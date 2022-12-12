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
package tungsten.types.util;

import tungsten.types.Range;
import tungsten.types.Set;
import tungsten.types.exceptions.CoercionException;
import tungsten.types.numerics.IntegerType;
import tungsten.types.numerics.RealType;
import tungsten.types.numerics.Sign;
import tungsten.types.numerics.impl.IntegerImpl;
import tungsten.types.numerics.impl.Pi;
import tungsten.types.numerics.impl.RealInfinity;
import tungsten.types.set.impl.EmptySet;
import tungsten.types.set.impl.NumericSet;

import java.math.BigInteger;
import java.math.MathContext;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.stream.StreamSupport;

import static tungsten.types.Range.BoundType;

/**
 * Utility class with factory methods for commonly used types of ranges.
 *
 * @author Robert Poole <Tarquin.AZ@gmail.com>
 */
public class RangeUtils {
    /**
     * The default input range for all real-valued functions.
     */
    public static final Range<RealType> ALL_REALS =
            new Range<>(RealInfinity.getInstance(Sign.NEGATIVE, MathContext.UNLIMITED),
                    RealInfinity.getInstance(Sign.POSITIVE, MathContext.UNLIMITED), BoundType.EXCLUSIVE);

    /**
     * Generate a range of (-pi, pi] for the given {@link MathContext}.
     * Note that this is the typical range of return values for atan2.
     * @param mctx the math context
     * @return a range representing the interval (-pi, pi]
     */
    public static Range<RealType> getAngularInstance(MathContext mctx) {
        final Pi pi = Pi.getInstance(mctx);
        return new Range<>(pi.negate(), BoundType.EXCLUSIVE, pi, BoundType.INCLUSIVE) {
            @Override
            public String toString() {
                return "(\u2212\uD835\uDF0B, \uD835\uDF0B]";
            }
        };
    }
    
    /**
     * Generate an interval that is symmetric around the origin, with the
     * specified bound type on both ends.  Negative values for {@code distance}
     * will be coerced to their absolute value.
     * @param distance the distance of each boundary from the origin
     * @param type the type of boundary for both ends
     * @return the desired range
     */
    public static Range<RealType> symmetricAroundOrigin(RealType distance, BoundType type) {
        distance = distance.magnitude();  // absolute value
        
        return new Range<>(distance.negate(), distance, type);
    }

    public static Range<IntegerType> symmetricAroundOrigin(IntegerType distance, BoundType type) {
        distance = distance.magnitude();  // absolute value
        
        return new Range<>(distance.negate(), distance, type);
    }

    public static Range<IntegerType> fromSet(Set<IntegerType> source) {
        IntegerType min = StreamSupport.stream(source.spliterator(), true).min(IntegerType::compareTo).orElseThrow();
        IntegerType max = StreamSupport.stream(source.spliterator(), true).max(IntegerType::compareTo).orElseThrow();
        // check that there are no gaps in the source, otherwise the range cannot represent it
        IntegerType current = (IntegerType) min.add(ONE);  // we don't need to check the min or max values themselves
        while (current.compareTo(max) < 0) {
            if (!source.contains(current)) {
                throw new IllegalArgumentException("Cannot extract a range; source is discontiguous starting at " + current);
            }
            current = (IntegerType) current.add(ONE);
        }
        return new Range<>(min, max, BoundType.INCLUSIVE);
    }

    private static final IntegerType ONE = new IntegerImpl(BigInteger.ONE);

    public static Set<IntegerType> asSet(Range<IntegerType> range) {
        return new Set<>() {
            final IntegerType limit = range.isUpperClosed() ? range.getUpperBound() : (IntegerType) range.getUpperBound().subtract(ONE);
            final IntegerType start = new IntegerImpl(range.isLowerClosed() ? range.getLowerBound().asBigInteger() : ((IntegerType) range.getLowerBound().add(ONE)).asBigInteger());

            class RangeIterator implements Iterator<IntegerType> {
                private IntegerType current = start;

                @Override
                public boolean hasNext() {
                    return current.compareTo(limit) <= 0;
                }

                @Override
                public IntegerType next() {
                    if (current.compareTo(limit) > 0) throw new NoSuchElementException("No more elements in set.");
                    IntegerType retval = current;
                    current = (IntegerType) current.add(ONE);
                    return retval;
                }
            }

            @Override
            public long cardinality() {
                assert limit.compareTo(start) > 0;
                IntegerType cardinality = (IntegerType) limit.subtract(start).add(ONE);
                return cardinality.asBigInteger().longValueExact();
            }

            @Override
            public boolean countable() {
                return true;
            }

            @Override
            public boolean contains(IntegerType element) {
                return range.contains(element);
            }

            @Override
            public void append(IntegerType element) {
                throw new UnsupportedOperationException("Cannot append to this set.");
            }

            @Override
            public void remove(IntegerType element) {
                throw new UnsupportedOperationException("Cannot remove elements from this set.");
            }

            @Override
            public Set<IntegerType> union(Set<IntegerType> other) {
                if (StreamSupport.stream(this.spliterator(), true).allMatch(other::contains) &&
                        this.cardinality() >= other.cardinality()) {
                    return this;
                }
                return other.union(this);
            }

            @Override
            public Set<IntegerType> intersection(Set<IntegerType> other) {
                return other.intersection(this);
            }

            @Override
            public Set<IntegerType> difference(Set<IntegerType> other) {
                final Set<IntegerType> container = this;

                return new Set<>() {
                    @Override
                    public long cardinality() {
                        return StreamSupport.stream(container.spliterator(), true).dropWhile(other::contains).count();
                    }

                    @Override
                    public boolean countable() {
                        return true;
                    }

                    @Override
                    public boolean contains(IntegerType element) {
                        return container.contains(element) && !other.contains(element);
                    }

                    @Override
                    public void append(IntegerType element) {
                        throw new UnsupportedOperationException("Cannot append to this set.");
                    }

                    @Override
                    public void remove(IntegerType element) {
                        throw new UnsupportedOperationException("Cannot remove elements from this set.");
                    }

                    @Override
                    public Set<IntegerType> union(Set<IntegerType> intSet) {
                        if (this.cardinality() == 0L) return intSet;
                        if (StreamSupport.stream(this.spliterator(), true).allMatch(intSet::contains) &&
                                this.cardinality() >= intSet.cardinality()) {
                            return this;
                        }
                        NumericSet aggregate = new NumericSet();
                        this.forEach(aggregate::append);
                        intSet.forEach(aggregate::append);
                        try {
                            return aggregate.coerceTo(IntegerType.class);
                        } catch (CoercionException e) {
                            throw new IllegalStateException(e);
                        }
                    }

                    @Override
                    public Set<IntegerType> intersection(Set<IntegerType> intSet) {
                        NumericSet intersection = new NumericSet();
                        StreamSupport.stream(this.spliterator(), true).filter(intSet::contains).forEach(intersection::append);
                        if (intersection.cardinality() == 0L) return EmptySet.getInstance();
                        try {
                            return intersection.coerceTo(IntegerType.class);
                        } catch (CoercionException e) {
                            throw new IllegalStateException(e);
                        }
                    }

                    @Override
                    public Set<IntegerType> difference(Set<IntegerType> intSet) {
                        NumericSet difference = new NumericSet();
                        StreamSupport.stream(this.spliterator(), true).dropWhile(intSet::contains).forEach(difference::append);
                        if (difference.cardinality() == 0L) return EmptySet.getInstance();
                        try {
                            return difference.coerceTo(IntegerType.class);
                        } catch (CoercionException e) {
                            throw new IllegalStateException(e);
                        }
                    }

                    @Override
                    public Iterator<IntegerType> iterator() {
                        return StreamSupport.stream(container.spliterator(), false).dropWhile(other::contains).iterator();
                    }

                    @Override
                    public String toString() {
                        StringBuilder buf = new StringBuilder();
                        buf.append('{');
                        Iterator<IntegerType> iterator = this.iterator();
                        while (iterator.hasNext()) {
                            buf.append(iterator.next());
                            if (iterator.hasNext()) buf.append(", ");
                        }
                        buf.append('}');
                        return buf.toString();
                    }
                };
            }

            @Override
            public Iterator<IntegerType> iterator() {
                return new RangeIterator();
            }

            @Override
            public String toString() {
                return "{x in \uD835\uDD74 \u2208\u2009" + range + "\u2009}";
            }
        };
    }
}
