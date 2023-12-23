package tungsten.types;
/*
 * The MIT License
 *
 * Copyright © 2023 Robert Poole <Tarquin.AZ@gmail.com>.
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

import tungsten.types.exceptions.CoercionException;
import tungsten.types.numerics.IntegerType;
import tungsten.types.numerics.RealType;
import tungsten.types.numerics.Sign;
import tungsten.types.numerics.impl.IntegerImpl;
import tungsten.types.numerics.impl.RealImpl;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.util.*;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * A real-typed range that supports iteration.
 * The lower bound is always {@link tungsten.types.Range.BoundType#INCLUSIVE inclusive}, i.e. closed.
 * @since 0.3
 * @author Robert Poole, <a href="mailto:tarquin@alum.mit.edu">MIT alumni e-mail</a>
 */
public class SteppedRange extends Range<RealType> implements Iterable<RealType> {
    private final RealType stepSize;

    public SteppedRange(RealType start, RealType end, BoundType endType, RealType stepSize) {
        super(start, BoundType.INCLUSIVE, end, endType);
        if (stepSize.compareTo((RealType) end.subtract(start)) > 0) {
            throw new IllegalArgumentException("stepSize > span from start to end");
        } else if (stepSize.sign() != Sign.POSITIVE) {
            throw new IllegalArgumentException("stepSize must be > 0");
        }
        this.stepSize = stepSize;
    }

    @Override
    public Iterator<RealType> iterator() {
        return new Iterator<>() {
            private RealType current = getLowerBound();
            private final RealType threshold = isUpperClosed() ? getUpperBound() : computeThreshold();

            @Override
            public boolean hasNext() {
                return current.compareTo(threshold) <= 0;
            }

            @Override
            public RealType next() {
                if (!hasNext()) {
                    throw new NoSuchElementException("At or beyond end of range");
                }
                RealType result = current;
                current = (RealType) current.add(stepSize);
                return result;
            }
        };
    }

    private RealType computeThreshold() {
        RealType span = getUpperBound().subtract(getLowerBound()).magnitude();
        RealType fracSteps = (RealType) span.divide(stepSize);
        IntegerType steps = fracSteps.floor();
        try {
            RealType temp = (RealType) stepSize.multiply(steps).coerceTo(RealType.class);
            if (temp.equals(getUpperBound())) {  // upper bound is open here
                temp = (RealType) temp.subtract(stepSize).coerceTo(RealType.class);
            }
            return temp;
        } catch (CoercionException e) {
            Logger.getLogger(SteppedRange.class.getName()).log(Level.WARNING,
                    "Encountered a problem computing adjusted step size for a span of {0} with {1} whole steps",
                    new Object[] {span, steps});
            return getUpperBound();
        }
    }

    @Override
    public Spliterator<RealType> spliterator() {
        return new Spliterator<>() {
            private RealType current = getLowerBound();
            private final RealType threshold = isUpperClosed() ? getUpperBound() : computeThreshold();

            @Override
            public boolean tryAdvance(Consumer<? super RealType> consumer) {
                if (current.compareTo(threshold) > 0) return false;
                if (!isUpperClosed() && current.equals(threshold)) return false; // corner case
                consumer.accept(current);
                current = (RealType) current.add(stepSize);
                return true;
            }

            @Override
            public void forEachRemaining(Consumer<? super RealType> action) {
                final MathContext ctx = getLowerBound().getMathContext();
                final BigDecimal step = stepSize.asBigDecimal();
                final BigDecimal endstop = threshold.asBigDecimal();
                // by using a for loop here with BigDecimal, as opposed to a do/while loop or similar,
                // the hope is to get some optimizations from the compiler or runtime
                // note the use of BigDecimal.add() without the MathContext argument
                // we don't want or need to be spending extra time rounding the result at each step
                final boolean exact = current.isExact() && stepSize.isExact();  // compute only once
                for (BigDecimal iter = current.asBigDecimal(); iter.compareTo(endstop) <= 0; iter = iter.add(step)) {
                    RealType val = new RealImpl(iter, ctx, exact);
                    action.accept(val);
                }
                // and update current so that future calls to this Spliterator will fail
                current = getUpperBound();
            }

            @Override
            public Spliterator<RealType> trySplit() {
                final long elementCount = estimateSize();
                if (elementCount < 4L) return null;
                // we're going to chop the range in half
                IntegerType scale = new IntegerImpl(BigInteger.valueOf(elementCount >> 1L)) {
                    @Override
                    public MathContext getMathContext() {
                        return getLowerBound().getMathContext();
                    }
                };

                RealType newBase = (RealType) stepSize.multiply(scale).add(current);
                SteppedRange subrange = new SteppedRange(current, newBase, BoundType.EXCLUSIVE, stepSize);
                current = newBase;
                return subrange.spliterator();
            }

            @Override
            public long estimateSize() {
                RealType span = getUpperBound().subtract(current).magnitude();
                RealType rawCount = (RealType) span.divide(stepSize);
                // Since this Spliterator is SIZED, we need to provide an accurate count of elements,
                // at least initially. That means we need to check for fencepost errors.
                long fencepost = isUpperClosed() && rawCount.isCoercibleTo(IntegerType.class) ? 1L : 0L;
                // this is probably faster than doing ...floor().asBigInteger().longValueExact()
                return rawCount.asBigDecimal().longValue() + fencepost;
            }

            /**
             * Since this Spliterator uses the natural ordering of RealType,
             * it should return null.
             * @return always null
             */
            @Override
            public Comparator<? super RealType> getComparator() {
                return null;
            }

            @Override
            public int characteristics() {
                return SIZED | DISTINCT | IMMUTABLE | ORDERED | SORTED | SUBSIZED | NONNULL;
            }
        };
    }

    public Stream<RealType> parallelStream() {
        return StreamSupport.stream(this.spliterator(), true);
    }

    @Override
    public String toString() {
        return super.toString() + ", step\u2009=\u2009" + stepSize;
    }

    @Override
    public int hashCode() {
        return Objects.hash(getLowerBound(), getUpperBound(), stepSize);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof SteppedRange) {
            SteppedRange other = (SteppedRange) obj;
            if (!stepSize.equals(other.stepSize)) return false;
        }
        return super.equals(obj);  // compare bounds
    }
}
