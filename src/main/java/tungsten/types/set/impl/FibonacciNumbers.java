package tungsten.types.set.impl;
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

import tungsten.types.Numeric;
import tungsten.types.Range;
import tungsten.types.Set;
import tungsten.types.exceptions.CoercionException;
import tungsten.types.numerics.IntegerType;
import tungsten.types.numerics.RationalType;
import tungsten.types.numerics.RealType;
import tungsten.types.numerics.Sign;
import tungsten.types.numerics.impl.IntegerImpl;
import tungsten.types.numerics.impl.Phi;
import tungsten.types.numerics.impl.RationalImpl;
import tungsten.types.numerics.impl.RealImpl;
import tungsten.types.util.CombiningIterator;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Iterator;
import java.util.Optional;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * A representation of the set of numbers generated by the Fibonacci sequence &#x1D5D9;.
 * Note that this set does not contain zero (0) as the algorithm starts computation
 * with 1, 1&hellip;
 * <br/> The first {@link #MAX_N_TO_CACHE} generated values are cached for performance.
 * @author Robert Poole, <a href="mailto:tarquin@alum.mit.edu">MIT alumni e-mail</a>
 *  or <a href="mailto:Tarquin.AZ@gmail.com">Gmail</a>
 */
public class FibonacciNumbers implements Set<IntegerType> {
    /**
     * A {@link String} representing a system property that governs a threshold limit
     * for &epsilon; values used to compute &#x03D5;.  When invoking {@link #getPhi(RealType)},
     * the &epsilon; argument is compared to this limit (if specified), and if &epsilon;
     * is less than the limit, &#x03D5; is computed directly.
     */
    public static final String EPSILON_LIMIT = "tungsten.types.set.impl.FibonacciNumbers.epsilonLimit";
    /**
     * The maximum number of Fibonacci values to cache.
     */
    public static final long MAX_N_TO_CACHE = 20L;
    private static final IntegerType ONE = new IntegerImpl(BigInteger.ONE);
    private final TreeSet<IntegerType> cache = new TreeSet<>();

    public FibonacciNumbers() {
        cache.add(ONE);  // f1, f0
    }

    @Override
    public long cardinality() {
        return -1;
    }

    @Override
    public boolean countable() {
        return true;
    }

    @Override
    public boolean contains(IntegerType element) {
        if (element.sign() == Sign.NEGATIVE) return false;
        if (cache.contains(element)) return true;

        for (IntegerType val : this) {
            if (val.equals(element)) return true;
            if (val.compareTo(element) > 0) break;
        }
        return false;
    }

    @Override
    public void append(IntegerType element) {
        throw new UnsupportedOperationException("The set of Fibonacci numbers is immutable");
    }

    @Override
    public void remove(IntegerType element) {
        throw new UnsupportedOperationException("The set of Fibonacci numbers is immutable");
    }

    @Override
    public Set<IntegerType> union(Set<IntegerType> other) {
        if (other.cardinality() == 0L || other instanceof FibonacciNumbers) return this;
        // general case uses a combining iterator
        return new Set<>() {
            @Override
            public long cardinality() {
                return -1;
            }

            @Override
            public boolean countable() {
                return true;
            }

            @Override
            public boolean contains(IntegerType element) {
                return FibonacciNumbers.this.contains(element) || other.contains(element);
            }

            @Override
            public void append(IntegerType element) {
                throw new UnsupportedOperationException("Append is not supported");
            }

            @Override
            public void remove(IntegerType element) {
                throw new UnsupportedOperationException("Remove is not supported");
            }

            @Override
            public Set<IntegerType> union(Set<IntegerType> other2) {
                return FibonacciNumbers.this.union(other.union(other2));
            }

            @Override
            public Set<IntegerType> intersection(Set<IntegerType> other2) {
                if (other2.cardinality() == 0L) return EmptySet.getInstance();
                if (other2.cardinality() > 0L) {
                    NumericSet intersection = new NumericSet();
                    StreamSupport.stream(other2.spliterator(), true).filter(this::contains).forEach(intersection::append);
                    if (intersection.cardinality() == 0L) return EmptySet.getInstance();
                    try {
                        return intersection.coerceTo(IntegerType.class);
                    } catch (CoercionException e) {
                        throw new IllegalStateException(e);
                    }
                }
                // (A ∪ B) ∩ C = (A ∩ C) ∪ (B ∩ C)
                return FibonacciNumbers.this.intersection(other2).union(other.intersection(other2));
            }

            @Override
            public Set<IntegerType> difference(Set<IntegerType> other2) {
                return FibonacciNumbers.this.difference(other2).union(other.difference(other2));
            }

            @Override
            public Iterator<IntegerType> iterator() {
                return new CombiningIterator<>(FibonacciNumbers.this.iterator(), other.iterator());
            }
        };
    }

    @Override
    public Set<IntegerType> intersection(Set<IntegerType> other) {
        if (other.countable() && other.cardinality() >= 0L) {
            if (other.cardinality() == 0L) return EmptySet.getInstance();
            NumericSet intersection = new NumericSet();
            StreamSupport.stream(other.spliterator(), true).filter(this::contains).forEach(intersection::append);
            if (intersection.cardinality() == 0L) return EmptySet.getInstance();
            try {
                return intersection.coerceTo(IntegerType.class);
            } catch (CoercionException e) {
                throw new IllegalStateException(e);
            }
        }
        // otherwise, construct a set that satisfies the constraints
        return new Set<>() {
            @Override
            public long cardinality() {
                return -1;
            }

            @Override
            public boolean countable() {
                return true;
            }

            @Override
            public boolean contains(IntegerType element) {
                for (IntegerType val : this) {
                    if (val.equals(element)) return true;
                    if (val.compareTo(element) > 0) break;
                }
                return false;
            }

            @Override
            public void append(IntegerType element) {
                throw new UnsupportedOperationException("Append is not supported");
            }

            @Override
            public void remove(IntegerType element) {
                throw new UnsupportedOperationException("Remove is not supported");
            }

            @Override
            public Set<IntegerType> union(Set<IntegerType> other2) {
                return new UnionSet<>(this, other2);
            }

            @Override
            public Set<IntegerType> intersection(Set<IntegerType> other2) {
                return FibonacciNumbers.this.intersection(other.intersection(other2));
            }

            @Override
            public Set<IntegerType> difference(Set<IntegerType> other2) {
                return FibonacciNumbers.this.difference(other2).intersection(other.difference(other2));
            }

            @Override
            public Iterator<IntegerType> iterator() {
                return StreamSupport.stream(FibonacciNumbers.this.spliterator(), false)
                        .filter(other::contains).iterator();
            }
        };
    }

    @Override
    public Set<IntegerType> difference(Set<IntegerType> other) {
        if (other instanceof FibonacciNumbers) return EmptySet.getInstance();
        if (other.cardinality() == 0L) return this;
        // if we're not in some kind of corner case, build a Set representation
        final Set<IntegerType> container = this;
        return new Set<>() {
            @Override
            public long cardinality() {
                return -1;
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
                throw new UnsupportedOperationException("This set is immutable");
            }

            @Override
            public void remove(IntegerType element) {
                throw new UnsupportedOperationException("This set is immutable");
            }

            @Override
            public Set<IntegerType> union(Set<IntegerType> other2) {
                return new UnionSet<>(this, other2);
            }

            @Override
            public Set<IntegerType> intersection(Set<IntegerType> other2) {
                if (other2.countable() && other2.cardinality() >= 0L) {
                    if (other2.cardinality() == 0L) return EmptySet.getInstance();
                    NumericSet intersection = new NumericSet();
                    StreamSupport.stream(other2.spliterator(), true).filter(this::contains).forEach(intersection::append);
                    if (intersection.cardinality() == 0L) return EmptySet.getInstance();
                    try {
                        return intersection.coerceTo(IntegerType.class);
                    } catch (CoercionException e) {
                        throw new IllegalStateException(e);
                    }
                }
                // use an identity for the general case
                // (A - B) ∩ C = A ∩ (B - C)
                return container.intersection(other.difference(other2));
            }

            @Override
            public Set<IntegerType> difference(Set<IntegerType> other2) {
                return container.difference(other.union(other2));
            }

            @Override
            public Iterator<IntegerType> iterator() {
                return StreamSupport.stream(container.spliterator(), false).dropWhile(other::contains).iterator();
            }
        };
    }

    @Override
    public Iterator<IntegerType> iterator() {
        return new Iterator<>() {
            IntegerType f0 = ONE;
            IntegerType f1 = ONE;
            long index = 0L;

            @Override
            public boolean hasNext() {
                return true;
            }

            @Override
            public IntegerType next() {
                IntegerType f2 = (IntegerType) f0.add(f1);
                if (index++ < MAX_N_TO_CACHE) cache.add(f2);
                f0 = f1;
                f1 = f2;
                return f0;
            }
        };
    }

    /**
     * Obtain the n<sup>th</sup> number in the Fibonacci sequence.
     * Note that the numbering is zero-based, i.e., the first Fibonacci number
     * has an index of 0.
     *
     * @param n the 0-based index of the desired Fibonacci number
     * @return the n<sup>th</sup> element of the Fibonacci sequence
     */
    public IntegerType getNthFibonacciNumber(long n) {
        if (n < 0L) throw new IndexOutOfBoundsException("Cannot negatively index into the Fibonacci sequence");
        if (n == 0L) return ONE;
        // a set is a unique set of numbers, but the Fibonacci sequence starts with 1, 1... so we offset
        // our view into the cache by 1
        Optional<IntegerType> cached = cache.stream().skip(n - 1L).findFirst();
        return cached.orElseGet(() -> StreamSupport.stream(this.spliterator(), false).skip(n - 1L).findFirst().orElseThrow());
    }

    private static final Range<RealType> epsilonRange = new Range<>(new RealImpl(BigDecimal.ZERO),
            new RealImpl(BigDecimal.ONE), Range.BoundType.EXCLUSIVE);

    /**
     * Obtain an approximate value for &#x03D5; by computing successive values of the Fibonacci sequence
     * until the difference in successive approximations is &lt; {@code epsilon}.  If the
     * {@link System#getProperty(String) System property} {@code tungsten.types.set.impl.FibonacciNumbers.epsilonLimit}
     * is available and is set to a value 0 &lt; &epsilon;<sub>0</sub> &#x226A; 1, then if
     * &epsilon; &lt; &epsilon;<sub>0</sub>, this iteration is bypassed and the value of &#x03D5;
     * is derived directly from {@link Phi}.
     *
     * @param epsilon the maximum allowable delta in approximations to &#x03D5;
     * @return a rational approximation to &#x03D5; with an accuracy determined by epsilon, or a real
     *  approximation of &#x03D5; if enabled by a threshold set by a special {@link #EPSILON_LIMIT System property}
     * @see #EPSILON_LIMIT
     */
    public Numeric getPhi(RealType epsilon) {
        if (!epsilonRange.contains(epsilon)) throw new IllegalArgumentException("Epsilon must be in range " + epsilonRange);
        String epsiLimit = System.getProperty(EPSILON_LIMIT);
        if (epsiLimit != null) {
            RealType limit = new RealImpl(epsiLimit);
            if (!epsilonRange.contains(limit)) throw new IllegalStateException("Bad configuration value " + limit + " for " + EPSILON_LIMIT);
            if (epsilon.compareTo(limit) < 0) return Phi.getInstance(epsilon.getMathContext());
        }
        RationalType prevPhi = new RationalImpl(ONE, ONE, epsilon.getMathContext());
        IntegerType prevElement = ONE;

        for (IntegerType currElement : this) {
            RationalType currPhi = new RationalImpl(currElement, prevElement, epsilon.getMathContext());
            BigDecimal delta = currPhi.asBigDecimal().subtract(prevPhi.asBigDecimal()).abs();
            if (delta.compareTo(epsilon.asBigDecimal()) < 0) {
                return currPhi;
            }
            prevPhi = currPhi;
            prevElement = currElement;
        }
        throw new IllegalStateException("We should never have gotten here");
    }

    @Override
    public String toString() {
        return "\uD835\uDDD9 \u2243 {" +
                cache.stream().map(Object::toString).collect(Collectors.joining(", ")) +
                "\u2009\u2026}";
    }
}
