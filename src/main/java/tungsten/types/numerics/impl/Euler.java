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
import tungsten.types.annotations.ConstantFactory;
import tungsten.types.exceptions.CoercionException;
import tungsten.types.numerics.*;
import tungsten.types.util.MathUtils;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.IntStream;

/**
 * This class provides a representation of the mathematical constant &#x212f; &mdash;
 * also known as Euler's number.
 * The class is not publicly instantiable; it provides a factory method
 * that will return an instance of itself for a given {@link MathContext},
 * and keeps a cache of instances that have been generated so that the value
 * of &#x212f; only needs to be calculated once for a given precision and
 * {@link RoundingMode}.
 * <br>
 * Internally, this class uses Brothers' formula for deriving &#x212f; to an
 * arbitrary precision.
 *
 * @author Robert Poole, <a href="mailto:Tarquin.AZ@gmail.com">Tarquin.AZ@gmail.com</a> or
 *   <a href="mailto:tarquin@alum.mit.edu">MIT alumni e-mail</a>
 * @see <a href="https://www.intmath.com/exponential-logarithmic-functions/calculating-e.php">an article at Interactive Mathematics about ways to calculate &#x212f;</a>
 * @see <a href="https://en.wikipedia.org/wiki/E_(mathematical_constant)">the Wikipedia article about this constant</a>
 * @see ContinuedFraction#euler(MathContext) the factory method to generate a continued fraction of &#x212f;
 */
@Constant(name = "euler", representation="\u212F")
public class Euler implements RealType {
    private final MathContext mctx;
    private BigDecimal value;

    private Euler(MathContext mctx) {
        this.mctx = mctx;
        calculate();
    }
    
    private static final Lock instanceLock = new ReentrantLock();
    private static final Map<MathContext, Euler> instanceMap = new HashMap<>();
    
