package tungsten.types;
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

import tungsten.types.exceptions.CoercionException;
import tungsten.types.numerics.RealType;
import tungsten.types.util.RangeUtils;

import java.util.Objects;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A class to represent a numeric range.  Any numeric type that can be
 * meaningfully compared can be used in a range.
 *
 * @author Robert Poole <a href="mailto:Tarquin.AZ@gmail.com">Tarquin.AZ@gmail.com</a>
 * @param <T> a class or interface that extends {@link Numeric} and {@link Comparable}
 */
public class Range<T extends Numeric & Comparable<? super T>> {
    public enum BoundType { INCLUSIVE, EXCLUSIVE }

    public class Bound implements Comparable<T> {
        private final BoundType type;
        private final T value;
        
        public Bound(T value, BoundType type) {
            this.value = value;
            this.type  = type;
        }

        @Override
        public int compareTo(T o) {
            return value.compareTo(o);
        }
        
        public boolean matchesLower(T o) {
            return switch (type) {
                case INCLUSIVE -> this.compareTo(o) <= 0;
                case EXCLUSIVE -> this.compareTo(o) < 0;
                default -> throw new IllegalStateException("Unknown bound of type " + type);
            };
        }
        
        public boolean matchesUpper(T o) {
            return switch (type) {
                case INCLUSIVE -> this.compareTo(o) >= 0;
                case EXCLUSIVE -> this.compareTo(o) > 0;
                default -> throw new IllegalStateException("Unknown bound of type " + type);
            };
        }
        
        public boolean isInclusive() { return type == BoundType.INCLUSIVE; }
        public T getValue() { return value; }

        @Override
        public int hashCode() {
            return Objects.hash(value, type);
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof Range<?>.Bound other) {
                return other.type == this.type && other.value.equals(this.value);
            }
            return false;
        }
    }
    
    private final Bound lowerBound, upperBound;
    
    /**
     * A convenience constructor which generates an instance where both
     * upper and lower bounds are of the same type.
     *
     * @param lowerVal the lower bound
     * @param upperVal the upper bound
     * @param type the desired bound type
     */
    public Range(T lowerVal, T upperVal, BoundType type) {
        this.lowerBound = new Bound(lowerVal, type);
        this.upperBound = new Bound(upperVal, type);
    }
    
    public Range(T lowerVal, BoundType lowerType, T upperVal, BoundType upperType) {
        this.lowerBound = new Bound(lowerVal, lowerType);
        this.upperBound = new Bound(upperVal, upperType);
    }
    
    public boolean contains(T val) {
        return lowerBound.matchesLower(val) && upperBound.matchesUpper(val);
    }

    public boolean contains(Range<T> range) {
        return lowerBound.matchesLower(range.getLowerBound()) && upperBound.matchesUpper(range.getUpperBound());
    }

    public boolean overlaps(Range<T> range) {
        return this.contains(range.getLowerBound()) || this.contains(range.getUpperBound());
    }

    public static Range<RealType> chooseNarrowest(Range<RealType> A, Range<RealType> B) {
        if (A.contains(B)) return B;
        if (B.contains(A)) return A;
        // if one does not contain the other, construct a range that is the intersection
        // providing that the two ranges are not completely disconnected
        if (A.overlaps(B)) {   // equivalent to B.overlaps(A)
            if (A.getClass().getSuperclass() == Range.class || B.getClass().getSuperclass() == Range.class) {
                Logger.getLogger(Range.class.getName()).log(Level.WARNING,
                        "chooseNarrowest() is being called with subclasses of Range " +
                                "that may lose information when computing the intersection " +
                                "of {0} ({1}) and {2} ({3})",
                        new Object[] { A, A.getClass().getTypeName(), B, B.getClass().getTypeName() });
            }
            RealType lowestBound = A.getLowerBound().compareTo(B.getLowerBound()) > 0 ?
                    A.getLowerBound() : B.getLowerBound();
            BoundType lowestBoundType = A.getLowerBound().equals(lowestBound) ?
                    A.lowerBound.type : B.lowerBound.type;
            RealType highestBound = A.getUpperBound().compareTo(B.getUpperBound()) < 0 ?
                    A.getUpperBound() : B.getUpperBound();
            BoundType highestBoundType = A.getUpperBound().equals(highestBound) ?
                    A.upperBound.type : B.upperBound.type;
            assert highestBound.compareTo(lowestBound) > 0;
            return new Range<>(lowestBound, lowestBoundType, highestBound, highestBoundType);
        } else {
            // no overlap, so construct the smallest NotchedRange possible that contains ranges A and B
            Range<RealType> first = A.getLowerBound().compareTo(B.getLowerBound()) < 0 ? A : B;
            Range<RealType> last = A.getUpperBound().compareTo(B.getUpperBound()) > 0 ? A : B;
            Range<RealType> between = RangeUtils.rangeBetween(first, last);
            if (between.getLowerBound().equals(between.getUpperBound())) {
                return new NotchedRange<>(first.getLowerBound(), first.lowerBound.type,
                        last.getUpperBound(), last.upperBound.type, between.getLowerBound());
            }
            Range<RealType> merged = RangeUtils.merge(first, last);
            return new NotchedRange<>(merged, RangeUtils.asRealSet(between));
        }
    }
    
    /**
     * Test whether the given value is below the lower bound of this range.
     * @param val the value to test
     * @return true if {@code val} is less than the lower bound
     */
    public boolean isBelow(T val) {
        return switch (lowerBound.type) {
            case INCLUSIVE -> val.compareTo(lowerBound.getValue()) < 0;
            case EXCLUSIVE -> val.compareTo(lowerBound.getValue()) <= 0;
            default -> throw new IllegalStateException("Unknown bound of type " + lowerBound.type);
        };
    }
    
    /**
     * Test whether the given value is above the upper bound of this range.
     * @param val the value to test
     * @return true if {@code val} is greater than the upper bound
     */
    public boolean isAbove(T val) {
        return switch (upperBound.type) {
            case INCLUSIVE -> val.compareTo(upperBound.getValue()) > 0;
            case EXCLUSIVE -> val.compareTo(upperBound.getValue()) >= 0;
            default -> throw new IllegalStateException("Unknown bound of type " + upperBound.type);
        };
    }
    
    public T getLowerBound() {
        return lowerBound.getValue();
    }
    
    public boolean isLowerClosed() {
        return lowerBound.isInclusive();
    }
    
    public T getUpperBound() {
        return upperBound.getValue();
    }
    
    public boolean isUpperClosed() {
        return upperBound.isInclusive();
    }

    public <R extends Numeric & Comparable<? super R>> Range<R> forNumericType(Class<R> target) {
        try {
            R lowerBound = (R) getLowerBound().coerceTo(target);
            R upperBound = (R) getUpperBound().coerceTo(target);
            return new Range<>(lowerBound, this.lowerBound.type, upperBound, this.upperBound.type);
        } catch (CoercionException e) {
            throw new IllegalArgumentException("Cannot convert Range to expected type", e);
        }
    }
    
    public Predicate<T> getPredicate() {
        return this::contains;
    }

    @Override
    public int hashCode() {
        return Objects.hash(lowerBound, upperBound);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof Range<?> other) {
            return lowerBound.equals(other.lowerBound) && upperBound.equals(other.upperBound);
        }
        return false;
    }

    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder();
        buf.append(lowerBound.isInclusive() ? '[' : '(');
        buf.append(lowerBound.getValue()).append(", ");
        buf.append(upperBound.getValue());
        buf.append(upperBound.isInclusive() ? ']' : ')');
        return buf.toString();
    }
}
