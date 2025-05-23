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
import tungsten.types.annotations.Constant;
import tungsten.types.annotations.ConstantFactory;
import tungsten.types.exceptions.CoercionException;
import tungsten.types.numerics.RealType;
import tungsten.types.numerics.Sign;
import tungsten.types.util.MathUtils;
import tungsten.types.util.OptionalOperations;

import java.math.MathContext;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * An abstract representation of negative infinity.
 *
 * @author Robert Poole <a href="mailto:Tarquin.AZ@gmail.com">Tarquin.AZ@gmail.com</a>
 */
@Constant(name = "-infinity", representation = "\u2212\u221E")
public class NegInfinity implements Numeric, Comparable<Numeric> {
    private final MathContext mctx;

    private NegInfinity(MathContext mctx) {
        this.mctx = mctx;
    }
    
    private static final Map<MathContext, NegInfinity> instanceMap = new HashMap<>();
    private static final Lock instanceLock = new ReentrantLock();

    @ConstantFactory
    public static Numeric getInstance(MathContext mctx) {
        instanceLock.lock();
        try {
            return instanceMap.computeIfAbsent(mctx, NegInfinity::new);
        } finally {
            instanceLock.unlock();
        }
    }
    
    @Override
    public boolean isExact() {
        return false;
    }

    @Override
    public boolean isCoercibleTo(Class<? extends Numeric> numtype) {
        return numtype.isAssignableFrom(RealInfinity.class);
    }

    @Override
    public Numeric coerceTo(Class<? extends Numeric> numtype) throws CoercionException {
        // works for RealInfinity, RealType, or tungsten.types.Numeric
        if (numtype.isAssignableFrom(RealInfinity.class)) {
            return RealInfinity.getInstance(Sign.NEGATIVE, mctx);
        }
        throw new CoercionException("Cannot coerce infinity to any other tungsten.types.Numeric type", this.getClass(), numtype);
    }

    @Override
    public Numeric magnitude() {
        return PosInfinity.getInstance(mctx);
    }

    @Override
    public Numeric negate() {
        return PosInfinity.getInstance(mctx);
    }

    @Override
    public Numeric add(Numeric addend) {
        if (addend instanceof PosInfinity) return NegZero.getInstance(mctx);
        return this;
    }

    @Override
    public Numeric subtract(Numeric subtrahend) {
        if (subtrahend instanceof NegInfinity) return NegZero.getInstance(mctx);
        return this;
    }

    @Override
    public Numeric multiply(Numeric multiplier) {
        if (multiplier instanceof NegInfinity) return PosInfinity.getInstance(mctx);
        if (OptionalOperations.sign(multiplier) == Sign.NEGATIVE) {
            return PosInfinity.getInstance(mctx);
        }
        return this;
    }

    @Override
    public Numeric divide(Numeric divisor) {
        if (divisor instanceof NegInfinity) return One.getInstance(mctx);
        if (divisor instanceof PosInfinity) return One.getInstance(mctx).negate();
        if (OptionalOperations.sign(divisor) == Sign.NEGATIVE) {
            return PosInfinity.getInstance(mctx);
        }
        return this;
    }

    @Override
    public Numeric inverse() {
        return NegZero.getInstance(mctx);
    }

    @Override
    public Numeric sqrt() {
        return RealInfinity.getInstance(Sign.NEGATIVE, mctx).sqrt();
    }

    @Override
    public MathContext getMathContext() {
        return mctx;
    }

    @Override
    public int compareTo(Numeric o) {
        if (o instanceof Comparable) {
            if (MathUtils.isInfinity(o, Sign.NEGATIVE)) return 0;
            // negative infinity is always less than any other value
            return -1;
        } else {
            throw new UnsupportedOperationException("Comparison to " + o.getClass().getTypeName() + " is not supported");
        }
    }

    public Sign sign() { return Sign.NEGATIVE; }
    
    @Override
    public boolean equals(Object o) {
        if (o instanceof RealInfinity) {
            return ((RealType) o).sign() == Sign.NEGATIVE;
        }
        return o instanceof NegInfinity;
    }
    
    @Override
    public int hashCode() {
        return Integer.MIN_VALUE;
    }
    
    @Override
    public String toString() {
        return "\u2212\u221E";
    }

    /*
     Groovy methods below.
     */
    public Numeric power(Numeric operand) {
        if (Zero.isZero(operand)) {
            return One.getInstance(mctx);
        } else if (One.isUnity(operand)) {
            // exponent = 1, so return this value
            return this;
        } else if (One.isUnity(operand.negate())) {
            // exponent = -1, so compute the inverse
            return this.inverse();
        }
        throw new ArithmeticException("Raising negative infinity to " + operand + " is currently undefined");
    }
}
