package tungsten.types.set.impl;

import tungsten.types.Range;
import tungsten.types.Set;
import tungsten.types.exceptions.CoercionException;
import tungsten.types.numerics.IntegerType;
import tungsten.types.numerics.RationalType;
import tungsten.types.numerics.RealType;
import tungsten.types.numerics.impl.IntegerImpl;
import tungsten.types.numerics.impl.RationalImpl;
import tungsten.types.numerics.impl.RealImpl;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Iterator;
import java.util.Optional;
import java.util.TreeSet;
import java.util.stream.StreamSupport;

public class FibonacciNumbers implements Set<IntegerType> {
    private static final long MAX_N_TO_CACHE = 20L;
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
        if (cache.contains(element)) return true;

        Iterator<IntegerType> iter = this.iterator();
        while (iter.hasNext()) {
            IntegerType val = iter.next();
            if (val.equals(element)) return true;
            if (val.compareTo(element) > 0) break;
        }
        return false;
    }

    @Override
    public void append(IntegerType element) {
        throw new UnsupportedOperationException("The set of Fibonacci numbers is immutable.");
    }

    @Override
    public void remove(IntegerType element) {
        throw new UnsupportedOperationException("The set of Fibonacci numbers is immutable.");
    }

    @Override
    public Set<IntegerType> union(Set<IntegerType> other) {
        return null;
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
                Iterator<IntegerType> iter = this.iterator();
                while (iter.hasNext()) {
                    IntegerType val = iter.next();
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
            public Set<IntegerType> union(Set<IntegerType> other) {
                return null;
            }

            @Override
            public Set<IntegerType> intersection(Set<IntegerType> other) {
                return null;
            }

            @Override
            public Set<IntegerType> difference(Set<IntegerType> other) {
                return null;
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
        return null;
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
        if (n < 0L) throw new IndexOutOfBoundsException("Cannot negatively index into the Fibonacci sequence.");
        if (n == 0L) return ONE;
        // a set is a unique set of numbers, but the Fibonacci sequence starts with 1, 1... so we offset
        // our view into the cache by 1
        Optional<IntegerType> cached = cache.stream().skip(n - 1L).findFirst();
        return cached.orElseGet(() -> StreamSupport.stream(this.spliterator(), false).skip(n - 1L).findFirst().orElseThrow());
    }

    private static final Range<RealType> epsilonRange = new Range<>(new RealImpl(BigDecimal.ZERO),
            new RealImpl(BigDecimal.ONE), Range.BoundType.EXCLUSIVE);

    public RationalType getPhi(RealType epsilon) {
        if (!epsilonRange.contains(epsilon)) throw new IllegalArgumentException("Epsilon must be in range " + epsilonRange);
        RationalType prevPhi = new RationalImpl(ONE, ONE, epsilon.getMathContext());
        IntegerType prevElement = ONE;

        Iterator<IntegerType> iter = this.iterator();
        while (iter.hasNext()) {
            IntegerType currElement = iter.next();
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
}
