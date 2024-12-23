/*
 * The MIT License
 *
 * Copyright © 2024 Robert Poole <Tarquin.AZ@gmail.com>.
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
 *
 */

package tungsten.types.numerics.impl;

import tungsten.types.Numeric;
import tungsten.types.Set;
import tungsten.types.exceptions.CoercionException;
import tungsten.types.numerics.*;
import tungsten.types.util.GosperTermIterator;
import tungsten.types.util.RationalCFTermAdapter;
import tungsten.types.util.UnicodeTextEffects;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.util.*;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A continued fraction representation of a real number.
 * This particular implementation uses the conventions of Simple Continued Fractions
 * (i.e., all numerators are 1).  Each term is stored as a {@code long}, giving 64-bit
 * signed integer precision for each term calculation.  It is tempting to use
 * {@code BigInteger} instead (as is used for other {@code Numeric} implementations),
 * but the <a href="https://en.m.wikipedia.org/wiki/Gauss%E2%80%93Kuzmin_distribution">Gauss-Kuzmin</a>
 * distribution suggests that the probability we'd need a bigger representation for any given term is low.
 * @author Robert Poole, <a href="mailto:tarquin@alum.mit.edu">MIT alumni e-mail</a>
 * @see <a href="https://r-knott.surrey.ac.uk/Fibonacci/cfINTRO.html">Continued Fractions &mdash; An introduction</a>
 * @see <a href="https://cp-algorithms.com/algebra/continued-fractions.html">An article at Algorithms
 *   for Competitive Programming</a>
 */
public class ContinuedFraction implements RealType, Iterable<Long> {
    private final long[] terms;
    private int repeatsFromIndex = -1;
    /**
     * For infinite continued fractions, takes
     * an index value and produces the value of
     * the term at that index.
     */
    private Function<Long, Long> mappingFunc;
    private MathContext mctx = MathContext.UNLIMITED;

    public ContinuedFraction(IntegerType num) {
        terms = new long[1];
        terms[0] = num.asBigInteger().longValueExact();
        mctx = num.getMathContext();
    }

    public ContinuedFraction(RealType num) {
        if (num instanceof ContinuedFraction) {
            // we just want to copy directly
            ContinuedFraction that = (ContinuedFraction) num;
            this.terms = that.terms;
            this.repeatsFromIndex = that.repeatsFromIndex;
            this.mappingFunc = that.mappingFunc;
        } else {
            BigDecimal termValue = num.asBigDecimal();
            final int precision = num.getMathContext().getPrecision() == 0 ?
                    termValue.precision() : num.getMathContext().getPrecision();
            final MathContext ctx = new MathContext(precision, num.getMathContext().getRoundingMode());
            final BigDecimal epsilon = BigDecimal.TEN.pow(1 - precision, ctx);
            BigDecimal frac;
            List<Long> terms = new LinkedList<>();

            do {
                long current = floor(termValue);
                terms.add(current);
                frac = termValue.subtract(BigDecimal.valueOf(current));
                termValue = BigDecimal.ONE.divide(frac, ctx);  // inverse of fractional part
            } while (frac.compareTo(epsilon) > 0);

            this.terms = terms.stream().mapToLong(Long::longValue).toArray();
        }
        this.mctx = num.getMathContext();
    }

    public ContinuedFraction(Long... terms) {
        this.terms = Arrays.stream(terms).mapToLong(Long::longValue).toArray();
    }

    public ContinuedFraction(List<Long> terms) {
        this.terms = terms.stream().mapToLong(Long::longValue).toArray();
    }

    /**
     * Constructs a continued fraction from an {@code Iterator} which
     * returns a sequence of terms.
     * @param lterms    an iterator which returns terms in sequential order
     * @param cacheSize the maximum number of terms to store internally
     */
    public ContinuedFraction(Iterator<Long> lterms, int cacheSize) {
        if (cacheSize < 1) throw new IllegalArgumentException("Cache size must be a positive integer");
        long[] tempTerms = new long[cacheSize];
        int k = 0;
        while (k < cacheSize && lterms.hasNext()) tempTerms[k++] = lterms.next();
        if (k < cacheSize) {
            this.terms = Arrays.copyOf(tempTerms, k);
        } else {
            this.terms = tempTerms;
        }

        if (lterms.hasNext()) {
            // there are more terms in the iterator, so provide a function to access them
            final long boundary = k;
            this.mappingFunc = new Function<>() {
                final List<Long> cache = new LinkedList<>();

                @Override
                public Long apply(Long index) {
                    if (index == null) throw new IllegalArgumentException("Index must not be null");
                    if (index - boundary < 0L) throw new IndexOutOfBoundsException("Index " + index + " < " + boundary);
                    Long val = 0L;
                    if (index - boundary < (long) cache.size()) {
                        val =  cache.get((int) (index - boundary));
                    } else {
                        Logger.getLogger(ContinuedFraction.class.getName()).log(Level.INFO,
                                "Can't find term {0} in cache; iterating over {1} terms and caching.",
                                new Object[] { index, index - boundary - cache.size() });
                        for (long kk = cache.size() + boundary; kk <= index && kk <= (long) Integer.MAX_VALUE; kk++) {
                            val = lterms.next();
                            if (val != null) cache.add(val);
                            else break;
                        }
                    }
                    return val;
                }
            };
        }
    }

