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
import tungsten.types.exceptions.CoercionException;
import tungsten.types.numerics.ComplexType;
import tungsten.types.numerics.IntegerType;
import tungsten.types.numerics.RealType;
import tungsten.types.numerics.Sign;
import tungsten.types.util.OptionalOperations;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A representation of Infinity of {@link RealType}.
 * This representation should be inter-compatible with {@link PosInfinity}
 * and {@link NegInfinity}.  This implementation is more &ldquo;concrete&rdquo;
 * than those other classes, and is suitable for use in real-valued applications.
 *
 * @author Robert Poole <a href="mailto:Tarquin.AZ@gmail.com">Tarquin.AZ@gmail.com</a>
 */
public class RealInfinity implements RealType {
    private static final String ERROR_INVALID_SIGN = "Infinity can only be positive or negative.";

    private final MathContext mctx;
    private final Sign sign;
    private static final Map<MathContext, RealInfinity> posCache = new HashMap<>();
    private static final Map<MathContext, RealInfinity> negCache = new HashMap<>();
    private static final Map<Sign, Map<MathContext, RealInfinity>> cacheMap = new EnumMap<>(Sign.class);
    private static final Map<Sign, Lock> lockMap = new EnumMap<>(Sign.class);
    
    static {
        cacheMap.put(Sign.POSITIVE, posCache);
        cacheMap.put(Sign.NEGATIVE, negCache);
        lockMap.put(Sign.POSITIVE, new ReentrantLock());
        lockMap.put(Sign.NEGATIVE, new ReentrantLock());
    }
    
    protected RealInfinity(Sign sign, MathContext mathContext) {
        if (sign == null || sign == Sign.ZERO) {
            throw new IllegalArgumentException(ERROR_INVALID_SIGN);
        }
        this.sign = sign;
        this.mctx = mathContext;
    }
    
    public static RealType getInstance(Sign sign, MathContext mctx) {
        Map<MathContext, RealInfinity> cache = cacheMap.get(sign);
        lockMap.get(sign).lock();
        try {
            return cache.computeIfAbsent(mctx, ctx -> new RealInfinity(sign, ctx));
        } finally {
            lockMap.get(sign).unlock();
        }
    }

    @Override
    public boolean isIrrational() {
        return false;
    }

    @Override
    public RealType magnitude() {
        return RealInfinity.getInstance(Sign.POSITIVE, mctx);
    }

    @Override
    public RealType negate() {
        return RealInfinity.getInstance(sign.negate(), mctx);
    }

    @Override
    public BigDecimal asBigDecimal() {
        throw new UnsupportedOperationException("There is no BigDecimal representation for Infinity.");
    }

    @Override
    public Sign sign() {
        return sign;
    }

    @Override
    public Set<ComplexType> nthRoots(IntegerType n) {
        throw new UnsupportedOperationException("Not supported.");
    }

    @Override
    public boolean isExact() {
        return false;
    }

    @Override
    public boolean isCoercibleTo(Class<? extends Numeric> numtype) {
        // short circuit if the caller is asking for a Real
        if (numtype.isAssignableFrom(RealType.class)) return true;

        switch (sign) {
            case POSITIVE:
                return numtype.isAssignableFrom(PosInfinity.class);
            case NEGATIVE:
                return numtype.isAssignableFrom(NegInfinity.class);
            default:
                throw new IllegalStateException(ERROR_INVALID_SIGN);
        }
    }

    @Override
    public Numeric coerceTo(Class<? extends Numeric> numtype) throws CoercionException {
        // short circuit coercion to self-type
        if (numtype.isAssignableFrom(RealType.class)) return this;
        
        if (isCoercibleTo(numtype)) {
            switch (sign) {
                case POSITIVE:
                    return PosInfinity.getInstance(mctx);
                case NEGATIVE:
                    return NegInfinity.getInstance(mctx);
            }
        }
        throw new CoercionException("Coercion of " + sign + " Infinity to the requested type is not supported.",
                RealInfinity.class, numtype);
    }

    @Override
    public Numeric add(Numeric addend) {
        if (addend instanceof PosInfinity) {
            return sign == Sign.NEGATIVE ? NegZero.getInstance(mctx) : this;
        } else if (addend instanceof NegInfinity) {
            return sign == Sign.POSITIVE ? PosZero.getInstance(mctx) : this;
        } else if (addend instanceof RealInfinity) {
            final RealInfinity rei = (RealInfinity) addend;
            return rei.sign() != sign ? pickZeroForSign() : this;
        }
        return this;
    }

    private Zero pickZeroForSign() {
        switch (sign) {
            case POSITIVE:
                return (Zero) PosZero.getInstance(mctx);
            case NEGATIVE:
                return (Zero) NegZero.getInstance(mctx);
            default:
                throw new IllegalStateException(ERROR_INVALID_SIGN);
        }
    }

