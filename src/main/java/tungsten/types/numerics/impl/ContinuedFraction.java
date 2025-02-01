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
import tungsten.types.util.*;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.util.*;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.LongStream;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

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
 * @since 0.5
 */
public class ContinuedFraction implements RealType, Iterable<Long> {
    /**
     * System property governing whether repeated sections are rendered in angle brackets.
     * If not present or false, the default behavior is to use an overline (vinculum).
     */
    public static final String REPEAT_IN_BRACKETS = "tungsten.types.numerics.ContinuedFraction.repeatInBrackets";

    private final long[] terms;
    private int repeatsFromIndex = -1;
    /**
     * For infinite continued fractions, takes
     * an index value and produces the value of
     * the term at that index.
     * Note: this function is not required to produce
     * valid values for indices {@code < terms.length}.
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
            final BigDecimal epsilon = BigDecimal.TEN.pow(3 - precision, ctx);
            Logger.getLogger(ContinuedFraction.class.getName()).log(Level.INFO,
                    "Obtaining continued fraction for {0} with precision = {1}, \uD835\uDF00 = {2}",
                    new Object[] { num, precision, epsilon.toPlainString() });
            BigDecimal frac;
            List<Long> terms = new LinkedList<>();

            do {
                long current = floor(termValue);
                terms.add(current);
                frac = termValue.subtract(BigDecimal.valueOf(current));
                if (frac.compareTo(BigDecimal.ZERO) == 0) break;  // for safety (avoiding division by 0)
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
                        long numTerms = index - boundary - cache.size();
                        if (numTerms > 0L) {
                            Logger.getLogger(ContinuedFraction.class.getName()).log(Level.INFO,
                                    "Cannot find term {0} in cache; iterating over {1} terms and caching.",
                                    new Object[] {index, numTerms});
                        }
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

    /**
     * A string-based constructor which takes a representation of a continued fraction.
     * The expected format of the string is [a<sub>0</sub>; a<sub>1</sub>, a<sub>2</sub>, &hellip;, a<sub>n</sub>]
     * where a<sub>0</sub> is the whole number portion and every a<sub>k</sub> is an integer term.
     * <br>If the continued fraction contains a repeating sequence, the first term of the repeated
     * sequence can be prefixed with the ^&nbsp;(caret) character.
     * @param init the {@code String} containing a specification for the desired continued fraction
     */
    public ContinuedFraction(String init) {
        int start = init.indexOf('[');
        int end = init.lastIndexOf(']');
        if (start == -1 || end == -1 || end < start) {
            throw new IllegalArgumentException("Incorrect format");
        }
        // unwrapping and scrubbing
        String effective = init.substring(start + 1, end).strip().replace('\u2212', '-');
        int semi = effective.indexOf(';');
        if (semi == -1) throw new IllegalArgumentException("No semicolon after whole part");
        long whole = Long.parseLong(effective.substring(0, semi).stripTrailing());
        String fractional = effective.substring(semi + 1).stripLeading();
        String[] fracTerms = fractional.split("\\s*,\\s*");
        if (fracTerms.length == 1 && fracTerms[0].isBlank()) {
            // if there's nothing between the semicolon (;) and right bracket (])
            terms = new long[] { whole };
            return;
        }
        terms = new long[fracTerms.length + 1];
        terms[0] = whole;
        for (int i = 0; i < fracTerms.length; i++) {
            if (fracTerms[i].charAt(0) == '^') {
                if (repeatsFromIndex > 0) {
                    // can only specify a single index from which to begin a repeated sequence
                    throw new IllegalArgumentException("Found a start index for repeats at " + (i + 1) +
                            " but one was already specified at " + repeatsFromIndex);
                }
                // this denotes the first term in a repeating sequence
                repeatsFromIndex = i + 1;
                fracTerms[i] = fracTerms[i].substring(1).stripLeading();
            }
            terms[i + 1] = Long.parseLong(fracTerms[i]);
        }
    }

