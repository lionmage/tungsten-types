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
//                RangeIterator iter = new RangeIterator();
//                long count = 0L;
//                while (iter.hasNext()) {
//                    count++;
//                    iter.next();
//                }
//                return count;
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
                return other.union(this);
            }

            @Override
            public Set<IntegerType> intersection(Set<IntegerType> other) {
                return other.intersection(this);
            }

            @Override
            public Set<IntegerType> difference(Set<IntegerType> other) {
                NumericSet diffSet = new NumericSet();
                StreamSupport.stream(spliterator(), true).dropWhile(other::contains)
                        .forEach(diffSet::append);
                try {
                    return diffSet.coerceTo(IntegerType.class);
                } catch (CoercionException e) {
                    throw new IllegalStateException("While coercing a NumericSet to a Set<IntegerType>", e);
                }
            }

            @Override
            public Iterator<IntegerType> iterator() {
                return new RangeIterator();
            }
        };
    }
}
