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
import tungsten.types.util.UnicodeTextEffects;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.util.*;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ContinuedFraction implements RealType {
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

    public long termAt(long index) {
        if (index < 0L) throw new IndexOutOfBoundsException("Negative indices are not supported");
        if (index < terms.length) return terms[(int) index];
        if (repeatsFromIndex >= 0) {
            long period = terms.length - repeatsFromIndex;
            return terms[(int) ((index - repeatsFromIndex) % period + repeatsFromIndex)];
        }
        if (mappingFunc != null) {
            return mappingFunc.apply(index);
        }
        throw new IndexOutOfBoundsException("No term present at " + index);
    }

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
        if (numtype == Numeric.class) return this;
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
        return null;
    }

    @Override
    public Numeric subtract(Numeric subtrahend) {
        return null;
    }

    @Override
    public Numeric multiply(Numeric multiplier) {
        return null;
    }

    @Override
    public Numeric divide(Numeric divisor) {
        return null;
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
        // TODO: need an algorithm to compute sqrt for an entire continued fraction
        return new RealImpl(asBigDecimal(), mctx).sqrt();
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
        return Arrays.hashCode(terms) + Objects.hash(repeatsFromIndex, mappingFunc, mctx);
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
}
