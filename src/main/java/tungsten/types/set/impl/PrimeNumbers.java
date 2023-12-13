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

import tungsten.types.Set;
import tungsten.types.exceptions.CoercionException;
import tungsten.types.numerics.IntegerType;
import tungsten.types.numerics.Sign;
import tungsten.types.numerics.impl.IntegerImpl;
import tungsten.types.util.CombiningIterator;

import java.math.BigInteger;
import java.util.Iterator;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * A representation of the set of prime numbers.
 * @since 0.3
 * @author Robert Poole, <a href="mailto:tarquin@alum.mit.edu">MIT alumni e-mail</a>
 *   or <a href="mailto:Tarquin.AZ+Tungsten@gmail.com">Gmail</a>
 */
public class PrimeNumbers implements Set<IntegerType> {
    private final SortedSet<BigInteger> primes = new TreeSet<>();

    public PrimeNumbers() {
        primes.add(BigInteger.TWO);
        primes.add(BigInteger.valueOf(3L));
        primes.add(BigInteger.valueOf(5L));
        primes.add(BigInteger.valueOf(7L));
        primes.add(BigInteger.valueOf(11L));
        primes.add(BigInteger.valueOf(13L));
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
        final BigInteger val = element.asBigInteger();
        if (element.sign() != Sign.POSITIVE || val.equals(BigInteger.ONE)) return false;
        if (primes.contains(val)) return true;
        if (!val.isProbablePrime(3)) return false;
        return isPrime(element);
    }

    /**
     * Determine if the supplied value is a prime number or not.
     * For a number N, this check will entail at most &radic;N
     * division operations.
     * @param value the integer to be tested for primality
     * @return true if {@code value} is prime, false otherwise
     */
    public boolean isPrime(IntegerType value) {
        final BigInteger inner = value.asBigInteger();
        if (primes.contains(inner)) return true;
        if (primes.parallelStream().anyMatch(v -> inner.mod(v).equals(BigInteger.ZERO))) return false;
        // otherwise, do it the slow way
        BigInteger divisor = BigInteger.valueOf(17L); // we pre-cache up to 13
        final BigInteger limit = inner.sqrt();
        while (divisor.compareTo(limit) <= 0) {
            if (inner.mod(divisor).equals(BigInteger.ZERO)) return false;
            divisor = divisor.add(BigInteger.ONE);
        }
        primes.add(inner);
        return true;
    }

    @Override
    public void append(IntegerType element) {
        throw new UnsupportedOperationException("The set of prime numbers is immutable");
    }

    @Override
    public void remove(IntegerType element) {
        throw new UnsupportedOperationException("The set of prime numbers is immutable");
    }

    @Override
    public Set<IntegerType> union(Set<IntegerType> other) {
        if (other.cardinality() == 0L || other instanceof PrimeNumbers) return this;
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
                return PrimeNumbers.this.contains(element) || other.contains(element);
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
                return PrimeNumbers.this.union(other.union(other2));
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
                return PrimeNumbers.this.intersection(other2).union(other.intersection(other2));
            }

            @Override
            public Set<IntegerType> difference(Set<IntegerType> other2) {
                return PrimeNumbers.this.difference(other2).union(other.difference(other2));
            }

            @Override
            public Iterator<IntegerType> iterator() {
                return new CombiningIterator<>(PrimeNumbers.this.iterator(), other.iterator());
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
                return PrimeNumbers.this.intersection(other.intersection(other2));
            }

            @Override
            public Set<IntegerType> difference(Set<IntegerType> other2) {
                return PrimeNumbers.this.difference(other2).intersection(other.difference(other2));
            }

            @Override
            public Iterator<IntegerType> iterator() {
                return StreamSupport.stream(PrimeNumbers.this.spliterator(), false)
                        .filter(other::contains).iterator();
            }
        };
    }

    @Override
    public Set<IntegerType> difference(Set<IntegerType> other) {
        if (other instanceof PrimeNumbers) return EmptySet.getInstance();
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
        final IntegerType one = new IntegerImpl(BigInteger.ONE);

        return new Iterator<>() {
            private IntegerType current = new IntegerImpl(BigInteger.TWO);

            @Override
            public boolean hasNext() {
                return true;
            }

            @Override
            public IntegerType next() {
                while (!isPrime(current)) {
                    current = (IntegerType) current.add(one);
                }
                IntegerType result = current;
                // and advance to the next value
                if (current.asBigInteger().compareTo(primes.last()) >= 0) {
                    current = new IntegerImpl(current.asBigInteger().nextProbablePrime());
                } else {
                    current = (IntegerType) current.add(one);
                }
                return result;
            }
        };
    }

    @Override
    public int hashCode() {
        return 5 + 7 * primes.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof Set) {
            if (obj instanceof PrimeNumbers) return true;
            if (!((Set<?>) obj).isOfType(IntegerType.class)) return false;
            Set<IntegerType> that = (Set<IntegerType>) obj;
            return this.difference(that).cardinality() == 0L;
        }
        return false;
    }

    @Override
    public String toString() {
        return primes.stream().map(BigInteger::toString).collect(Collectors.joining(", ", "{", "\u2009\u2026}"));
    }
}