    private long floor(BigDecimal value) {
        long whole = value.toBigInteger().longValueExact();
        if (value.signum() == -1 && value.stripTrailingZeros().scale() > 0) {
            // negative values that are non-integers need to be decremented
            whole = Math.decrementExact(whole);
        }
        return whole;
    }

    protected ContinuedFraction(long[] terms, int repeatsFromIndex, Function<Long, Long> mappingFunc) {
        long[] cleanedTerms = annealZeros(terms);
        if (!Arrays.equals(terms, cleanedTerms)) {
            int zeroIndex = findValue(terms, 0L);
            // 3 terms being combined into 1
            if (repeatsFromIndex > zeroIndex) {
                repeatsFromIndex -= 2;
            }
            if (mappingFunc != null) {
                mappingFunc = mappingFunc.compose(n -> n + 2L);
            }
        }
        this.terms = cleanedTerms;
        this.repeatsFromIndex = repeatsFromIndex;
        this.mappingFunc = mappingFunc;
    }

    private long[] annealZeros(long[] source) {
        if (source.length == 1) return source;

        ArrayList<Long> termList = new ArrayList<>();
        termList.add(source[0]);
        for (int k = 1; k < source.length; k++) {
            if (source[k] == 0L) {
                long a = source[k - 1];
                long b = source[++k];
                termList.remove(termList.size() - 1);
                termList.add(a + b);
            } else {
                termList.add(source[k]);
            }
        }
        return termList.stream().mapToLong(Long::longValue).toArray();
    }

    private int findValue(long[] values, long value) {
        int k = 1;
        while (values[k] != value) {
            k++;
            if (k == values.length) {
                Logger.getLogger(ContinuedFraction.class.getName()).log(Level.SEVERE,
                        "Expected to find {0} in term array {1} but did not.",
                        new Object[] { value, values });
                throw new NoSuchElementException("Could not find term " + value);
            }
        }
        return k;
    }

    /**
     * Given an index, return the term at that index.
     * @param index the 0-based index of the desired term
     * @return the term located at {@code index}
     */
    public long termAt(long index) {
        if (index < 0L) throw new IndexOutOfBoundsException("Negative indices are not supported");
        if (index < terms.length) return terms[(int) index];
        if (repeatsFromIndex >= 0) {
            long period = terms.length - repeatsFromIndex;
            return terms[(int) ((index - repeatsFromIndex) % period + repeatsFromIndex)];
        }
        if (mappingFunc != null) {
            Long mapping = mappingFunc.apply(index);
            return mapping == null ? 0L : mapping;
        }
        throw new IndexOutOfBoundsException("No term present at " + index);
    }

    /**
     * Return the number of terms in this continued fraction, if known.
     * @return the number of terms, or -1 if the number is indeterminate
     */
    public long terms() {
        if (repeatsFromIndex >= 0 || mappingFunc != null) return -1L;
        return terms.length;
    }

    @Override
    public boolean isIrrational() {
        return repeatsFromIndex >= 0 || mappingFunc != null;
    }

    @Override
    public RealType magnitude() {
        return new RealImpl(asBigDecimal().abs(), mctx);
    }

    @Override
    public boolean isExact() {
        return mappingFunc == null;
    }

    @Override
    public boolean isCoercibleTo(Class<? extends Numeric> numtype) {
        if (numtype == Numeric.class) return true;
        NumericHierarchy htype = NumericHierarchy.forNumericType(numtype);
        switch (htype) {
            case INTEGER:
                return terms() == 1L;
            case COMPLEX:
            case REAL:
            case RATIONAL:
                return true;
            default:
                return false;
        }
    }