    /**
     * A string-based constructor which takes a representation of a continued fraction and
     * a {@code MathContext}.
     * @param init the {@code String} containing a specification for the desired continued fraction
     * @param mctx the initial {@code MathContext} of this continued fraction
     */
    public ContinuedFraction(String init, MathContext mctx) {
        this(init);
        this.mctx = mctx;
    }

    private long floor(BigDecimal value) {
        BigDecimal stripped = value.stripTrailingZeros();
        long whole = stripped.toBigInteger().longValueExact();
        if (value.signum() == -1 && stripped.scale() > 0) {
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

    /**
     * A convenience constructor for values where the first term a<sub>0</sub> is
     * specified and the remaining terms are specified by a function.
     * @param a0          the whole number term a<sub>0</sub>
     * @param mappingFunc a function that maps indices to term values
     */
    public ContinuedFraction(long a0, Function<Long, Long> mappingFunc) {
        terms = new long[] {a0};
        this.mappingFunc = mappingFunc;
    }

    private long[] annealZeros(long[] source) {
        if (source.length == 1) return source;

        ArrayList<Long> termList = new ArrayList<>();
        termList.add(source[0]);
        for (int k = 1; k < source.length; k++) {
            if (source[k] == 0L) {
                if (k == source.length - 1) break; // if the last term is 0, just drop it
                long a = source[k - 1];
                long b = source[++k];
                // term a is already in termList, so remove it
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
            if (mapping == null) {
                Logger.getLogger(ContinuedFraction.class.getName()).log(Level.FINE,
                        "Mapping function returned null for index {0}.", index);
            }
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
                return true;
            case RATIONAL:
                return !isIrrational();
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
                if (isIrrational()) throw new CoercionException("Continued fraction is irrational", this.getClass(), numtype);
                long lastTerm = terms() < 0L ? lastViableTerm(mctx.getPrecision()) : terms() - 1L;
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
            throw new IllegalStateException("While computing a rational approximation, k = " + k, e);
        }
    }

    @Override
    public RealType negate() {
        if (terms() == 1L) {
            ContinuedFraction singleTerm = new ContinuedFraction(-terms[0]);
            singleTerm.setMathContext(mctx);
            return singleTerm;
        }

        long[] negValue = new long[Math.max(terms.length + 1, 3)];
        negValue[0] = -terms[0] - 1L;
        negValue[1] = 1L;
        negValue[2] = terms.length > 1 ? terms[1] - 1L : termAt(1L) - 1L;
        if (terms.length > 2) {
            System.arraycopy(terms, 2, negValue, 3, terms.length - 2);
        }

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
        if (Zero.isZero(addend)) return this;
        if (addend instanceof ContinuedFraction) {
            ContinuedFraction rhs = (ContinuedFraction) addend;
            if (rhs.terms() == 1L) {
                long[] simpleSum = Arrays.copyOf(this.terms, this.terms.length);
                simpleSum[0] += rhs.termAt(0L);
                return new ContinuedFraction(simpleSum, repeatsFromIndex, mappingFunc);
            }
            Iterator<Long> result = GosperTermIterator.add(this.iterator(), rhs.iterator());
            ContinuedFraction sum = new ContinuedFraction(result, 10);
            sum.setMathContext(mctx);
            return sum;
        }

        if (addend.isCoercibleTo(IntegerType.class)) {
            try {
                IntegerType value = (IntegerType) addend.coerceTo(IntegerType.class);
                if (terms() == 1L && value.asBigInteger().abs().bitLength() > 62) {
                    // if the magnitude of the addend is too large, try to return an IntegerImpl
                    return value.add(new IntegerImpl(BigInteger.valueOf(terms[0])));
                }
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

        return addend.add(this);
    }

    @Override
    public Numeric subtract(Numeric subtrahend) {
        if (Zero.isZero(subtrahend)) return this;
        if (subtrahend instanceof ContinuedFraction) {
            ContinuedFraction rhs = (ContinuedFraction) subtrahend;
            if (rhs.terms() == 1L) {
                long[] simpleDiff = Arrays.copyOf(this.terms, this.terms.length);
                simpleDiff[0] -= rhs.termAt(0L);
                return new ContinuedFraction(simpleDiff, repeatsFromIndex, mappingFunc);
            }
            Iterator<Long> result = GosperTermIterator.subtract(this.iterator(), rhs.iterator());
            ContinuedFraction difference = new ContinuedFraction(result, 10);
            difference.setMathContext(mctx);
            return difference;
        }

        if (subtrahend.isCoercibleTo(IntegerType.class)) {
            try {
                IntegerType value = (IntegerType) subtrahend.coerceTo(IntegerType.class);
                if (terms() == 1L && value.asBigInteger().abs().bitLength() > 62) {
                    // if the magnitude of the subtrahend is too big, try to return an IntegerImpl
                    return new IntegerImpl(BigInteger.valueOf(terms[0])).subtract(value);
                }
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

        return subtrahend.negate().add(this);
    }

    @Override
    public Numeric multiply(Numeric multiplier) {
        if (One.isUnity(multiplier)) return this;
        if (Zero.isZero(multiplier)) return ExactZero.getInstance(mctx);
        if (multiplier instanceof ContinuedFraction) {
            ContinuedFraction rhs = (ContinuedFraction) multiplier;
            Iterator<Long> result = GosperTermIterator.multiply(this.iterator(), rhs.iterator());
            ContinuedFraction product = new ContinuedFraction(result, 10);
            if (this.sign() == rhs.sign() && product.termAt(0L) < 0L) {
                // If a₀ < 0 but the multiplicands are of the same sign (product should be positive),
                // Gosper's algorithm has failed.  We need to recover.
                Logger.getLogger(ContinuedFraction.class.getName()).log(Level.WARNING,
                        "Obtained a negative a₀ for the product of {0} and {1}. Recovering with RealImpl.",
                        new Object[] { this, rhs });
                BigDecimal decProduct = this.asBigDecimal().multiply(rhs.asBigDecimal(), mctx);
                return new RealImpl(decProduct, mctx, false);
            }
            product.setMathContext(mctx);
            return product;
        } else if (multiplier instanceof RealType) {
            // it's a real but not a CF
            ContinuedFraction cf = new ContinuedFraction((RealType) multiplier);
            Logger.getLogger(ContinuedFraction.class.getName()).log(Level.FINE,
                    "Converted decimal multiplier {0} into {1}.", new Object[] { multiplier, cf });
            return this.multiply(cf);
        } else if (multiplier.isCoercibleTo(RationalType.class)) {
            try {
                RationalType rational = (RationalType) multiplier.coerceTo(RationalType.class);
                Iterator<Long> result = GosperTermIterator.multiply(this.iterator(), new RationalCFTermAdapter(rational));
                ContinuedFraction prod = new ContinuedFraction(result, 10);
                prod.setMathContext(mctx);
                return prod;
            } catch (CoercionException e) {
                throw new ArithmeticException("While multiplying a rational value: " + e.getMessage());
            }
        }

        return multiplier.multiply(this);
    }

    @Override
    public Numeric divide(Numeric divisor) {
        if (Zero.isZero(divisor)) throw new ArithmeticException("Division by 0");
        if (One.isUnity(divisor)) return this;
        if (divisor.equals(this)) return One.getInstance(mctx);
        if (divisor instanceof ContinuedFraction) {
            ContinuedFraction rhs = (ContinuedFraction) divisor;
            Iterator<Long> result = GosperTermIterator.divide(this.iterator(), rhs.iterator());
            ContinuedFraction quotient = new ContinuedFraction(result, 10);
            quotient.setMathContext(mctx);
            return quotient;
        } else if (divisor instanceof RealType) {
            // the divisor is a real but not a CF
            ContinuedFraction cf = new ContinuedFraction((RealType) divisor);
            Logger.getLogger(ContinuedFraction.class.getName()).log(Level.FINE,
                    "Converted decimal divisor {0} into {1}.", new Object[] { divisor, cf });
            return this.divide(cf);
        } else if (divisor.isCoercibleTo(RationalType.class)) {
            try {
                RationalType rational = (RationalType) divisor.coerceTo(RationalType.class);
                Iterator<Long> result = GosperTermIterator.divide(this.iterator(), new RationalCFTermAdapter(rational));
                ContinuedFraction quotient = new ContinuedFraction(result, 10);
                quotient.setMathContext(mctx);
                return quotient;
            } catch (CoercionException e) {
                throw new ArithmeticException("While dividing by a rational value: " + e.getMessage());
            }
        }

        return divisor.inverse().multiply(this);
    }

    @Override
    public Numeric inverse() {
        if (terms() == 1L && terms[0] == 0L) throw new ArithmeticException("Division by 0");

        ContinuedFraction result;
        if (terms[0] == 0L) {
            // remove a 0
            long[] invTerms = Arrays.copyOfRange(terms, 1, terms.length);
            if (invTerms.length == 0) invTerms = new long[] { termAt(1L) };
            int cycleStart = repeatsFromIndex < 0 ? -1 : repeatsFromIndex - 1;
            Function<Long, Long> nuMapper = null;
            if (mappingFunc != null) {
                nuMapper = mappingFunc.compose(n -> n + 1L);
            }
            result = new ContinuedFraction(invTerms, cycleStart, nuMapper);
        } else {
            if (terms[0] < 0L) {
                // special handling of negatives
                return this.negate().inverse().negate();
            }
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

        final long guess = sqrt(terms[0]);  // a₀
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
            prev = new ContinuedFraction(avg, iter + 1) {
                @Override
                public boolean isExact() {
                    // override the default behavior here since square roots
                    // computed using Newton's method iterations are seldom exact
                    return false;
                }
            };
        }
        prev.setMathContext(mctx);
        return prev;
    }

    private int findPalindromeStart(long[] arr) {
        if (arr.length < 2) return -1;

        for (int k = 1; k <= arr.length - 2; k++) {
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
     * The caller should supply the {@code MathContext} for the result, if needed.
     * @param n the integral exponent
     * @return this continued fraction raised to the {@code n}<sup>th</sup> power
     * @apiNote For certain &ldquo;difficult&rdquo; continued fractions, such as
     *   the square roots of non-square integers, Gosper's algorithm fails at
     *   multiplication.  Since this method operates by the repeated application
     *   of {@link GosperTermIterator#multiply(Iterator, Iterator)}, it is possible
     *   for the a<sub>0</sub> term of the result to be negative when it should not be.
     *   For non-negative continued fractions, a negative value in a<sub>0</sub>
     *   returned by this method is an obvious indication of failure.
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

    /**
     * Compute the n<sup>th</sup> root of {@code this}, where n is an integer value.
     * @param n the degree of the root to be computed
     * @return the n<sup>th</sup> root as a continued fraction
     * @throws ArithmeticException if {@code n} is even and {@code this} is negative
     * @throws IllegalArgumentException if {@code n < 2}
     * @since 0.6
     */
    public ContinuedFraction nthRoot(long n) {
        if (n < 2L) throw new IllegalArgumentException("Roots of degree < 2 not supported");
        if (n % 2L == 0L && this.sign() == Sign.NEGATIVE) throw new ArithmeticException("Cannot compute a real-valued " + n + "th root of " + this);
        final RationalType fracCoeff = new RationalImpl(n - 1L, n, mctx);
        Iterator<Long> AdivnIter = GosperTermIterator.divide(this.iterator(),
                new ContinuedFraction(n).iterator());
        final ContinuedFraction Adivn = new ContinuedFraction(AdivnIter, 5);

        ContinuedFraction xk = Adivn;  // initial estimate
        for (int i = 1; i < mctx.getPrecision() + Math.min(n, 7L); i++) {
            Iterator<Long> termA = GosperTermIterator.multiply(xk.iterator(),
                    new RationalCFTermAdapter(fracCoeff));
            Iterator<Long> termB = GosperTermIterator.multiply(Adivn.iterator(),
                    xk.pow(-n + 1L).iterator());  // inverse of n - 1 exponent
            Iterator<Long> sum = GosperTermIterator.add(termA, termB);
            xk = new ContinuedFraction(sum, i);
        }
        xk.setMathContext(mctx);
        return xk;
    }

    @Override
    public MathContext getMathContext() {
        return mctx;
    }

    /**
     * Set the {@code MathContext} for this real value.
     * @param ctx any valid {@code MathContext}
     */
    public void setMathContext(MathContext ctx) {
        this.mctx = ctx;
    }

    @Override
    public BigDecimal asBigDecimal() {
        long lastTerm = terms() < 0L ? mctx.getPrecision() : terms() - 1L;  // initial guess
        lastTerm = lastViableTerm(lastTerm);  // refine the guess
        BigDecimal accum = BigDecimal.ZERO;
        for (long k = lastTerm; k >= 0L; k--) {
            if (k != lastTerm  && accum.compareTo(BigDecimal.ZERO) == 0) {
                Logger.getLogger(ContinuedFraction.class.getName())
                        .log(Level.WARNING, "Encountered unexpected 0 term; k = {0}, last term = {1}.",
                                new Object[] { k, lastTerm });
            }
            BigDecimal prev = k != lastTerm ? BigDecimal.ONE.divide(accum, mctx) : BigDecimal.ZERO;
            accum = BigDecimal.valueOf(termAt(k)).add(prev, mctx);
        }
        return accum;
    }

    private long lastViableTerm(long lastTerm) {
        long updated = lastTerm;
        for (long index = lastTerm; index > 0L; index--) {
            if (termAt(index) == 0L) updated = index - 1L;
        }
        return updated;
    }

    /**
     * Truncate this continued fraction to create a new continued fraction
     * with exactly <em>N</em> terms.  The newly created fraction has
     * no indication of repetition, nor does it have a mapping function.
     * Used in conjuction with {@link #coerceTo(Class)} with an argument
     * of {@code RationalType.class}, a convergent can be generated.
     * @param nterms the number <em>N</em> of terms to keep
     * @return a continued fraction consisting of the first <em>N</em> terms of this
     *   continued fraction
     * @apiNote The argument is an {@code int} since we want the generated continued
     *   fraction to fit all its terms into an array.
     * @see #computeRationalValue(long, long) the implementation of the algorithm to generate
     *   a rational from a continued fraction
     */
    public ContinuedFraction trimTo(int nterms) {
        if (nterms < 1) throw new IllegalArgumentException("Trimmed CF must have at least 1 term");
        if (terms() > 0L && terms() < (long) nterms) throw new IllegalArgumentException("Not enough source terms");
        if (nterms <= this.terms.length) {
            // efficient path
            return new ContinuedFraction(Arrays.copyOf(this.terms, nterms), -1, null);
        }
        long[] trimmedTerms = StreamSupport.stream(this.spliterator(), false)
                .limit(nterms).mapToLong(Long::longValue).toArray();
        return new ContinuedFraction(trimmedTerms, -1, null);
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
        if (terms() == 1L) return new IntegerImpl(BigInteger.valueOf(terms[0]));
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

    protected boolean useAngleBracketsForRepeating() {
        return Boolean.getBoolean(REPEAT_IN_BRACKETS);
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
            if (extent > 1) buf.append(", ");
            if (useAngleBracketsForRepeating()) {
                buf.append('\u27E8').append(buf2).append('\u27E9');
            } else {
                buf.append(UnicodeTextEffects.overline(buf2.toString()));
            }
        }
        if (mappingFunc != null) {
            if (terms.length > 1) buf.append(", ");
            buf.append('\u2026');
        }
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

    /*
     The following section provides factory methods that produce various constants.
     */

    /**
     * Generate a continued fraction representation of &#x03D5; (phi),
     * the Golden Ratio.
     * @param ctx the {@code MathContext} to use for this constant
     * @return the value of &#x03D5; as a continued fraction
     */
    public static ContinuedFraction phi(MathContext ctx) {
        ContinuedFraction phi = new ContinuedFraction(new long[] {1L, 1L}, 1, null) {
            @Override
            public Numeric subtract(Numeric subtrahend) {
                if (subtrahend instanceof Phi) return ExactZero.getInstance(getMathContext());
                return super.subtract(subtrahend);
            }

            @Override
            public Numeric divide(Numeric divisor) {
                if (divisor instanceof Phi) return One.getInstance(getMathContext());
                return super.divide(divisor);
            }

            /**
             * We could use {@link tungsten.types.set.impl.FibonacciNumbers#getNthFibonacciNumber(long)} to
             * obtain the n<sup>th</sup> Fibonacci number, but that would require carrying around an instance
             * of the set just in case someone needs to compute a power of &#x03D5; &mdash; not to mention
             * having to unwrap an {@code IntegerType} with each invocation.  This should be more than good
             * enough.
             * @param n a non-negative integer index
             * @return the {@code n}<sup>th</sup> Fibonacci number
             */
            private long fibonacci(long n) {
                if (n < 0L) throw new ArithmeticException("No negative indices allowed for Fibonacci numbers");
                if (n == 0L || n == 1L) return 1L;
                return fibonacci(n - 2L) + fibonacci(n - 1L);
            }

            @Override
            public ContinuedFraction pow(long n) {
                if (n == 0L) return new ContinuedFraction(1L);
                if (n == 1L) return this;
                if (n == -1L) return (ContinuedFraction) inverse();  // a very cheap operation
                // powers of phi can be computed without exponentiation, just a simple multiply and add
                // see https://r-knott.surrey.ac.uk/Fibonacci/propsOfPhi.html#numprops
                long phiCoeff = fibonacci(Math.abs(n) - 1L);
                if (n > 0L) {
                    long offset  = fibonacci(n - 2L);
                    ContinuedFraction coeff = new ContinuedFraction(phiCoeff);
                    ContinuedFraction cfOffset = new ContinuedFraction(offset);
                    Iterator<Long> prod = GosperTermIterator.multiply(coeff.iterator(), this.iterator());
                    Iterator<Long> sum = GosperTermIterator.add(cfOffset.iterator(), prod);
                    return new ContinuedFraction(sum, 3);
                } else {
                    // otherwise, we have a negative exponent
                    final long negn = -n;
                    if (negn % 2L == 0L) phiCoeff = -phiCoeff;
                    long offset = fibonacci(negn);
                    if (negn % 2L == 1L) offset = -offset;
                    ContinuedFraction coeff = new ContinuedFraction(phiCoeff);
                    ContinuedFraction cfOffset = new ContinuedFraction(offset);
                    Iterator<Long> prod = GosperTermIterator.multiply(coeff.iterator(), this.iterator());
                    Iterator<Long> sum = GosperTermIterator.add(cfOffset.iterator(), prod);
                    // negative powers of phi can have 4 significant terms
                    return new ContinuedFraction(sum, 4);
                }
            }
        };
        phi.setMathContext(ctx);
        return phi;
    }

    /**
     * Generate a continued fraction representation of &#x212f;, Euler's number.
     * @param ctx the {@code MathContext} to use for this constant
     * @return the value of &#x212f; as a continued fraction
     */
    public static ContinuedFraction euler(MathContext ctx) {
        ContinuedFraction e = new ContinuedFraction(new long[] {2L}, -1,
                n -> (n + 1L)%3L == 0L ? 2L * (n + 1L) / 3L : 1L) {
            @Override
            public Numeric subtract(Numeric subtrahend) {
                if (subtrahend instanceof Euler) return ExactZero.getInstance(getMathContext());
                return super.subtract(subtrahend);
            }

            @Override
            public Numeric divide(Numeric divisor) {
                if (divisor instanceof Euler) return One.getInstance(getMathContext());
                return super.divide(divisor);
            }

            @Override
            public Numeric sqrt() {
                ContinuedFraction root = nthRoot(2L);
                root.setMathContext(getMathContext());
                return root;
            }

            /**
             * Compute &#x212f;<sup>n</sup>, where n is an integer.
             * For n&nbsp;=&nbsp;2, the calculation is performed as
             * if computing &#x212f;<sup>2/x</sup> where x&nbsp;=&nbsp;1.
             * @param n the integral exponent
             * @return &#x212f; raised to the {@code n} power
             * @since 0.6
             */
            @Override
            public ContinuedFraction pow(long n) {
                if (n == 2L) {
                    Stream<Long> cat = Stream.concat(Stream.of(1L),
                            LongStream.range(1L, Long.MAX_VALUE).mapToObj(k -> root2term(k, 1L)));
                    Iterator<Long> esquared = new CFCleaner(cat.iterator());
                    return new ContinuedFraction(esquared, 10);
                }
                return super.pow(n);
            }

            /**
             * Compute &#x212f;<sup>1/n</sup>, where n is a positive integer &ge;&nbsp;2.
             * @param n the degree of the root to be computed
             * @return a continued fraction representation of the {@code n}<sup>th</sup> root of Euler's number
             * @see <a href="https://en.wikipedia.org/wiki/Simple_continued_fraction#Regular_patterns_in_continued_fractions">this
             *   article at Wikipedia detailing continued fractions with regular patterns</a>
             * @apiNote This implementation of {@code nthRoot()} does not set the {@code MathContext} of the result.
             * @since 0.6
             */
            @Override
            public ContinuedFraction nthRoot(long n) {
                if (n < 2L) throw new IllegalArgumentException("Degree of root must be ≥ 2");
                return new ContinuedFraction(new long[] {1L}, -1,
                        k -> (k - 1L)%3L == 0L ? rootTerm(k, n) : 1L);
            }

            private long rootTerm(long k, long n) {
                long coeff = k - (k - 1L)/3L;
                return coeff * n - 1L;
            }

            /*
             Groovy operator overloading.  We provide a special version of power() that takes
             advantage of the regularity of the nth root of e.
             */

            @Override
            public RealType power(Numeric operand) {
                if (operand instanceof RationalType) {
                    RationalType exponent = (RationalType) operand;
                    long p = exponent.numerator().asBigInteger().longValueExact();
                    long q = exponent.denominator().asBigInteger().longValueExact();
                    if (p == 2L && exponent.denominator().isOdd()) {
                        // exponents of 2/q have a special form
                        ContinuedFraction result = new ContinuedFraction(new long[] {1L}, -1,
                                k -> root2term(k, q));
                        result.setMathContext(getMathContext());
                        return result;
                    }
                    // normally, we would take the power first, then the nth root
                    // but since the nth root of e is perfectly known, it may be best
                    // to take the root first and then exponentiate
                    ContinuedFraction root = nthRoot(q);
                    root.setMathContext(getMathContext());
                    ContinuedFraction pwr = root.pow(p);
                    pwr.setMathContext(getMathContext());
                    return pwr;
                }
                return super.power(operand);
            }

            private long root2term(long k, long n) {
                final int fiveBlock = (int) ((k - 1L) % 5L);
                long base = (k - 1L)/5L * 6L + 1L;  // divide by 5 first
                switch (fiveBlock) {
                    case 0:
                        return (base * n - 1L)/2L;
                    case 1:
                        return (base * 2L + 4L) * n;
                    case 2:
                        long extBase = base + 4L;
                        return (extBase * n - 1L)/2L;
                    default:
                        return 1L;
                }
            }
        };
        e.setMathContext(ctx);
        return e;
    }

    /**
     * Generate a continued fraction representation of &pi;.
     * @param ctx the {@code MathContext} to use for this constant
     * @return an approximation of &pi;, the accuracy of which is governed by {@code ctx}
     */
    public static ContinuedFraction pi(MathContext ctx) {
        Iterator<Long> accum = new RationalCFTermAdapter(piTerm(0L, ctx));
        for (long k = 1L; k < (long) ctx.getPrecision() + 1L; k++) {
            accum = GosperTermIterator.add(accum, new RationalCFTermAdapter(piTerm(k, ctx)));
        }
        ContinuedFraction pi = new ContinuedFraction(accum, ctx.getPrecision()/2 + 3) {
            @Override
            public Numeric subtract(Numeric subtrahend) {
                // approximation is ≠ that generated by Pi, so reflect this symbolically
                if (subtrahend instanceof Pi) {
                    return ((Pi) subtrahend).compareTo(this) > 0 ? NegZero.getInstance(getMathContext()) : PosZero.getInstance(getMathContext());
                }
                return super.subtract(subtrahend);
            }

            @Override
            public Numeric divide(Numeric divisor) {
                if (divisor instanceof Pi) return One.getInstance(getMathContext());
                return super.divide(divisor);
            }
        };
        pi.setMathContext(ctx);
        return pi;
    }

    private static RationalType piTerm(long k, MathContext ctx) {
        IntegerType denom = MathUtils.factorial(new IntegerImpl(BigInteger.valueOf(2L * k + 1L)));
        IntegerType pwr2 = (IntegerType) new IntegerImpl(BigInteger.TWO).pow(new IntegerImpl(BigInteger.valueOf(k + 1L)));
        IntegerType kfact = MathUtils.factorial(new IntegerImpl(BigInteger.valueOf(k)));
        IntegerType num = (IntegerType) kfact.multiply(kfact).multiply(pwr2);
        return new RationalImpl(num, denom, ctx);
    }

    /**
     * Generate a continued fraction representation of Freiman's constant.
     * This constant is a quadratic irrational and arises in the theory of continued fractions.
     * @param ctx the {@code MathContext} to use for this constant
     * @return a continued fraction representation of Freiman's constant
     * @see <a href="https://mathoverflow.net/questions/457086/simple-continued-fraction-of-freimans-constant">this
     *   question on MathOverflow</a>
     */
    public static ContinuedFraction freiman(MathContext ctx) {
        ContinuedFraction denom = new ContinuedFraction(491993569L);
        ContinuedFraction numA = new ContinuedFraction(2221564096L);
        ContinuedFraction numB = new ContinuedFraction(283748L);
        // we know that we'll get a ContinuedFraction back for taking the square root of an integer
        ContinuedFraction numC = (ContinuedFraction) new ContinuedFraction(462L).sqrt();
        Iterator<Long> num = GosperTermIterator.add(numA.iterator(),
                GosperTermIterator.multiply(numB.iterator(), numC.iterator()));
        Iterator<Long> quotient = GosperTermIterator.divide(num, denom.iterator());
        ContinuedFraction freiman = new ContinuedFraction(quotient, ctx.getPrecision()/2 + 1);
        freiman.setMathContext(ctx);
        return freiman;
    }

    /*
     Methods for Groovy operator overloading follow.
     */

    @Override
    public RealType power(Numeric operand) {
        if (operand instanceof IntegerType) {
            long exponent = ((IntegerType) operand).asBigInteger().longValueExact();
            ContinuedFraction result = this.pow(exponent);
            result.setMathContext(mctx);
            return result;
        } else if (operand instanceof RationalType) {
            RationalType exponent = (RationalType) operand;
            long p = exponent.numerator().asBigInteger().longValueExact();
            long q = exponent.denominator().asBigInteger().longValueExact();
            ContinuedFraction afterPow = this.pow(p);
            afterPow.setMathContext(mctx);
            if (q == 1L) return afterPow;
            return afterPow.nthRoot(q);
        }
        return RealType.super.power(operand);
    }

    public Long getAt(int k) {
        if (k < 0) {
            if (terms() < 0L) throw new IndexOutOfBoundsException("Cannot use a negative index with an infinite continued fraction");
            return termAt(terms() + k);
        }
        return termAt(k);
    }
}
