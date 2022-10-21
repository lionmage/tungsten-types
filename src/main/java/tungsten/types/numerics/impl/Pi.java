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
package tungsten.types.numerics.impl;

import tungsten.types.Numeric;
import tungsten.types.Set;
import tungsten.types.annotations.Constant;
import tungsten.types.exceptions.CoercionException;
import tungsten.types.numerics.*;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * This class provides a representation of the mathematical constant pi (&pi;).
 * The class is not publicly instantiable; it provides a factory method
 * that will give you back an instance of itself for a given {@link MathContext},
 * and keeps a cache of instances that have been generated so that the value
 * of pi only needs to be calculated once for a given precision and
 * {@link RoundingMode}.
 * 
 * Internally, this class uses the BBP formula for deriving Pi to an
 * arbitrary precision.
 *
 * Currently, intermediate terms are cached as {@link RationalType} objects;
 * coercion to real (via {@link BigDecimal} division with {@link MathContext}-specified rounding)
 * is only done once the correct number of terms have been summed. This saves a
 * tremendous amount of recalculation.
 *
 * @author tarquin
 * @see <a href="https://en.wikipedia.org/wiki/Bailey%E2%80%93Borwein%E2%80%93Plouffe_formula">the Wikipedia article on BBP</a>
 */
@Constant(name = "pi", representation = "\uD835\uDF0B")
public class Pi implements RealType {
    private BigDecimal value;
    private final MathContext mctx;
    
    private Pi(MathContext mctx) {
        this.mctx = mctx;
        calculate();
    }
    
    private static final Lock instanceLock = new ReentrantLock();
    private static final Map<MathContext, Pi> instanceMap = new HashMap<>();
    
    /**
     * Factory method for obtaining an instance of &pi; at a given precision.
     * @param mctx provides the desired precision and {@link RoundingMode} used for internal calculations
     * @return an instance of &pi; to the specified precision
     */
    public static Pi getInstance(MathContext mctx) {
        instanceLock.lock();
        try {
            Pi instance = instanceMap.get(mctx);
            if (instance == null) {
                instance = new Pi(mctx);
                instanceMap.put(mctx, instance);
            }
            return instance;
        } finally {
            instanceLock.unlock();
        }
    }

    @Override
    public boolean isIrrational() {
        return true;
    }

    @Override
    public RealType magnitude() {
        final RealImpl proxy = new RealImpl(value, false);
        proxy.setIrrational(true);
        proxy.setMathContext(mctx);
        return proxy;
    }

    @Override
    public RealType negate() {
        final RealImpl negvalue = new RealImpl(value.negate(), false);
        negvalue.setIrrational(true);
        negvalue.setMathContext(mctx);
        return negvalue;
    }

    @Override
    public BigDecimal asBigDecimal() {
        return value;
    }

    @Override
    public Sign sign() {
        return Sign.POSITIVE;
    }

    @Override
    public boolean isExact() {
        return false;
    }

    @Override
    public boolean isCoercibleTo(Class<? extends Numeric> numtype) {
        if (numtype == Numeric.class) return true;
        NumericHierarchy htype = NumericHierarchy.forNumericType(numtype);
        // can be coerced to real or complex
        return htype.compareTo(NumericHierarchy.REAL) >= 0;
    }

    @Override
    public Numeric coerceTo(Class<? extends Numeric> numtype) throws CoercionException {
        if (numtype == Numeric.class) return this;
        NumericHierarchy htype = NumericHierarchy.forNumericType(numtype);
        switch (htype) {
            case REAL:
                return this;  // it's already a real
            case COMPLEX:
                return new ComplexRectImpl(this, (RealType) ExactZero.getInstance(mctx).coerceTo(RealType.class));
            default:
                throw new CoercionException("Pi can only be coerced to real or complex",
                        this.getClass(), numtype);
        }
    }

    @Override
    public Numeric add(Numeric addend) {
        if (addend instanceof Pi) {
            // to avoid a stack overflow
            RealImpl real = new RealImpl(value.add(((RealType) addend).asBigDecimal(), mctx), false);
            real.setIrrational(true);
            real.setMathContext(mctx);
            return real;
        }
        final Numeric result = addend.add(this);
        return result;
    }

    @Override
    public Numeric subtract(Numeric subtrahend) {
        final Numeric result = subtrahend.negate().add(this);
        return result;
    }

    @Override
    public Numeric multiply(Numeric multiplier) {
        if (multiplier instanceof Pi) {
            // to avoid stack overflow
            RealImpl real = new RealImpl(value.multiply(((RealType) multiplier).asBigDecimal(), mctx), false);
            real.setIrrational(true);
            real.setMathContext(mctx);
            return real;
        }
        final Numeric result = multiplier.multiply(this);
        return result;
    }

