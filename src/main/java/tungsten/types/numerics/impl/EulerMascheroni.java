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
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.LongStream;

/**
 * The Euler-Mascheroni constant, denoted &#x1D6FE; (the lower-case Greek letter gamma).
 * The decimal value is approximately equal to 0.5772&hellip; and is notoriously difficult
 * to calculate compared to the &ldquo;usual&rdquo; constants.  The algorithm as currently
 * implemented is attributed to Sweeney ca.&nbsp;1963 and described in a paper by
 * Gourdon and Sebah.  It is very efficient, requiring relatively few iterations to
 * converge to a desired value, while also avoiding a bunch of esoteric operations.
 * Note: Gourdon and Sebah also describe a refined version of this series, but this
 * version is simpler and &ldquo;good enough.&rdquo; Note also that the intermediate
 * calculations only require twice the precision of the desired result to allow for
 * correction terms.
 * @author Robert Poole, <a href="mailto:tarquin@alum.mit.edu">MIT alumni e-mail</a>
 *   or <a href="mailto:Tarquin.AZ@gmail.com">Gmail</a>
 * @see tungsten.types.util.MathUtils#gamma(Numeric) the Gamma function
 * @see <a href="https://mathworld.wolfram.com/Euler-MascheroniConstant.html">the article at Wolfram MathWorld</a>
 * @see <a href="https://en.wikipedia.org/wiki/Euler%27s_constant">a confusingly-named article at Wikipedia</a>
 * @see <a href="http://numbers.computation.free.fr/Constants/Gamma/gamma.pdf">a paper by Xavier Gourdon and Pascal Sebah</a>
 *   that goes into some detail regarding techniques of computing &#x1D6FE;
 */
@Constant(name = "euler-gamma", representation = "\uD835\uDEFE")
public class EulerMascheroni implements RealType {
    private final MathContext mctx;
    private BigDecimal value;
    private static final Map<MathContext, EulerMascheroni> instanceMap = new HashMap<>();
    private static final Lock instanceLock = new ReentrantLock();

    protected EulerMascheroni(MathContext mctx) {
        this.mctx = mctx;
        calculate();
    }

    @ConstantFactory(returnType = EulerMascheroni.class)
    public static EulerMascheroni getInstance(MathContext mctx) {
        instanceLock.lock();
        try {
            return instanceMap.computeIfAbsent(mctx, EulerMascheroni::new);
        } finally {
            instanceLock.unlock();
        }
    }

    private static final RealType TEN = new RealImpl(BigDecimal.TEN);

    private void calculate() {
        // explicit advice from Gourdon and Sebah is that we should calculate with 2d digits of precision to get d digits
        final MathContext compCtx = new MathContext(mctx.getPrecision() * 2, mctx.getRoundingMode());
        // This is an approximation of alpha.  The value satisfies the relationship
        // 𝛼(ln(𝛼) - 1) = 1
        final RealImpl alpha = new RealImpl("3.5911", false);
        alpha.setMathContext(compCtx);
        final RealType log10 = MathUtils.ln(TEN, compCtx);
        RealType n = (RealType) new RealImpl(BigDecimal.valueOf(mctx.getPrecision() + 1L), compCtx).multiply(log10);
        IntegerType iterLimit = ((RealType) alpha.multiply(n)).ceil();  // 𝛼⋅n gives us the number of terms to compute

        // Note: I tried to make this stream .parallel(), but this generated a
        // ConcurrentModificationException in ForkJoinTask (used by parallel streams under the covers).
        // This seems strange and counterintuitive, since this stream does not explicitly modify a collection.
        Numeric sum = LongStream.range(1L, iterLimit.asBigInteger().longValueExact())
                .mapToObj(k -> computeTerm(new IntegerImpl(BigInteger.valueOf(k)), n))
                .map(Numeric.class::cast)
                .reduce(ExactZero.getInstance(compCtx), Numeric::add);
        value = OptionalOperations.asBigDecimal(sum.subtract(MathUtils.ln(n, compCtx))).round(mctx);
    }

    private RealType computeTerm(IntegerType k, RealType n) {
        try {
            RealType denom = (RealType) MathUtils.factorial(k).multiply(k).coerceTo(RealType.class); // k⋅k!
            RealType intermediate = (RealType) MathUtils.computeIntegerExponent(n, k).divide(denom);
            if (k.isEven()) intermediate = intermediate.negate();  // originally tested for k - 1 isOdd
            return intermediate;
        } catch (CoercionException fatal) {
            Logger.getLogger(EulerMascheroni.class.getName()).log(Level.SEVERE,
                    "Failed to coerce k\u22C5k! to a real value; k = {0}", k);
            throw new IllegalStateException("While computing the " + k + "th term of \uD835\uDEFE", fatal);
        }
    }

