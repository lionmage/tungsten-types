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


import tungsten.types.numerics.IntegerType;
import tungsten.types.numerics.RealType;
import tungsten.types.set.impl.CoercionWrapperSet;
import tungsten.types.util.OptionalOperations;
import tungsten.types.util.RangeUtils;

import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.StreamSupport;

/**
 * An implementation of {@link Range} which maintains an internal {@link Set}
 * of values that are excluded from the range.  This is useful for function input ranges
 * that exclude a single value or a periodic set of values, e.g., 1/x has an input range
 * of &minus;&infin; &ndash; &infin; excluding 0.
 * @author Robert Poole, <a href="mailto:Tarquin.AZ+Tungsten@gmail.com">Gmail</a>
 *   or <a href="mailto:tarquin@alum.mit.edu">MIT alumni e-mail</a>
 * @param <T> a class or interface that extends {@link Numeric} and {@link Comparable}
 */
public class NotchedRange<T extends Numeric & Comparable<? super T>> extends Range<T> {
    private final Set<T> notches;

    @SafeVarargs
    public NotchedRange(T lowerVal, T upperVal, BoundType type, T... except) {
        super(lowerVal, upperVal, type);
        if (Arrays.stream(except).anyMatch(val -> !super.contains(val))) throw new IllegalArgumentException("Notch must be within bounds of range");
        notches = Set.of(except);
    }

    @SafeVarargs
    public NotchedRange(T lowerVal, BoundType lowerType, T upperVal, BoundType upperType, T... except) {
        super(lowerVal, lowerType, upperVal, upperType);
        if (Arrays.stream(except).anyMatch(val -> !super.contains(val))) throw new IllegalArgumentException("Notch must be within bounds of range");
        notches = Set.of(except);
    }

    /**
     * Construct a {@code NotchedRange} using an existing range, excluding elements of the
     * supplied {@code Set}.
     * @param source   the original range
     * @param excluded the elements to exclude
     */
    public NotchedRange(Range<T> source, Set<T> excluded) {
        super(source.getLowerBound(), source.isLowerClosed() ? BoundType.INCLUSIVE : BoundType.EXCLUSIVE,
                source.getUpperBound(), source.isUpperClosed() ? BoundType.INCLUSIVE : BoundType.EXCLUSIVE);
        if (excluded.countable() && excluded.cardinality() > 0L) {
            // if excluded is finite, check its elements to ensure they're within bounds
            if (!StreamSupport.stream(excluded.spliterator(), true).allMatch(source::contains)) {
                Logger.getLogger(NotchedRange.class.getName()).log(Level.SEVERE,
                        "Elements of set {0} do not fall within range {1}.",
                        new Object[] { excluded, source });
                throw new IllegalArgumentException("Exclusion set elements must be within range bounds");
            }
        }
        notches = excluded;
    }

    @Override
    public boolean contains(T val) {
        if (notches.contains(val)) return false;
        return super.contains(val);
    }

    @Override
    public boolean contains(Range<T> range) {
        if (notches.countable() && notches.cardinality() > 0L) {
            if (StreamSupport.stream(notches.spliterator(), true).anyMatch(range::contains)) return false;
        } else {
            Class<T> clazz = (Class<T>) OptionalOperations.findCommonType(getLowerBound().getClass(), getUpperBound().getClass());
            if (RealType.class.isAssignableFrom(clazz)) {
                Set<RealType> aggregate =  RangeUtils.asRealSet(range.forNumericType(RealType.class)).intersection(new CoercionWrapperSet<>(notches, RealType.class));
                if (aggregate.cardinality() != 0L) return false;
            } else if (IntegerType.class.isAssignableFrom(clazz)) {
                Set<IntegerType> aggregate = RangeUtils.asSet(range.forNumericType(IntegerType.class)).intersection(new CoercionWrapperSet<>(notches, IntegerType.class));
                if (aggregate.cardinality() != 0L) return false;
            }
            for (T notch : notches) {
                if (range.contains(notch)) return false;
            }
        }
        return super.contains(range);
    }

    /**
     * Obtain a set of notches, representing values that are exceptions
     * to this range.
     * @return the set of notches
     */
    public Set<T> getNotches() {
        return notches;
    }

    /**
     * Obtain a {@link Range} representing the bounds of this range without
     * the notches.
     * @return the inner range
     */
    public Range<T> getInnerRange() {
        BoundType lower = this.isLowerClosed() ? BoundType.INCLUSIVE : BoundType.EXCLUSIVE;
        BoundType upper = this.isUpperClosed() ? BoundType.INCLUSIVE : BoundType.EXCLUSIVE;
        return new Range<>(this.getLowerBound(), lower, this.getUpperBound(), upper);
    }

    @Override
    public String toString() {
        return super.toString() + " excluding " + notches;
    }
}
