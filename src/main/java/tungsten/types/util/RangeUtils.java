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

import tungsten.types.Numeric;
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
import java.util.Collections;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.StreamSupport;

import static tungsten.types.Range.BoundType;

/**
 * Utility class with factory methods for commonly used types of ranges.
 *
 * @author Robert Poole <a href="mailto:Tarquin.AZ@gmail.com">Tarquin.AZ@gmail.com</a>
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

    public static <T extends Numeric & Comparable<? super T>> Range<T> rangeBetween(Range<T> A, Range<T> B) {
        if (A.overlaps(B)) throw new IllegalArgumentException("Ranges overlap, therefore there is no range between them.");
        Range<T> lowest = A.isBelow(B.getLowerBound()) ? A : B;
        Range<T> highest = B.isAbove(A.getUpperBound()) ? B : A;
        // ensure the bound types are complementary, e.g., if lowest.upperBound is inclusive (closed),
        // then the between-range's lower bound should be exclusive (open)
        return new Range<>(lowest.getUpperBound(), lowest.isUpperClosed() ? BoundType.EXCLUSIVE : BoundType.INCLUSIVE,
                highest.getLowerBound(), highest.isLowerClosed() ? BoundType.EXCLUSIVE : BoundType.INCLUSIVE);
    }

    public static <T extends Numeric & Comparable<? super T>> Range<T> merge(Range<T> A, Range<T> B) {
        if (A.contains(B)) return A;
        if (B.contains(A)) return B;
        if (!A.overlaps(B)) {
            Logger.getLogger(RangeUtils.class.getName()).log(Level.WARNING,
                    "Ranges {} and {} do not overlap; a merged range contains elements in {} that are not in either original range.",
                    new Object[] { A, B, rangeBetween(A, B) } );
        }
        T lowerBound, upperBound;
        BoundType lowerBoundType, upperBoundType;
        if (A.getLowerBound().compareTo(B.getLowerBound()) < 0) {
            lowerBound = A.getLowerBound();
            lowerBoundType = A.isLowerClosed() ? BoundType.INCLUSIVE : BoundType.EXCLUSIVE;
        } else {
            lowerBound = B.getLowerBound();
            lowerBoundType = B.isLowerClosed() ? BoundType.INCLUSIVE : BoundType.EXCLUSIVE;
        }
        if (A.getUpperBound().compareTo(B.getUpperBound()) > 0) {
            upperBound = A.getUpperBound();
            upperBoundType = A.isUpperClosed() ? BoundType.INCLUSIVE : BoundType.EXCLUSIVE;
        } else {
            upperBound = B.getUpperBound();
            upperBoundType = B.isUpperClosed() ? BoundType.INCLUSIVE : BoundType.EXCLUSIVE;
        }
        return new Range<>(lowerBound, lowerBoundType, upperBound, upperBoundType);
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
            final IntegerType start = range.isLowerClosed() ? range.getLowerBound() : (IntegerType) range.getLowerBound().add(ONE);

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
                assert limit.compareTo(start) >= 0;
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
                if (this.cardinality() == 0L) return other;
                if (this.cardinality() >= other.cardinality() &&
                        StreamSupport.stream(other.spliterator(), true).allMatch(this::contains)) {
                    return this;
                }
                return other.union(this);
            }

            @Override
            public Set<IntegerType> intersection(Set<IntegerType> other) {
                if (this.cardinality() == 0L || StreamSupport.stream(this.spliterator(), true).noneMatch(other::contains)) {
                    return EmptySet.getInstance();
                }
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
                        if (this.cardinality() >= intSet.cardinality() &&
                                StreamSupport.stream(intSet.spliterator(), true).allMatch(this::contains)) {
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

    public static Set<RealType> asRealSet(Range<RealType> range) {
        return new Set<>() {
            @Override
            public long cardinality() {
                return -1L;
            }

            @Override
            public boolean countable() {
                return false;
            }

            @Override
            public boolean contains(RealType element) {
                return range.contains(element);
            }

            @Override
            public void append(RealType element) {
                throw new UnsupportedOperationException("Cannot append to this set.");
            }

            @Override
            public void remove(RealType element) {
                throw new UnsupportedOperationException("Cannot remove elements from this set.");
            }

            @Override
            public Set<RealType> union(Set<RealType> other) {
                if (other.countable() && other.cardinality() > 0L) {
                    if (StreamSupport.stream(other.spliterator(), true).allMatch(range::contains)) {
                        // the elements of other are already contained within this set
                        return this;
                    }
                    // TODO what we need is a hybrid type of Set that can incorporate ranges as well as individual elements
                    NumericSet outOfRange = new NumericSet();
                    StreamSupport.stream(other.spliterator(), false).filter(realVal -> !range.contains(realVal))
                            .forEach(outOfRange::append);
                    Logger.getLogger(RangeUtils.class.getName()).log(Level.WARNING,
                            "Some elements of {} are outside range {}: {}",
                            new Object[] { other, range, outOfRange } );
                }
                // last resort
                return other.union(this);
            }

            @Override
            public Set<RealType> intersection(Set<RealType> other) {
                if (other.countable() && other.cardinality() > 0L) {
                    NumericSet intersection = new NumericSet();
                    StreamSupport.stream(other.spliterator(), true).filter(range::contains).forEach(intersection::append);
                    if (intersection.cardinality() == 0L) return EmptySet.getInstance();
                    try {
                        return intersection.coerceTo(RealType.class);
                    } catch (CoercionException e) {
                        throw new IllegalStateException(e);
                    }
                }
                // TODO it would be nice to be able to ascertain if other contains ranges, so we could compute
                //  the intersection of this range with those

                // last ditch effort
                return other.intersection(this);
            }

            @Override
            public Set<RealType> difference(Set<RealType> other) {
                final Set<RealType> container = this;

                return new Set<>() {
                    @Override
                    public long cardinality() {
                        return -1;
                    }

                    @Override
                    public boolean countable() {
                        return false;
                    }

                    @Override
                    public boolean contains(RealType element) {
                        return container.contains(element) && !other.contains(element);
                    }

                    @Override
                    public void append(RealType element) {
                        throw new UnsupportedOperationException("Cannot append to this set.");
                    }

                    @Override
                    public void remove(RealType element) {
                        throw new UnsupportedOperationException("Cannot remove elements from this set.");
                    }

                    @Override
                    public Set<RealType> union(Set<RealType> other) {
                        if (other.countable() && other.cardinality() > 0L) {
                            if (StreamSupport.stream(other.spliterator(), true).allMatch(this::contains)) {
                                // the elements of other are already contained within this set
                                return this;
                            }
                        }
                        return other.union(this);
                    }

                    @Override
                    public Set<RealType> intersection(Set<RealType> other) {
                        if (other.countable() && other.cardinality() > 0L) {
                            NumericSet intersection = new NumericSet();
                            StreamSupport.stream(other.spliterator(), true).filter(this::contains).forEach(intersection::append);
                            if (intersection.cardinality() == 0L) return EmptySet.getInstance();
                            try {
                                return intersection.coerceTo(RealType.class);
                            } catch (CoercionException e) {
                                throw new IllegalStateException(e);
                            }
                        }
                        return other.intersection(this);
                    }

                    @Override
                    public Set<RealType> difference(Set<RealType> other) {
                        if (other.countable() && other.cardinality() > 0L) {
                            // if none of the elements of other are in this set, we can just return this set
                            if (StreamSupport.stream(other.spliterator(), true).noneMatch(this::contains)) {
                                return this;
                            }
                            // TODO it would be nice to have a hybrid set that can handle ranges as well as elements of
                            //  both inclusion AND exclusion.
                        }
                        // this operation is non-commutative, so we can't try this in reverse
                        throw new UnsupportedOperationException("Cannot currently compute difference with a non-discrete set.");
                    }

                    @Override
                    public Iterator<RealType> iterator() {
                        return Collections.emptyIterator();
                    }
                };
            }

            @Override
            public Iterator<RealType> iterator() {
                return Collections.emptyIterator();
            }

            @Override
            public String toString() {
                return "{x in \uD835\uDD7D \u2208\u2009" + range + "\u2009}";
            }
        };
    }
}