    /**
     * It is not actually known whether this constant is irrational or not.
     * This is one of the great unsolved problems of modern mathematics.
     * We're returning {@code true} here because evidence seems to lean
     * strongly in that direction.
     * @return currently always returns true
     */
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
        return h.compareTo(NumericHierarchy.REAL) >= 0;
    }

    @Override
    public Numeric coerceTo(Class<? extends Numeric> numtype) throws CoercionException {
        if (RealType.class.isAssignableFrom(numtype)) {
            return this;
        } else if (ComplexType.class.isAssignableFrom(numtype)) {
            return new ComplexRectImpl(this);
        }
        throw new CoercionException("Cannot downconvert \uD835\uDEFE", this.getClass(), numtype);
    }

    @Override
    public RealType negate() {
        return new RealImpl(value.negate(mctx), mctx, false) {
            @Override
            public boolean isIrrational() {
                return EulerMascheroni.this.isIrrational();
            }

            @Override
            public RealType negate() {
                return EulerMascheroni.this;
            }

            @Override
            public String toString() {
                return "\u2212\uD835\uDEFE";
            }
        };
    }

    @Override
    public Numeric add(Numeric addend) {
        if (Zero.isZero(addend)) return this;
        if (addend instanceof RealType) {
            RealType that = (RealType) addend;
            return new RealImpl(value.add(that.asBigDecimal(), mctx), mctx, false);
        }
        return addend.add(this);
    }

    @Override
    public Numeric subtract(Numeric subtrahend) {
        if (subtrahend instanceof EulerMascheroni) return ExactZero.getInstance(mctx);
        if (Zero.isZero(subtrahend)) return this;
        if (subtrahend instanceof RealType) {
            RealType that = (RealType) subtrahend;
            return new RealImpl(value.subtract(that.asBigDecimal(), mctx), mctx, false);
        }
        return subtrahend.negate().add(this);
    }

    @Override
    public Numeric multiply(Numeric multiplier) {
        if (One.isUnity(multiplier)) return this;
        if (Zero.isZero(multiplier)) return ExactZero.getInstance(mctx);
        if (multiplier instanceof RealType) {
            RealType that = (RealType) multiplier;
            return new RealImpl(value.multiply(that.asBigDecimal(), mctx), mctx, false);
        }
        return multiplier.multiply(this);
    }

    @Override
    public Numeric divide(Numeric divisor) {
        if (Zero.isZero(divisor)) throw new ArithmeticException("Division by 0");
        if (divisor instanceof EulerMascheroni) return One.getInstance(mctx);
        if (One.isUnity(divisor)) return this;
        if (divisor instanceof RealType) {
            RealType that = (RealType) divisor;
            return new RealImpl(value.divide(that.asBigDecimal(), mctx), mctx, false);
        }
        return divisor.inverse().multiply(this);
    }

    @Override
    public Numeric inverse() {
        return new RealImpl(BigDecimal.ONE.divide(value, mctx), mctx, false) {
            @Override
            public boolean isIrrational() {
                return EulerMascheroni.this.isIrrational();
            }

            @Override
            public Numeric inverse() {
                return EulerMascheroni.this;
            }

            @Override
            public String toString() {
                return "1/\uD835\uDEFE";
            }
        };
    }

    @Override
    public Numeric sqrt() {
        RealImpl root = new RealImpl(value.sqrt(mctx), mctx, false);
        root.setIrrational(true);
        return root;
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
        return new IntegerImpl(value.toBigInteger());
    }

    @Override
    public IntegerType ceil() {
        return new IntegerImpl(value.toBigInteger().add(BigInteger.ONE));
    }

    @Override
    public Set<ComplexType> nthRoots(IntegerType n) {
        return new RealImpl(value, mctx, false).nthRoots(n);
    }

    @Override
    public int compareTo(RealType realType) {
        return value.compareTo(realType.asBigDecimal());
    }

    @Override
    public int hashCode() {
        return Objects.hash(value, mctx);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof EulerMascheroni) {
            return mctx.equals(((EulerMascheroni) obj).getMathContext());
        }
        return false;
    }

    @Override
    public String toString() {
        // precision should always be > 0
        return "\uD835\uDEFE[" + mctx.getPrecision() + "]";
    }
}
