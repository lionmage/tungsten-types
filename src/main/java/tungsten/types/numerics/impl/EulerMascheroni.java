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
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.LongStream;

/**
 * The Euler-Mascheroni constant, denoted &#x1D6FE; (the lower-case Greek letter gamma).
 * The decimal value is approximately equal to 0.5772&hellip; and is notoriously difficult
 * to calculate compared to the &ldquo;usual&rdquo; constants.  The algorithm currently
 * implemented in this class uses a convergent infinite sum attributed to Euler.
 * Because each term of this sum contains ln(x), the sum is computationally expensive.
 * Using ~&thinsp;450N as the iteration limit where N is the requested precision of the result,
 * for {@link MathContext#DECIMAL128}, we get approximately 4 digits of accuracy.
 * This is poor performance considering the computational cost!  This will hopefully improve
 * very soon.
 * @author Robert Poole, <a href="mailto:tarquin@alum.mit.edu">MIT alumni e-mail</a>
 *   or <a href="mailto:Tarquin.AZ@gmail.com">Gmail</a>
 * @see tungsten.types.util.MathUtils#gamma(Numeric) the Gamma function
 * @see <a href="https://mathworld.wolfram.com/Euler-MascheroniConstant.html">the article at Wolfram MathWorld</a>
 * @see <a href="https://en.wikipedia.org/wiki/Euler%27s_constant">a confusingly-named article at Wikipedia</a>
 */
@Constant(name = "euler-gamma", representation = "\uD835\uDEFE")
public class EulerMascheroni implements RealType {
    private MathContext mctx;
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

    // Vacca's method, simple but doesn't converge very fast
    private void calculate() {
        final long iterLimit = (long) mctx.getPrecision() * 10_000L;

        Numeric result = LongStream.range(1L, iterLimit).parallel()
                .mapToObj(n -> {
                    IntegerType N = new IntegerImpl(BigInteger.valueOf(n));
                    RationalImpl interim = new RationalImpl(MathUtils.log2floor(N), N, mctx);
                    if (n % 2L == 1L) return interim.negate();
                    return interim;
                }).map(Numeric.class::cast).reduce(ExactZero.getInstance(mctx), Numeric::add);
        value = OptionalOperations.asBigDecimal(result);
    }

    // Euler's method
//    private void calculate() {
//        final long iterLimit = (long) mctx.getPrecision() * 450L + 9L;
//        // using an expansion discovered by who else? Euler
//        Numeric result = LongStream.range(1L, iterLimit).mapToObj(this::computeKthTerm)
//                .reduce(ExactZero.getInstance(mctx), Numeric::add);
//        try {
//            RealType real = (RealType) result.coerceTo(RealType.class);
//            value = real.asBigDecimal().round(mctx);
//        } catch (CoercionException fatal) {
//            Logger.getLogger(EulerMascheroni.class.getName()).log(Level.SEVERE,
//                    "Failed to instantiate \uD835\uDEFE for MathContext " + mctx, fatal);
//            throw new IllegalStateException("Fatal error while instantiating \uD835\uDEFE", fatal);
//        }
//    }

    // used by Euler's method above
//    private Numeric computeKthTerm(long k) {
//        MathContext calcCtx = new MathContext(mctx.getPrecision() * 2, mctx.getRoundingMode());
//        RationalType kInv = new RationalImpl(1L, k, calcCtx);
//        final Numeric one = One.getInstance(calcCtx);
//        try {
//            return kInv.subtract(MathUtils.ln((RealType) one.add(kInv).coerceTo(RealType.class)));
//        } catch (CoercionException e) {
//            throw new ArithmeticException("While computing term " + k + " of \uD835\uDEFE: " + e.getMessage());
//        }
//    }

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
        return false;
    }

    @Override
    public Numeric coerceTo(Class<? extends Numeric> numtype) throws CoercionException {
        return RealType.super.coerceTo(numtype);
    }

    @Override
    public RealType negate() {
        return new RealImpl(value.negate(mctx),mctx, false);
    }

    @Override
    public Numeric add(Numeric addend) {
        if (addend instanceof RealType) {
            RealType that = (RealType) addend;
            return new RealImpl(value.add(that.asBigDecimal(), mctx), mctx, false);
        }
        return addend.add(this);
    }

    @Override
    public Numeric subtract(Numeric subtrahend) {
        if (subtrahend instanceof RealType) {
            RealType that = (RealType) subtrahend;
            return new RealImpl(value.subtract(that.asBigDecimal(), mctx), mctx, false);
        }
        return subtrahend.negate().add(this);
    }

    @Override
    public Numeric multiply(Numeric multiplier) {
        if (multiplier instanceof RealType) {
            RealType that = (RealType) multiplier;
            return new RealImpl(value.multiply(that.asBigDecimal(), mctx), mctx, false);
        }
        return multiplier.multiply(this);
    }

    @Override
    public Numeric divide(Numeric divisor) {
        if (divisor instanceof RealType) {
            RealType that = (RealType) divisor;
            return new RealImpl(value.divide(that.asBigDecimal(), mctx), mctx, false);
        }
        return divisor.inverse().multiply(this);
    }

    @Override
    public Numeric inverse() {
        return new RealImpl(BigDecimal.ONE.divide(value, mctx), mctx, false);
    }

    @Override
    public Numeric sqrt() {
        return new RealImpl(value.sqrt(mctx), mctx, false);
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
    public String toString() {
        // precision should always be > 0
        return "\uD835\uDEFE[" + mctx.getPrecision() + "]";
    }
}