    /**
     * Factory method for obtaining an instance of &#x212f; at a given precision.
     * @param mctx provides the desired precision and {@link RoundingMode} used for internal calculations
     * @return an instance of &#x212f; to the specified precision
     */
    @ConstantFactory(returnType = Euler.class)
    public static Euler getInstance(MathContext mctx) {
        instanceLock.lock();
        try {
            Euler instance = instanceMap.get(mctx);
            if (instance == null) {
                instance = new Euler(mctx);
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
        RealImpl magnitude = new RealImpl(value, mctx, false);
        magnitude.setIrrational(true);
        return magnitude;
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
                return Euler.this;
            }

            @Override
            public Numeric add(Numeric addend) {
                if (addend instanceof Euler) return ExactZero.getInstance(mctx);
                return super.add(addend);
            }

            @Override
            public String toString() {
                return "\u2212\u212F";
            }
        };
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
                throw new CoercionException("Euler can only be coerced to real or complex",
                        this.getClass(), numtype);
        }
    }

    @Override
    public Numeric add(Numeric addend) {
        if (addend instanceof Euler) {
            // to avoid a stack overflow
            RealImpl real = new RealImpl(value.multiply(TWO), mctx, false);
            real.setIrrational(true);
            return real;
        }
        return addend.add(this);
    }

    @Override
    public Numeric subtract(Numeric subtrahend) {
        if (subtrahend instanceof Euler) return ExactZero.getInstance(mctx);
        return subtrahend.negate().add(this);
    }

    @Override
    public Numeric multiply(Numeric multiplier) {
        if (multiplier instanceof Euler) {
            // to avoid a stack overflow
            RealImpl real = new RealImpl(value.pow(2, mctx), mctx, false);
            real.setIrrational(true);
            return real;
        }
        return multiplier.multiply(this);
    }

    @Override
    public Numeric divide(Numeric divisor) {
        if (divisor instanceof Euler) return One.getInstance(mctx);
        return divisor.inverse().multiply(this);
    }

    @Override
    public RealType inverse() {
        return new RealImpl(BigDecimal.ONE.divide(value, mctx), mctx, false) {
            @Override
            public boolean isIrrational() {
                return true;
            }

            @Override
            public Numeric inverse() {
                return Euler.this;
            }

            @Override
            public Numeric multiply(Numeric multiplier) {
                if (multiplier instanceof Euler) return One.getInstance(mctx);
                return super.multiply(multiplier);
            }

            @Override
            public String toString() {
                return "1/\u212F";
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
    public Set<ComplexType> nthRoots(IntegerType n) {
        return magnitude().nthRoots(n);
    }
    
    public long numberOfDigits() {
        return mctx.getPrecision();
    }
    
    private void calculate() {
        // compute a few extra digits so we can round off later
        final MathContext compctx = new MathContext(mctx.getPrecision() + 4, mctx.getRoundingMode());
        BigDecimal value = IntStream.range(0, mctx.getPrecision() / 2).parallel()
                .mapToObj(k -> computeKthTerm(k, compctx))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        this.value = value.round(mctx);
    }
    
    private static final BigDecimal TWO = BigDecimal.valueOf(2L);

    private BigDecimal computeKthTerm(int k, MathContext ctx) {
        BigDecimal numerator = TWO.multiply(BigDecimal.valueOf(k), ctx).add(TWO, ctx);
        BigInteger innerDenom = BigInteger.TWO.multiply(BigInteger.valueOf(k)).add(BigInteger.ONE);
        IntegerType denominator = MathUtils.factorial(new IntegerImpl(innerDenom));
        
        return numerator.divide(new BigDecimal(denominator.asBigInteger(), ctx), ctx);
    }
    
    /**
     * Compute &#x212f;<sup>x</sup> for real-valued x.
     * @param x the real-valued exponent
     * @return &#x212f;<sup>x</sup>
     */
    public RealType exp(RealType x) {
        // we need to do this check first since asBigDecimal will fail for RealInfinity
        if (x instanceof RealInfinity) {
            switch (x.sign()) {
                case NEGATIVE:
                    return new RealImpl(BigDecimal.ZERO, mctx, false);
                case POSITIVE:
                    return x;
                default:
                    throw new IllegalStateException("Unknown state for " + x);
            }
        }
        if (x.asBigDecimal().compareTo(BigDecimal.ZERO) == 0) return new RealImpl(BigDecimal.ONE, mctx);
        else if (x.asBigDecimal().compareTo(BigDecimal.ONE) == 0) return this;
        else if (x.asBigDecimal().compareTo(BigDecimal.ONE.negate()) == 0) return this.inverse();

        // otherwise compute the series
        final MathContext compctx = new MathContext(mctx.getPrecision() + 4, mctx.getRoundingMode());
        BigDecimal sum = IntStream.range(0, mctx.getPrecision()).parallel()
                .mapToObj(n -> computeNthTerm(n, x, compctx)).reduce(BigDecimal.ZERO, BigDecimal::add);
        RealImpl result = new RealImpl(sum.round(mctx), mctx, false);
        result.setIrrational(true);
        return result;
    }
    
    /**
     * Compute &#x212f;<sup>z</sup> for complex-valued z.
     * @param z the complex-valued exponent
     * @return &#x212f;<sup>z</sup>
     */
    public ComplexType exp(ComplexType z) {
        if (z.isCoercibleTo(RealType.class)) {
            return new ComplexRectImpl(exp(z.real()), new RealImpl(BigDecimal.ZERO, mctx), false);
        }
        
        // e^(x+iy) = (e^x)*(e^iy), where e^x becomes the modulus and y becomes the argument
        final ComplexPolarImpl polarval = new ComplexPolarImpl(exp(z.real()), z.imaginary(), false);
        polarval.setMathContext(mctx);
        
        return polarval;
    }
    
    private BigDecimal computeNthTerm(int n, RealType x, MathContext ctx) {
        RealType numerator = MathUtils.computeIntegerExponent(x, n, ctx);
        IntegerType denominator = MathUtils.factorial(new IntegerImpl(BigInteger.valueOf(n)));
        try {
            // a little clunky, but this is not publicly visible...
            return ((RealType) numerator.divide(denominator).coerceTo(RealType.class)).asBigDecimal();
        } catch (CoercionException ex) {
            Logger.getLogger(Euler.class.getName()).log(Level.SEVERE, "Failed to compute term " + n + " of exp series.", ex);
            throw new ArithmeticException("Fatal error while computing exp()");
        }
    }

    @Override
    public MathContext getMathContext() {
        return mctx;
    }

    @Override
    public int compareTo(RealType o) {
        if (MathUtils.isInfinity(o, Sign.POSITIVE)) return -1;
        if (MathUtils.isInfinity(o, Sign.NEGATIVE)) return 1;
        if (o instanceof ContinuedFraction) {
            // avoid the BigDecimal conversion
            return ContinuedFraction.euler(mctx).compareTo(o);
        }
        return this.value.compareTo(o.asBigDecimal());
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof Euler) {
            Euler that = (Euler) o;
            if (this.mctx.getRoundingMode() != that.mctx.getRoundingMode()) return false;
            return this.numberOfDigits() == that.numberOfDigits();
        }
        return false;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 11 * hash + Objects.hashCode(this.mctx);
        hash = 11 * hash + Objects.hashCode(this.value);
        return hash;
    }

    @Override
    public String toString() {
        return "\u212F[" + this.numberOfDigits() + "]";
    }

    @Override
    public IntegerType floor() {
        return new IntegerImpl(value.toBigInteger());
    }

    @Override
    public IntegerType ceil() {
        return new IntegerImpl(value.toBigInteger().add(BigInteger.ONE));
    }

    /*
     Groovy methods below.
     */
    public Object asType(Class<?> clazz) {
        if (CharSequence.class.isAssignableFrom(clazz)) {
            if (clazz == StringBuilder.class) return new StringBuilder().append(value.toPlainString());
            return value.toPlainString();
        }
        return RealType.super.asType(clazz);
    }

    public ComplexType power(ComplexType operand) {
        return this.exp(operand);
    }
    public RealType power(RealType operand) {
        return this.exp(operand);
    }
    public RealType power(RationalType operand) {
        MathContext ctx = MathUtils.inferMathContext(List.of(this, operand));
        RealType converted = new RealImpl(operand.asBigDecimal(), ctx);
        return this.exp(converted);
    }
    public RealType power(IntegerType operand) {
        return MathUtils.computeIntegerExponent(this, operand);
    }
}