    @Override
    public Numeric divide(Numeric divisor) {
        final Numeric result = divisor.inverse().multiply(this);
        return result;
    }

    @Override
    public Numeric inverse() {
        return this.magnitude().inverse();
    }

    @Override
    public Numeric sqrt() {
        return this.magnitude().sqrt();
    }

    @Override
    public Set<ComplexType> nthRoots(IntegerType n) {
        return this.magnitude().nthRoots(n);
    }
    
    public long numberOfDigits() {
        return (long) mctx.getPrecision();
    }

    private static final IntegerType SIXTEEN = new IntegerImpl(BigInteger.valueOf(16L));

    protected static RationalType safeReduce(RationalType A, RationalType B) {
        Numeric sum = A.add(B);
        try {
            return (RationalType) sum.coerceTo(RationalType.class);
        } catch (CoercionException e) {
            throw new IllegalStateException(e);
        }
    }
    
    /*
    Computes the value of pi using the BBP formula.
    */
    private void calculate() {
        // compute a few extra digits so that we can round off later
        MathContext compctx = new MathContext(mctx.getPrecision() + 4, mctx.getRoundingMode());
        for (long k = termsInCache(); k < mctx.getPrecision() - 1; k++) {
            boolean success = cacheTerm(computeKthTerm(k), k);
            if (!success) {
                Logger.getLogger(Pi.class.getName()).log(Level.WARNING,
                        "Unable to cache term for k = {}, continuing. Pi value may not match desired precision.",
                        new Object[] {k});
            }
        }
        // now reduce this
        RationalType sum = termCache.stream().limit(mctx.getPrecision() - 1).reduce(Pi::safeReduce).orElseThrow();
        RealType converted = new RealImpl(sum, compctx);

        this.value = converted.asBigDecimal().round(mctx);
    }
    
    private static final List<RationalType> termCache = new LinkedList<>();

    protected RationalType getFromCache(long k) {
        return termCache.stream().skip(k).findFirst().orElse(null);
    }

    protected boolean cacheTerm(RationalType term, long k) {
        if (getFromCache(k) != null || termsInCache() > k) return false;
        return termCache.add(term);
    }

    protected boolean cacheTerms(List<RationalType> terms, long k_start) {
        if (termsInCache() > k_start) return false;
        return termCache.addAll(terms);
    }

    protected long termsInCache() {
        return termCache.stream().count();
    }

    private RationalType computeKthTerm(long k) {
        RationalType cached = getFromCache(k);
        if (cached != null) {
            return cached;
        }
        final IntegerType one = new IntegerImpl(BigInteger.ONE);
        final IntegerType four = new IntegerImpl(BigInteger.valueOf(4L));
        final IntegerType two = new IntegerImpl(BigInteger.valueOf(2L));
        final IntegerType five = (IntegerType) four.add(one);
        final IntegerType six  = (IntegerType) five.add(one);

        IntegerType kval = new IntegerImpl(BigInteger.valueOf(k));
        RationalType scale = new RationalImpl(one, (IntegerType) SIXTEEN.pow(kval));
        RationalType interm1 = new RationalImpl(four, computeSubterm(kval, one));
        RationalType interm2 = new RationalImpl(two, computeSubterm(kval, four));
        RationalType interm3 = new RationalImpl(one, computeSubterm(kval, five));
        RationalType interm4 = new RationalImpl(one, computeSubterm(kval, six));
        return (RationalType) interm1.subtract(interm2).subtract(interm3).subtract(interm4).multiply(scale);
    }

    private IntegerType computeSubterm(IntegerType k, IntegerType val) {
        final IntegerType eight = new IntegerImpl(BigInteger.valueOf(8L));

        return (IntegerType) eight.multiply(k).add(val);
    }
    
    @Override
    public boolean equals(Object o) {
        if (o instanceof Pi) {
            Pi that = (Pi) o;
            if (this.mctx.getRoundingMode() != that.mctx.getRoundingMode()) return false;
            return this.numberOfDigits() == that.numberOfDigits();
        }
        return false;
    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 73 * hash + Objects.hashCode(this.value);
        hash = 73 * hash + Objects.hashCode(this.mctx);
        return hash;
    }

    @Override
    public int compareTo(RealType o) {
        return value.compareTo(o.asBigDecimal());
    }
    
    @Override
    public String toString() {
        // returns the mathematical small italic pi symbol with precision in digits
        return "\uD835\uDF0B[" + numberOfDigits() + "]";
    }

    @Override
    public MathContext getMathContext() {
        return mctx;
    }

    @Override
    public IntegerType floor() {
        return new IntegerImpl(BigInteger.valueOf(3L));
    }

    @Override
    public IntegerType ceil() {
        return new IntegerImpl(BigInteger.valueOf(4L));
    }
}
