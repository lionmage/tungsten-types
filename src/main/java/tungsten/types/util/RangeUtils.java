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

import tungsten.types.*;
import tungsten.types.Set;
import tungsten.types.exceptions.CoercionException;
import tungsten.types.numerics.IntegerType;
import tungsten.types.numerics.RealType;
import tungsten.types.numerics.Sign;
import tungsten.types.numerics.impl.IntegerImpl;
import tungsten.types.numerics.impl.Pi;
import tungsten.types.numerics.impl.RealImpl;
import tungsten.types.numerics.impl.RealInfinity;
import tungsten.types.set.impl.*;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.StreamSupport;

import static tungsten.types.Range.BoundType;

/**
 * Utility class with factory methods for commonly used types of ranges.
 *
 * @author Robert Poole, <a href="mailto:Tarquin.AZ@gmail.com">Tarquin.AZ@gmail.com</a>
 */
public class RangeUtils {
    private static final BigInteger MAX_LONG = BigInteger.valueOf(Long.MAX_VALUE);
    private static final BigInteger MIN_LONG = BigInteger.valueOf(Long.MIN_VALUE);
    private static final String CANNOT_APPEND = "Cannot append to this set";
    private static final String CANNOT_REMOVE = "Cannot remove elements from this set";

    private RangeUtils() {
        // to prevent instantiation
    }

    /**
     * The default input range for all real-valued functions.
     */
    public static final Range<RealType> ALL_REALS =
            new Range<>(RealInfinity.getInstance(Sign.NEGATIVE, MathContext.UNLIMITED),
                    RealInfinity.getInstance(Sign.POSITIVE, MathContext.UNLIMITED), BoundType.EXCLUSIVE);
    /**
     * A range specifying all integer values that fit into (i.e., can be losslessly converted to)
     * a {@code long}.  Any {@code IntegerType} value contained by this range can be safely
     * converted to a {@code long} using {@code value.asBigInteger().longValueExact()}.
     */
    public static final Range<IntegerType> ALL_LONGS =
            new Range<>(new IntegerImpl(MIN_LONG), new IntegerImpl(MAX_LONG), BoundType.INCLUSIVE);

    /**
     * Generate a range with an exclusive lower bound of 0 and an upper bound of 1.
     * @param mctx      the MathContext for the bounds
     * @param inclusive if true, use an inclusive upper bound; otherwise use an exclusive upper bound
     * @return a range representing an interval between 0 and 1
     * @since 0.8
     */
    public static Range<RealType> getZeroToUnity(MathContext mctx, boolean inclusive) {
        RealType zero = new RealImpl(BigDecimal.ZERO, mctx);
        RealType unity = new RealImpl(BigDecimal.ONE, mctx);
        return new Range<>(zero, BoundType.EXCLUSIVE, unity, inclusive ? BoundType.INCLUSIVE : BoundType.EXCLUSIVE);
    }

    /**
     * Generate a range of (&minus;&pi;, &pi;] for the given {@link MathContext}.
     * Note that this is the typical range of return values for atan2 and
     * is the principal input range for cos and sin.
     * @param mctx the math context
     * @return a range representing the interval (&minus;&pi;, &pi;]
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
     * Generate a range of (&minus;&pi;, &pi;) with an arbitrarily
     * chosen {@code MathContext}. This is the typical range of values
     * permitted for Arg(z) when computing ln&#x1D6AA;(z).
     * @return a range representing the interval (&minus;&pi;, &pi;)
     */
    public static Range<RealType> getGammaArgumentInstance() {
        // MathContext is chosen arbitrarily
        final RealType pi = Pi.getInstance(MathContext.DECIMAL128);
        return new Range<>(pi.negate(), pi, BoundType.EXCLUSIVE);
    }

