package tungsten.types.numerics.impl;
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
import tungsten.types.Set;
import tungsten.types.annotations.Constant;
import tungsten.types.annotations.ConstantFactory;
import tungsten.types.exceptions.CoercionException;
import tungsten.types.numerics.*;
import tungsten.types.util.MathUtils;
import tungsten.types.util.OptionalOperations;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The Golden Ratio, denoted by &#x03D5; (the Greek letter phi).
 * Phi is perhaps not as important as Euler's number or &pi;,
 * but it shows up in a lot of places.
 * @author Robert Poole, <a href="mailto:tarquin@alum.mit.edu">MIT alumni e-mail</a> or
 *  <a href="mailto:Tarquin.AZ@gmail.com">Gmail</a>
 * @see tungsten.types.set.impl.FibonacciNumbers
 * @see <a href="https://en.wikipedia.org/wiki/Golden_ratio">the Wikipedia article on Phi</a>
 * @see ContinuedFraction#phi(MathContext) the factory method to generate a continued fraction of &#x03D5;
 */
@Constant(name = "phi", representation = "\u03D5")
public class Phi implements RealType {
    private final MathContext mctx;
    private final BigDecimal value;
    private static final Map<MathContext, Phi> instanceMap = new ConcurrentHashMap<>();

    protected Phi(MathContext mctx) {
        this.mctx = mctx;
        BigDecimal two = BigDecimal.valueOf(2L);
        BigDecimal five = BigDecimal.valueOf(5L);
        this.value = BigDecimal.ONE.add(five.sqrt(mctx), mctx).divide(two, mctx);
    }

    @ConstantFactory(returnType = Phi.class)
    public static Phi getInstance(MathContext mctx) {
        return instanceMap.computeIfAbsent(mctx, Phi::new);
    }

    @Override
    public boolean isIrrational() {
        return true;
    }

    @Override
    public RealType magnitude() {
        return this;
    }

    @Override
    public boolean isExact() {
        return false;
    }

    @Override
    public boolean isCoercibleTo(Class<? extends Numeric> numtype) {
        NumericHierarchy h = NumericHierarchy.forNumericType(numtype);
        if (h == null) return false;
        return h.compareTo(NumericHierarchy.REAL) >= 0;
    }

    @Override
    public Numeric coerceTo(Class<? extends Numeric> numtype) throws CoercionException {
        if (RealType.class.isAssignableFrom(numtype)) return this;
        else if (ComplexType.class.isAssignableFrom(numtype)) {
            return new ComplexPolarImpl(this);
        }
        throw new CoercionException("Phi cannot be coerced to " + numtype.getTypeName(), this.getClass(), numtype);
    }

    @Override
    public RealType negate() {
        return new RealImpl(value.negate(), mctx, false) {
            @Override
            public boolean isIrrational() {
                return true;
            }

            @Override
            public RealType negate() {
                return Phi.this;
            }

            @Override
            public Numeric add(Numeric addend) {
                if (addend instanceof Phi) return ExactZero.getInstance(mctx);
                return super.add(addend);
            }

            @Override
            public String toString() {
                return "\u2212\u03D5";
            }
        };
    }

    @Override
    public Numeric add(Numeric addend) {
        if (addend instanceof ComplexType) {
            return addend.add(this);
        } else if (Zero.isZero(addend)) {
            return this;
        }
        BigDecimal other = OptionalOperations.asBigDecimal(addend);
        return new RealImpl(value.add(other, mctx), mctx, false);
    }

    @Override
    public Numeric subtract(Numeric subtrahend) {
        if (subtrahend instanceof Phi) return ExactZero.getInstance(mctx);
        if (subtrahend instanceof ComplexType) {
            return subtrahend.negate().add(this);
        } else if (Zero.isZero(subtrahend)) {
            return this;
        } else if (One.isUnity(subtrahend)) {
            // recurrence relationship: ϕ - 1 = 1/ϕ
            return inverse();
        }
        BigDecimal other = OptionalOperations.asBigDecimal(subtrahend);
        return new RealImpl(value.subtract(other, mctx), mctx, false);
    }

    @Override
    public Numeric multiply(Numeric multiplier) {
        if (multiplier instanceof ComplexType) {
            return multiplier.multiply(this);
        } else if (One.isUnity(multiplier)) {
            return this;
        }
        BigDecimal other = OptionalOperations.asBigDecimal(multiplier);
        return new RealImpl(value.multiply(other, mctx), mctx, false);
    }

    @Override
    public Numeric divide(Numeric divisor) {
        if (divisor instanceof Phi) return One.getInstance(mctx);
        if (divisor instanceof ComplexType) {
            return divisor.inverse().multiply(this);
        } else if (One.isUnity(divisor)) {
            return this;
        }
        BigDecimal other = OptionalOperations.asBigDecimal(divisor);
        return new RealImpl(value.divide(other, mctx), mctx, false);
    }

    @Override
    public RealType inverse() {
        // recurrence relationship: 1/ϕ = ϕ - 1
        // this is cheaper (faster) than using division to compute the inverse
        // and arguably just as accurate
        return new RealImpl(value.subtract(BigDecimal.ONE), mctx, false) {
            @Override
            public boolean isIrrational() {
                return true;
            }

            @Override
            public Numeric inverse() {
                return Phi.this;
            }

            @Override
            public Numeric add(Numeric addend) {
                if (One.isUnity(addend)) {
                    // recurrence relationship: 1 + 1/ϕ = ϕ
                    return Phi.this;
                }
                return super.add(addend);
            }

            @Override
            public Numeric multiply(Numeric multiplier) {
                if (multiplier instanceof Phi) return One.getInstance(mctx);
                return super.multiply(multiplier);
            }

            @Override
            public String toString() {
                return "1/\u03D5";
            }
        };
    }

    @Override
    public Numeric sqrt() {
        RealImpl result = new RealImpl(value.sqrt(mctx), mctx, false);
        result.setIrrational(true);
        return result;
    }

    @Override
    public MathContext getMathContext() {
        return mctx;
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
    public IntegerType floor() {
        return new IntegerImpl(BigInteger.ONE);
    }

    @Override
    public IntegerType ceil() {
        return new IntegerImpl(BigInteger.TWO);
    }

    @Override
    public Set<ComplexType> nthRoots(IntegerType n) {
        return new RealImpl(value, mctx, false).nthRoots(n);
    }

    @Override
    public int compareTo(RealType realType) {
        if (MathUtils.isInfinity(realType, Sign.POSITIVE)) return -1;
        if (MathUtils.isInfinity(realType, Sign.NEGATIVE)) return 1;
        return value.compareTo(realType.asBigDecimal());
    }

    @Override
    public String toString() {
        return "\u03D5[" + mctx.getPrecision() + "]";
    }

    @Override
    public int hashCode() {
        return Objects.hash(value, mctx);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof Phi) {
            return mctx.equals(((Phi) obj).getMathContext());
        }
        return false;
    }
}