    @Override
    public Numeric coerceTo(Class<? extends Numeric> numtype) throws CoercionException {
        if (numtype == Numeric.class) {
            if (terms() == 1L) {
                if (terms[0] == 0L) return ExactZero.getInstance(mctx);
                if (terms[0] == 1L) return One.getInstance(mctx);
            }
            return this;
        }
        NumericHierarchy htype = NumericHierarchy.forNumericType(numtype);
        switch (htype) {
            case COMPLEX:
                return new ComplexRectImpl(this);
            case REAL:
                return this;
            case RATIONAL:
                long lastTerm = terms() < 0L ? mctx.getPrecision() : terms() - 1L;
                return computeRationalValue(0L, lastTerm);
            case INTEGER:
                if (terms() == 1L) return new IntegerImpl(BigInteger.valueOf(terms[0]));
                throw new CoercionException("Continued fraction is not an integer", this.getClass(), numtype);
            default:
                throw new CoercionException("Unsupported coercion", this.getClass(), numtype);
        }
    }

    private RationalType computeRationalValue(long k, long limit) {
        RationalType a_k = new RationalImpl(termAt(k), 1L, mctx);
        if (k == limit) return a_k;
        Numeric nextTerm = computeRationalValue(k + 1L, limit).inverse();
        try {
            return (RationalType) a_k.add(nextTerm).coerceTo(RationalType.class);
        } catch (CoercionException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public RealType negate() {
        if (terms() == 1L) {
            ContinuedFraction singleTerm = new ContinuedFraction(-terms[0]);
            singleTerm.setMathContext(mctx);
            return singleTerm;
        }

        long[] negValue = new long[terms.length + 1];
        negValue[0] = -terms[0] - 1L;
        negValue[1] = 1L;
        negValue[2] = terms[1] - 1L;
        System.arraycopy(terms, 2, negValue, 3, terms.length - 2);

        int startRepeat = repeatsFromIndex < 0 ? -1 : repeatsFromIndex + 1;
        Function<Long, Long> indexMapper = null;
        if (mappingFunc != null) {
            indexMapper = mappingFunc.compose(n -> n - 1L);
        }

        ContinuedFraction cf = new ContinuedFraction(negValue, startRepeat, indexMapper);
        cf.setMathContext(mctx);
        return cf;
    }

    @Override
    public Numeric add(Numeric addend) {
        if (addend.isCoercibleTo(IntegerType.class)) {
            try {
                IntegerType value = (IntegerType) addend.coerceTo(IntegerType.class);
                long primAddend = value.asBigInteger().longValueExact();
                long[] sum = new long[terms.length];
                sum[0] = terms[0] + primAddend;
                System.arraycopy(terms, 1, sum, 1, terms.length - 1);
                ContinuedFraction result = new ContinuedFraction(sum, repeatsFromIndex, mappingFunc);
                result.setMathContext(mctx);
                return result;
            } catch (CoercionException e) {
                throw new ArithmeticException("While adding an integer value: " + e.getMessage());
            }
        } else if (addend.isCoercibleTo(RationalType.class)) {
            try {
                RationalType rational = (RationalType) addend.coerceTo(RationalType.class);
                Iterator<Long> result = GosperTermIterator.add(this.iterator(), new RationalCFTermAdapter(rational));
                ContinuedFraction sum = new ContinuedFraction(result, 10);
                sum.setMathContext(mctx);
                return sum;
            } catch (CoercionException e) {
                throw new ArithmeticException("While adding a rational value: " + e.getMessage());
            }
        }

        if (addend instanceof ContinuedFraction) {
            ContinuedFraction rhs = (ContinuedFraction) addend;
            Iterator<Long> result = GosperTermIterator.add(this.iterator(), rhs.iterator());
            ContinuedFraction sum = new ContinuedFraction(result, 10);
            sum.setMathContext(mctx);
            return sum;
        }

        return addend.add(this);
    }

    @Override
    public Numeric subtract(Numeric subtrahend) {
        if (subtrahend.isCoercibleTo(IntegerType.class)) {
            try {
                IntegerType value = (IntegerType) subtrahend.coerceTo(IntegerType.class);
                long primSubrahend = value.asBigInteger().longValueExact();
                long[] diff = new long[terms.length];
                diff[0] = terms[0] - primSubrahend;
                System.arraycopy(terms, 1, diff, 1, terms.length - 1);
                ContinuedFraction result = new ContinuedFraction(diff, repeatsFromIndex, mappingFunc);
                result.setMathContext(mctx);
                return result;
            } catch (CoercionException e) {
                throw new ArithmeticException("While subtracting an integer value: " + e.getMessage());
            }
        }  else if (subtrahend.isCoercibleTo(RationalType.class)) {
            try {
                RationalType rational = (RationalType) subtrahend.coerceTo(RationalType.class);
                Iterator<Long> result = GosperTermIterator.subtract(this.iterator(), new RationalCFTermAdapter(rational));
                ContinuedFraction diff = new ContinuedFraction(result, 10);
                diff.setMathContext(mctx);
                return diff;
            } catch (CoercionException e) {
                throw new ArithmeticException("While subtracting a rational value: " + e.getMessage());
            }
        }

        if (subtrahend instanceof ContinuedFraction) {
            ContinuedFraction rhs = (ContinuedFraction) subtrahend;
            Iterator<Long> result = GosperTermIterator.subtract(this.iterator(), rhs.iterator());
            ContinuedFraction difference = new ContinuedFraction(result, 10);
            difference.setMathContext(mctx);
            return difference;
        }

        return subtrahend.negate().add(this);
    }

    @Override
    public Numeric multiply(Numeric multiplier) {
        if (multiplier instanceof ContinuedFraction) {
            ContinuedFraction rhs = (ContinuedFraction) multiplier;
            Iterator<Long> result = GosperTermIterator.multiply(this.iterator(), rhs.iterator());
            ContinuedFraction product = new ContinuedFraction(result, 10);
            product.setMathContext(mctx);
            return product;
        } else if (multiplier.isCoercibleTo(RationalType.class)) {
            try {
                RationalType rational = (RationalType) multiplier.coerceTo(RationalType.class);
                Iterator<Long> result = GosperTermIterator.multiply(this.iterator(), new RationalCFTermAdapter(rational));
                ContinuedFraction prod = new ContinuedFraction(result, 10);
                prod.setMathContext(mctx);
                return prod;
            } catch (CoercionException e) {
                throw new ArithmeticException("While multipling a rational value: " + e.getMessage());
            }
        }

        return multiplier.multiply(this);
    }

    @Override
    public Numeric divide(Numeric divisor) {
        if (divisor instanceof ContinuedFraction) {
            ContinuedFraction rhs = (ContinuedFraction) divisor;
            Iterator<Long> result = GosperTermIterator.divide(this.iterator(), rhs.iterator());
            ContinuedFraction dividend = new ContinuedFraction(result, 10);
            dividend.setMathContext(mctx);
            return dividend;
        } else if (divisor.isCoercibleTo(RationalType.class)) {
            try {
                RationalType rational = (RationalType) divisor.coerceTo(RationalType.class);
                Iterator<Long> result = GosperTermIterator.divide(this.iterator(), new RationalCFTermAdapter(rational));
                ContinuedFraction dividend = new ContinuedFraction(result, 10);
                dividend.setMathContext(mctx);
                return dividend;
            } catch (CoercionException e) {
                throw new ArithmeticException("While dividing by a rational value: " + e.getMessage());
            }
        }

        return divisor.inverse().multiply(this);
    }

    @Override
    public Numeric inverse() {
        ContinuedFraction result;
        if (terms[0] == 0L) {
            long[] invTerms = Arrays.copyOfRange(terms, 1, terms.length);
            int cycleStart = repeatsFromIndex < 0 ? -1 : repeatsFromIndex - 1;
            Function<Long, Long> nuMapper = null;
            if (mappingFunc != null) {
                nuMapper = mappingFunc.compose(n -> n + 1L);
            }
            result = new ContinuedFraction(invTerms, cycleStart, nuMapper);
        } else {
            // insert a 0
            long[] invTerms = new long[terms.length + 1];
            invTerms[0] = 0L;
            System.arraycopy(terms, 0, invTerms, 1, terms.length);
            int cycleStart = repeatsFromIndex < 0 ? -1 : repeatsFromIndex + 1;
            Function<Long, Long> nuMapper = null;
            if (mappingFunc != null) {
                nuMapper = mappingFunc.compose(n -> n - 1L);
            }
            result = new ContinuedFraction(invTerms, cycleStart, nuMapper);
        }
        result.setMathContext(mctx);
        return result;
    }

    @Override
    public Numeric sqrt() {
        if (sign() == Sign.NEGATIVE) return new RealImpl(asBigDecimal(), mctx).sqrt();

        long guess = sqrt(terms[0]);  // a₀
        if (terms() == 1L) {
            if (guess * guess == terms[0]) {
                // if it's a perfect square, we're done
                ContinuedFraction cf = new ContinuedFraction(guess);
                cf.setMathContext(mctx);
                return cf;
            }
            // otherwise, expand the fraction
            // The below liberally adapted from https://math.stackexchange.com/questions/265690/continued-fraction-of-a-square-root
            List<Long> nuterms = new ArrayList<>();
            long ak;
            long rk = 0L;  // r₀
            long sk = 1L;  // s₀
            do {
                ak = (rk + guess) / sk; // aₖ = (rₖ + a₀) / sₖ
                rk = ak * sk - rk;  // rₖ₊₁ = aₖsₖ - rₖ
                sk = (terms[0] - rk * rk) / sk;  // sₖ₊₁ = (N - rₖ₊₁²) / sₖ
                nuterms.add(ak);
            } while (ak != 2L * guess);  // repeating sequence terminates with 2a₀
            long[] a = nuterms.stream().mapToLong(Long::longValue).toArray();
            ContinuedFraction result = new ContinuedFraction(a, findPalindromeStart(a), null);
            result.setMathContext(mctx);
            return result;
        }
        // use Newton's method iterations
        ContinuedFraction prev = new ContinuedFraction(guess);  // initial guess
        prev.setMathContext(mctx);
        final ContinuedFraction half = new ContinuedFraction(0L, 2L);
        half.setMathContext(mctx);
        final int bailout = mctx.getPrecision() / 2 + 1;  // a total hack
        for (int iter = 0; iter < bailout; iter++) {
            Iterator<Long> quotient = GosperTermIterator.divide(this.iterator(), prev.iterator());
            Iterator<Long> sum = GosperTermIterator.add(prev.iterator(), quotient);
            Iterator<Long> avg = GosperTermIterator.multiply(half.iterator(), sum);
            prev = new ContinuedFraction(avg, iter + 1);
        }
        prev.setMathContext(mctx);
        return prev; // was: new RealImpl(asBigDecimal(), mctx).sqrt();
    }

    private int findPalindromeStart(long[] arr) {
        if (arr.length < 2) return -1;

        for (int k = 1; k < arr.length - 2; k++) {
            long[] subarr = Arrays.copyOfRange(arr, k, arr.length - 1);
            if (isPalindrome(subarr)) return k;
        }
        return arr.length - 1;
    }

    private boolean isPalindrome(long[] candidate) {
        for (int i = 0; i <= candidate.length / 2; i++) {
            if (candidate[i] != candidate[candidate.length - i - 1]) return false;
        }

        return true;
    }

    private long sqrt(long val) {
        int bitLength = 63 - Long.numberOfLeadingZeros(val);
        long div = 1L << (bitLength / 2);
        long div2 = div;

        while (true) {
            long y = (div + (val / div)) >> 1L;
            if (y == div || y == div2) {
                long lowest = Math.min(div, div2);
                return closestWithoutGoingOver(lowest, y, val);
            }
            div2 = div;
            div = y;
        }
    }

    private long closestWithoutGoingOver(long a, long b, long valSq) {
        final long asquared = a * a;
        if (asquared > valSq) return b;
        final long bsquared = b * b;
        if (bsquared > valSq) return a;
        return (valSq - asquared) > (valSq - bsquared) ? b : a;
    }

    /**
     * Compute {@code this}<sup>n</sup>, where n is an integer.
     * Note: the caller should supply the {@code MathContext} for the result, if needed.
     * @param n the integral exponent
     * @return this continued fraction raised to the {@code n}<sup>th</sup> power
     */
    public ContinuedFraction pow(long n) {
        if (n == -1L) return (ContinuedFraction) this.inverse();
        if (n == 0L) return new ContinuedFraction(1L);
        if (n < 0L) return (ContinuedFraction) pow(-n).inverse();
        if (n == 1L) return this;
        // from this point, n is guaranteed > 1
        Iterator<Long> oddTerm = n % 2L == 1L ? this.iterator() : null; // if n is odd, no need to decrement
        ContinuedFraction intermediate = pow(n >> 1L);  // bit shift takes care of it all
        Iterator<Long> evenTerms = GosperTermIterator.multiply(intermediate.iterator(), intermediate.iterator());
        Iterator<Long> result = oddTerm != null ? GosperTermIterator.multiply(oddTerm, evenTerms) : evenTerms;
        return new ContinuedFraction(result, mctx.getPrecision() + 1);
    }

    @Override
    public MathContext getMathContext() {
        return mctx;
    }

    public void setMathContext(MathContext ctx) {
        this.mctx = ctx;
    }

    @Override
    public BigDecimal asBigDecimal() {
        long lastTerm = terms() < 0L ? mctx.getPrecision() : terms() - 1L;
        while (termAt(lastTerm) == 0L && lastTerm > 0L) lastTerm--;
        BigDecimal accum = BigDecimal.ZERO;
        for (long k = lastTerm; k >= 0L; k--) {
            BigDecimal prev = k != lastTerm ? BigDecimal.ONE.divide(accum, mctx) : BigDecimal.ZERO;
            accum = BigDecimal.valueOf(termAt(k)).add(prev, mctx);
        }
        return accum;
    }

    @Override
    public Sign sign() {
        for (long term : terms) {
            if (term != 0L) return Sign.fromValue(term);
        }
        return Sign.ZERO;
    }

    @Override
    public IntegerType floor() {
        return new IntegerImpl(BigInteger.valueOf(terms[0]));
    }

    @Override
    public IntegerType ceil() {
        if (terms.length == 1) return new IntegerImpl(BigInteger.valueOf(terms[0]));
        return new IntegerImpl(BigInteger.valueOf(terms[0] + 1L));
    }

    @Override
    public Set<ComplexType> nthRoots(IntegerType n) {
        return new RealImpl(asBigDecimal(), mctx).nthRoots(n);
    }

    @Override
    public int compareTo(RealType o) {
        if (o instanceof ContinuedFraction) {
            ContinuedFraction that = (ContinuedFraction) o;
            final long extent = computeExtent(this.terms(), that.terms());
            for (long k = 0L; k < extent; k++) {
                int temp = Long.compare(this.termAt(k), that.termAt(k));
                if (temp == 0) continue;
                if (k % 2L == 0L) return temp;
                // for odd terms, flip the order of the comparison
                else return -temp;
            }
            // if we fell through here, all the terms tht exist on both
            // sides are the same, so pick whichever has more terms as the winner
            if (this.terms() > extent || this.terms() < 0L) return 1;
            else if (that.terms() > extent || that.terms() < 0L) return -1;
            return 0;
        }
        // fall back to BigDecimal comparisons
        return asBigDecimal().compareTo(o.asBigDecimal());
    }

    private long computeExtent(long a, long b) {
        if (a < 0L && b < 0L) return mctx.getPrecision() == 0 ? Long.MAX_VALUE : mctx.getPrecision();
        if (a < 0L) return b;
        if (b < 0L) return a;
        return Math.min(a, b);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(terms) + 7 * Objects.hash(repeatsFromIndex, mappingFunc, mctx);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof ContinuedFraction) {
            ContinuedFraction that = (ContinuedFraction) obj;
            if ((this.mappingFunc == null && that.mappingFunc != null) ||
                    (this.mappingFunc != null && that.mappingFunc == null)) return false;
            return Arrays.equals(this.terms, that.terms) &&
                    this.repeatsFromIndex == that.repeatsFromIndex;
        }

        return obj.equals(this);
    }

    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder();
        buf.append('[').append(terms[0]).append("; ");
        int extent = repeatsFromIndex < 0 ? terms.length : repeatsFromIndex;
        for (int k = 1; k < extent; k++) {
            buf.append(terms[k]);
            if (k != extent - 1) buf.append(", ");
        }
        if (extent < terms.length) {
            StringBuilder buf2 = new StringBuilder();
            for (int k = extent; k < terms.length; k++) {
                buf2.append(terms[k]);
                if (k != terms.length - 1) buf2.append(", ");
            }
            buf.append(", ").append(UnicodeTextEffects.overline(buf2.toString()));
        }
        if (mappingFunc != null) buf.append(", \u2026");
        buf.append(']');
        return buf.toString();
    }

    /**
     * Obtain an iterator over the terms of this continued fraction.
     * @return an iterator that returns a (possibly non-terminating) sequence of {@code Long} values
     */
    @Override
    public Iterator<Long> iterator() {
        return new Iterator<>() {
            private long k = 0L;

            @Override
            public boolean hasNext() {
                return terms() < 0L || k < terms();
            }

            @Override
            public Long next() {
                if (terms() > 0L && k >= terms()) return null;
                long term = termAt(k++);
                // since k has advanced, compare with 1 instead of 0
                if (k > 1L && term == 0L) return null;
                return term;
            }
        };
    }
}