    /**
     * Generate a range of (&minus;&pi;/2, &pi;/2) for the given {@link MathContext}.
     * Note that this is the principal input range for the tan function.
     * @param mctx the math context
     * @return a range representing the interval (&minus;&pi;/2, &pi;/2)
     */
    public static Range<RealType> getTangentInstance(MathContext mctx) {
        final RealType halfPi = (RealType) Pi.getInstance(mctx).divide(new RealImpl(BigDecimal.valueOf(2L), mctx));
        return new Range<>(halfPi.negate(), halfPi, Range.BoundType.EXCLUSIVE) {
            @Override
            public String toString() {
                return "(\u2212\uD835\uDF0B/2, \uD835\uDF0B/2)";
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
        if (A.overlaps(B)) throw new IllegalArgumentException("Ranges overlap, therefore there is no range between them");
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
                    "Ranges {0} and {1} do not overlap; a merged range contains elements in {2} that are not in either original range.",
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
        // check that there are no gaps in the source, otherwise we will need a NotchedRange
        List<IntegerType> notches = new ArrayList<>();
        IntegerType current = (IntegerType) min.add(ONE);  // we don't need to check the min or max values themselves
        while (current.compareTo(max) < 0) {
            if (!source.contains(current)) {
                // log the first discontinuity
                if (notches.isEmpty()) Logger.getLogger(RangeUtils.class.getName()).log(Level.INFO,
                        "Source set is discontiguous starting at {0}.", current);
                notches.add(current);
            }
            current = (IntegerType) current.add(ONE);
        }
        return notches.isEmpty() ? new Range<>(min, max, BoundType.INCLUSIVE) : new NotchedRange<>(min, max, BoundType.INCLUSIVE, notches.toArray(IntegerType[]::new));
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
                    if (current.compareTo(limit) > 0) throw new NoSuchElementException("No more elements in set");
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
            public boolean isOfType(Class<?> clazz) {
                return IntegerType.class.isAssignableFrom(clazz);
            }

            @Override
            public void append(IntegerType element) {
                throw new UnsupportedOperationException(CANNOT_APPEND);
            }

            @Override
            public void remove(IntegerType element) {
                throw new UnsupportedOperationException(CANNOT_REMOVE);
            }

            @Override
            public Set<IntegerType> union(Set<IntegerType> other) {
                if (other.cardinality() == 0L) return this;  // this.cardinality() will always be >= 1
                if (this.cardinality() >= other.cardinality() &&
                        StreamSupport.stream(other.spliterator(), true).allMatch(this::contains)) {
                    return this;
                }
                return new UnionSet<>(this, other);
            }

            @Override
            public Set<IntegerType> intersection(Set<IntegerType> other) {
                // this.cardinality() will always be >= 1
                NumericSet intersection = new NumericSet();
                StreamSupport.stream(this.spliterator(), true).filter(other::contains).forEach(intersection::append);
                if (intersection.cardinality() == 0L) return EmptySet.getInstance();
                try {
                    return intersection.coerceTo(IntegerType.class);
                } catch (CoercionException e) {
                    throw new IllegalStateException("While computing intersection set", e);
                }
            }

            @Override
            public Set<IntegerType> difference(Set<IntegerType> other) {
                if (other.cardinality() == 0L) return this;
                final Set<IntegerType> parent = this;

                return new DiffSet<>(this, other) {
                    @Override
                    public Set<IntegerType> union(Set<IntegerType> rhs) {
                        if (rhs.cardinality() == 0L) return this;
                        if (other.equals(rhs)) return parent;
                        return new UnionSet<>(this, rhs);
                    }
                };
            }

            @Override
            public boolean equals(Object obj) {
                if (obj instanceof Set) {
                    Set<?> that = (Set<?>) obj;
                    if (!that.countable() || that.cardinality() != this.cardinality()) return false;
                    if (!that.isOfType(IntegerType.class)) return false;
                    Set<IntegerType> foreignInts = (Set<IntegerType>) that;
                    return StreamSupport.stream(this.spliterator(), true).allMatch(foreignInts::contains);
                }
                return false;
            }

            @Override
            public int hashCode() {
                return Objects.hash(range, start, limit);
            }

            @Override
            public Iterator<IntegerType> iterator() {
                return new RangeIterator();
            }

            @Override
            public String toString() {
                return "{x in \u2124 \u2208\u2009" + range + "\u2009}";
            }
        };
    }

    public static Set<RealType> asRealSet(Range<RealType> range) {
        if (range instanceof SteppedRange) {
            SteppedRange srange = (SteppedRange) range;

            return new Set<>() {
                @Override
                public long cardinality() {
                    RealType count = (RealType) srange.getUpperBound().subtract(srange.getLowerBound()).divide(srange.getStepSize());
                    return count.asBigDecimal().longValue();
                }

                @Override
                public boolean countable() {
                    return true;
                }

                @Override
                public boolean contains(RealType element) {
                    return srange.parallelStream().anyMatch(element::equals);
                }

                @Override
                public void append(RealType element) {
                    throw new UnsupportedOperationException(CANNOT_APPEND);
                }

                @Override
                public void remove(RealType element) {
                    throw new UnsupportedOperationException(CANNOT_REMOVE);
                }

                @Override
                public Set<RealType> union(Set<RealType> other) {
                    return new UnionSet<>(this, other);
                }

                @Override
                public Set<RealType> intersection(Set<RealType> other) {
                    if (other.cardinality() == 0L) return EmptySet.getInstance();
                    NumericSet intersec = new NumericSet();
                    for (RealType element : srange) {
                        if (other.contains(element)) intersec.append(element);
                    }
                    if (intersec.cardinality() == 0L) return EmptySet.getInstance();
                    try {
                        return intersec.coerceTo(RealType.class);
                    } catch (CoercionException e) {
                        throw new IllegalStateException("While computing set intersection", e);
                    }
                }

                @Override
                public Set<RealType> difference(Set<RealType> other) {
                    if (other.cardinality() == 0L) return this;
                    final Set<RealType> parent = this;

                    return new DiffSet<>(this, other) {
                        @Override
                        public Set<RealType> union(Set<RealType> rhs) {
                            if (rhs.cardinality() == 0L) return this;
                            if (other.equals(rhs)) return parent;
                            return new UnionSet<>(this, rhs);
                        }
                    };
                }

                @Override
                public Iterator<RealType> iterator() {
                    return srange.iterator();
                }

                @Override
                public boolean equals(Object obj) {
                    if (obj instanceof Set) {
                        Set<?> that = (Set<?>) obj;
                        if (!that.countable() || !that.isOfType(RealType.class)) return false;
                        if (this.cardinality() != that.cardinality()) return false;
                        Set<RealType> other = (Set<RealType>) that;
                        return srange.parallelStream().allMatch(other::contains);
                    }
                    return false;
                }

                @Override
                public int hashCode() {
                    return 7 * Objects.hashCode(srange) - 5;
                }
            };
        }
        // not a stepped range, therefore not countable
        return new RangeSet(range);
    }

    private static class RangeSet implements Set<RealType> {
        private final Range<RealType> range;

        public RangeSet(Range<RealType> range) {
            this.range = range;
        }

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
        public boolean isOfType(Class<?> clazz) {
            return RealType.class.isAssignableFrom(clazz);
        }

        @Override
        public void append(RealType element) {
            throw new UnsupportedOperationException(CANNOT_APPEND);
        }

        @Override
        public void remove(RealType element) {
            throw new UnsupportedOperationException(CANNOT_REMOVE);
        }

        @Override
        public Set<RealType> union(Set<RealType> other) {
            if (other.countable() && other.cardinality() > 0L) {
                if (StreamSupport.stream(other.spliterator(), true).allMatch(range::contains)) {
                    // the elements of other are already contained within this set
                    return this;
                }
                if (range instanceof NotchedRange) {
                    NotchedRange<RealType> notchedRange = (NotchedRange<RealType>) range;
                    Set<RealType> notches = notchedRange.getNotches();
                    if (notches.cardinality() <= other.cardinality() &&
                            StreamSupport.stream(notches.spliterator(), true).allMatch(other::contains)) {
                        Set<RealType> lhs = asRealSet(notchedRange.getInnerRange());
                        if (other.cardinality() > notches.cardinality()) {
                            Set<RealType> remainder = other.difference(notches);
                            return new UnionSet<>(lhs, remainder);
                        }
                        return lhs;
                    }
                }
                NumericSet outOfRange = new NumericSet();
                StreamSupport.stream(other.spliterator(), false).filter(realVal -> !range.contains(realVal))
                        .forEach(outOfRange::append);
                Logger.getLogger(RangeUtils.class.getName()).log(Level.WARNING,
                        "Some elements of {0} are outside range {1}: {2}",
                        new Object[] { other, range, outOfRange } );
                try {
                    // compute a union set with no overlap between the component sets
                    return new UnionSet<>(this, outOfRange.coerceTo(RealType.class));
                } catch (CoercionException e) {
                    throw new IllegalStateException("While computing union set", e);
                }
            }
            if (other instanceof RangeSet) {
                RangeSet that = (RangeSet) other;
                if (range.contains(that.range)) return this;
                else if (that.range.contains(range)) return that;
                if (!range.overlaps(that.range)) {
                    return new UnionSet<>(this, that);
                }
                return new RangeSet(merge(range, that.range));
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
                    throw new IllegalStateException("While computing intersection set", e);
                }
            }
            if (other instanceof RangeSet) {
                RangeSet that = (RangeSet) other;
                if (range.overlaps(that.range)) {
                    if (range.contains(that.range)) return that;
                    else if (that.range.contains(range)) return this;
                    else {
                        RealType lowerBound = MathUtils.max(range.getLowerBound(), that.range.getLowerBound());
                        RealType upperBound = MathUtils.min(range.getUpperBound(), that.range.getUpperBound());
                        BoundType lowerType;
                        BoundType upperType;
                        if (lowerBound.equals(range.getLowerBound())) {
                            lowerType = range.isLowerClosed() ? BoundType.INCLUSIVE : BoundType.EXCLUSIVE;
                        } else {
                            lowerType = that.range.isLowerClosed() ? BoundType.INCLUSIVE : BoundType.EXCLUSIVE;
                        }
                        if (upperBound.equals(range.getUpperBound())) {
                            upperType = range.isUpperClosed() ? BoundType.INCLUSIVE : BoundType.EXCLUSIVE;
                        } else {
                            upperType = that.range.isUpperClosed() ? BoundType.INCLUSIVE : BoundType.EXCLUSIVE;
                        }
                        Range<RealType> intersection = new Range<>(lowerBound, lowerType, upperBound, upperType);
                        return new RangeSet(intersection);
                    }
                } else {
                    return EmptySet.getInstance();
                }
            }

            // last ditch effort
            return other.intersection(this);
        }

        @Override
        public Set<RealType> difference(Set<RealType> other) {
            if (range instanceof NotchedRange) {
                Set<RealType> notches = ((NotchedRange<RealType>) range).getNotches();
                Set<RealType> rangeSet = asRealSet(((NotchedRange<RealType>) range).getInnerRange());
                return new DiffSet<>(rangeSet, new UnionSet<>(other, notches));
            }
            if (other instanceof RangeSet) {
                RangeSet that = (RangeSet) other;
                if (!range.overlaps(that.range)) return this;
                if (that.range.contains(range)) return EmptySet.getInstance();
                final Range<RealType> topRange = new Range<>(that.range.getUpperBound(),
                        that.range.isUpperClosed() ? BoundType.EXCLUSIVE : BoundType.INCLUSIVE,
                        range.getUpperBound(),
                        range.isUpperClosed() ? BoundType.INCLUSIVE : BoundType.EXCLUSIVE);
                final Range<RealType> bottomRange = new Range<>(range.getLowerBound(),
                        range.isLowerClosed() ? BoundType.INCLUSIVE : BoundType.EXCLUSIVE,
                        that.range.getLowerBound(),
                        that.range.isLowerClosed() ? BoundType.EXCLUSIVE : BoundType.INCLUSIVE);
                if (range.contains(that.range)) {
                    // we're taking a chunk out of the middle
                    return new UnionSet<>(new RangeSet(bottomRange), new RangeSet(topRange));
                }
                if (range.isBelow(that.range.getLowerBound())) {
                    return new RangeSet(topRange);
                } else if (range.isAbove(that.range.getUpperBound())) {
                    return new RangeSet(bottomRange);
                }
                Logger.getLogger(RangeSet.class.getName()).log(Level.WARNING,
                        "Failed to compute difference between {0} and {1}.",
                        new Object[] {this, that});
            }
            return new DiffSet<>(this, other) {
                @Override
                public Set<RealType> union(Set<RealType> rhs) {
                    if (rhs.cardinality() == 0L) return this;
                    if (rhs.equals(other)) return RangeSet.this;
                    return new UnionSet<>(this, rhs);
                }
            };
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof RangeSet)) return false;
            RangeSet realTypes = (RangeSet) o;
            return Objects.equals(range, realTypes.range);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(range);
        }

        @Override
        public Iterator<RealType> iterator() {
            return Collections.emptyIterator();
        }

        @Override
        public String toString() {
            return "{x in \u211D \u2208\u2009" + range + "\u2009}";
        }
    }
}