    @Override
    public Numeric subtract(Numeric subtrahend) {
        if (subtrahend instanceof PosInfinity) {
            return sign == Sign.POSITIVE ? PosZero.getInstance(mctx) : this;
        } else if (subtrahend instanceof NegInfinity) {
            return sign == Sign.NEGATIVE ? NegZero.getInstance(mctx) : this;
        } else if (subtrahend instanceof RealInfinity) {
            final RealInfinity rei = (RealInfinity) subtrahend;
            return rei.sign() == sign ? pickZeroForSign() : this;
        }
        return this;
    }

    @Override
    public Numeric multiply(Numeric multiplier) {
        if (Zero.isZero(multiplier)) {
            // This seems to be a sane behavior, but might change in the future.
            return ExactZero.getInstance(mctx);
        }
        if (multiplier instanceof NegInfinity) {
            return sign == Sign.NEGATIVE ? PosInfinity.getInstance(mctx) : NegInfinity.getInstance(mctx);
        }
        if (multiplier instanceof PosInfinity) {
            return sign == Sign.POSITIVE ? PosInfinity.getInstance(mctx) : NegInfinity.getInstance(mctx);
        }
        if (OptionalOperations.sign(multiplier) != sign) {
            return RealInfinity.getInstance(sign.negate(), mctx);
        }
        return this;
    }

    @Override
    public Numeric divide(Numeric divisor) {
        if (Zero.isZero(divisor)) {
            throw new ArithmeticException("Division of infinity by zero is currently undefined");
        }
        if (divisor instanceof NegInfinity) {
            return sign == Sign.POSITIVE ? One.getInstance(mctx).negate() : One.getInstance(mctx);
        }
        if (divisor instanceof PosInfinity) {
            return sign == Sign.NEGATIVE ? One.getInstance(mctx).negate() : One.getInstance(mctx);
        }
        if (divisor instanceof RealInfinity) {
            final RealInfinity dval = (RealInfinity) divisor;
            return sign != dval.sign() ? One.getInstance(mctx).negate() : One.getInstance(mctx);
        }
        if (OptionalOperations.sign(divisor) != sign) {
            return RealInfinity.getInstance(sign.negate(), mctx);
        }
        return this;
    }

    @Override
    public Numeric inverse() {
        switch (sign) {
            case POSITIVE:
                return PosZero.getInstance(mctx);
            case NEGATIVE:
                return NegZero.getInstance(mctx);
            default:
                throw new IllegalStateException(ERROR_INVALID_SIGN);
        }
    }

    @Override
    public Numeric sqrt() {
        switch (sign) {
            case POSITIVE:
                return this;
            case NEGATIVE:
                return new ComplexRectImpl(getZeroInstance(),
                        RealInfinity.getInstance(Sign.POSITIVE, mctx));
            default:
                throw new IllegalStateException(ERROR_INVALID_SIGN);
        }
    }
    
    private RealType getZeroInstance() {
        try {
            return (RealType) ExactZero.getInstance(mctx).coerceTo(RealType.class);
        } catch (CoercionException ex) {
            Logger.getLogger(RealInfinity.class.getName()).log(Level.SEVERE,
                    "Unable to obtain a Real instance of Zero.", ex);
            throw new IllegalStateException(ex);
        }
    }

    @Override
    public MathContext getMathContext() {
        return mctx;
    }

    @Override
    public int compareTo(RealType t) {
        if (t instanceof RealInfinity && t.sign() == sign) return 0;
        switch (sign) {
            case POSITIVE:
                return 1;
            case NEGATIVE:
                return -1;
            default:
                throw new IllegalStateException(ERROR_INVALID_SIGN);
        }
    }
    
    @Override
    public boolean equals(Object o) {
        if (o instanceof RealInfinity) {
            final RealInfinity that = (RealInfinity) o;
            return this.sign == that.sign();
        }
        if (o instanceof PosInfinity) {
            return this.sign == Sign.POSITIVE;
        }
        if (o instanceof NegInfinity) {
            return this.sign == Sign.NEGATIVE;
        }
        return false;
    }

    @Override
    public int hashCode() {
        return sign == Sign.POSITIVE ? Integer.MAX_VALUE : Integer.MIN_VALUE;
    }
    
    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder();
        buf.append(sign.getSymbol()).append("\u221E");
        return buf.toString();
    }

    @Override
    public IntegerType floor() {
        throw new ArithmeticException("floor() not supported");
    }

    @Override
    public IntegerType ceil() {
        throw new ArithmeticException("ceil() not supported");
    }
}
