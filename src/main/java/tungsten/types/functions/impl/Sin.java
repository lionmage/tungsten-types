package tungsten.types.functions.impl;
/*
 * The MIT License
 *
 * Copyright © 2022 Robert Poole <Tarquin.AZ@gmail.com>.
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
import tungsten.types.Range;
import tungsten.types.annotations.Differentiable;
import tungsten.types.functions.ArgVector;
import tungsten.types.functions.Periodic;
import tungsten.types.functions.Proxable;
import tungsten.types.functions.UnaryFunction;
import tungsten.types.numerics.IntegerType;
import tungsten.types.numerics.RationalType;
import tungsten.types.numerics.RealType;
import tungsten.types.numerics.impl.IntegerImpl;
import tungsten.types.numerics.impl.Pi;
import tungsten.types.numerics.impl.RationalImpl;
import tungsten.types.numerics.impl.RealImpl;
import tungsten.types.util.MathUtils;
import tungsten.types.util.RangeUtils;
import tungsten.types.util.UnicodeTextEffects;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * A function that implements sine for real-valued inputs.
 * This implementation has a set of pre-computed values for
 * certain magic angles, and is treated as periodic.  Aside from a table of fixed
 * values, this function delegates to {@link MathUtils#sin(RealType)}.
 * @author Robert Poole, <a href="mailto:Tarquin.AZ@gmail.com">Gmail</a>
 */
public class Sin extends UnaryFunction<RealType, RealType> implements Proxable<RealType, RealType>, Periodic {
    private final MathContext mctx;

    private final Map<RealType, RealType> knownValues = new LinkedHashMap<>();
    private final Range<RealType> internalRange;

    /**
     * Constructor for this function which takes an argument name and
     * a {@link MathContext}.
     * 
     * @param argName the name of the sole argument to this function
     * @param mctx the {@link MathContext} governing the precision and rounding of this function
     */
    public Sin(String argName, MathContext mctx) {
        super(argName, RealType.class);
        this.mctx = mctx;
        RealType decTwo = new RealImpl(BigDecimal.valueOf(2L), mctx);
        // since the table of internal values is specified over the range 0 to 2𝜋,
        // set up the internal range appropriately
        internalRange = new Range<>(new RealImpl(BigDecimal.ZERO, mctx), Range.BoundType.INCLUSIVE,
                (RealType) Pi.getInstance(mctx).multiply(decTwo), Range.BoundType.EXCLUSIVE);
        initializeTable();
    }

    /**
     * Constructor which takes a {@link MathContext} value and uses the default value for
     * this function's argument name, i.e. &theta;
     *
     * @param mctx the math context for this function instance
     */
    public Sin(MathContext mctx) {
        this(MathUtils.THETA, mctx);
    }

    private void initializeTable() {
        final Pi pi = Pi.getInstance(mctx);
        final RealType zero = new RealImpl(BigDecimal.ZERO, mctx);
        final RealType one = new RealImpl(BigDecimal.ONE, mctx);
        final RealType two = new RealImpl(BigDecimal.valueOf(2L), mctx);
        final IntegerType four = new IntegerImpl("4") {
            @Override
            public MathContext getMathContext() {
                return mctx;
            }
        };
        final RealType ten = new RealImpl(BigDecimal.TEN, mctx);
        final RationalType oneHalf = new RationalImpl("1/2", mctx);
        final RealType sqrtTwo = (RealType) two.sqrt();
        final RealType sqrtThree = (RealType) new RealImpl(BigDecimal.valueOf(3L), mctx).sqrt();
        final RealType sqrtFive = (RealType) new RealImpl(BigDecimal.valueOf(5L), mctx).sqrt();

        knownValues.put(zero, zero);
        knownValues.put(piDividedBy(24L), (RealType) sqrtThree.add(two).sqrt().negate()
                .add(two).sqrt().multiply(oneHalf));
        knownValues.put(piDividedBy(12L), (RealType) sqrtThree.subtract(one)
                .multiply(sqrtTwo).divide(four));
        knownValues.put(piDividedBy(10L), (RealType) sqrtFive.subtract(one).divide(four));
        knownValues.put(piDividedBy(8L), (RealType) two.subtract(sqrtTwo).sqrt().multiply(oneHalf));
        knownValues.put(piDividedBy(6L), new RealImpl(oneHalf, mctx));
        knownValues.put(piDividedBy(5L), (RealType) ten.subtract(two.multiply(sqrtFive)).sqrt().divide(four));
        knownValues.put(piDividedBy(4L), (RealType) sqrtTwo.divide(two));
        knownValues.put(piFraction(new RationalImpl("3/10")),
                (RealType) one.add(sqrtFive).divide(four));
        knownValues.put(piDividedBy(3L), (RealType) sqrtThree.divide(two));
        knownValues.put(piFraction(new RationalImpl("3/8")),
                (RealType) sqrtTwo.add(two).sqrt().divide(two));
        knownValues.put(piFraction(new RationalImpl("2/5")),
                (RealType) ten.add(two.multiply(sqrtFive)).sqrt().divide(four));
        knownValues.put(piFraction(new RationalImpl("5/12", mctx)),
                (RealType) sqrtThree.add(one).multiply(sqrtTwo).divide(four));
        knownValues.put(piDividedBy(2L), one);
        knownValues.put(piFraction(new RationalImpl("7/12", mctx)),
                (RealType) sqrtThree.add(one).multiply(sqrtTwo).divide(four));
        knownValues.put(piFraction(new RationalImpl("2/3", mctx)), (RealType) sqrtThree.divide(two));
        knownValues.put(piFraction(new RationalImpl("3/4")), (RealType) sqrtTwo.divide(two));
        knownValues.put(piFraction(new RationalImpl("5/6", mctx)), new RealImpl(oneHalf, mctx));
        knownValues.put(piFraction(new RationalImpl("11/12", mctx)),
                (RealType) sqrtThree.subtract(one).multiply(sqrtTwo).divide(four));
        knownValues.put(pi, zero);
        knownValues.put(piFraction(new RationalImpl("13/12", mctx)),
                (RealType) sqrtThree.subtract(one).divide(two.multiply(sqrtTwo)).negate());
        knownValues.put(piFraction(new RationalImpl("7/6", mctx)),
                new RealImpl(oneHalf.negate(), mctx));
        knownValues.put(piFraction(new RationalImpl("5/4")), (RealType) sqrtTwo.divide(two).negate());
        knownValues.put(piFraction(new RationalImpl("4/3", mctx)), (RealType) sqrtThree.divide(two).negate());
        knownValues.put(piFraction(new RationalImpl("17/12", mctx)),
                ((RealType) sqrtThree.add(one).multiply(sqrtTwo).divide(four)).negate());
        knownValues.put(piFraction(new RationalImpl("3/2")), one.negate());
        knownValues.put(piFraction(new RationalImpl("19/12", mctx)),
                ((RealType) sqrtThree.add(one).multiply(sqrtTwo).divide(four)).negate());
        knownValues.put(piFraction(new RationalImpl("5/3", mctx)), (RealType) sqrtThree.divide(two).negate());
        knownValues.put(piFraction(new RationalImpl("7/4")), (RealType) sqrtTwo.divide(two).negate());
        knownValues.put(piFraction(new RationalImpl("11/6", mctx)),
                new RealImpl(oneHalf.negate(), mctx));
        knownValues.put(piFraction(new RationalImpl("23/12", mctx)),
                ((RealType) sqrtThree.subtract(one).multiply(sqrtTwo).divide(four)).negate());
        knownValues.put((RealType) pi.multiply(two), zero);
    }

    private RealType piDividedBy(long divisor) {
        Pi pi = Pi.getInstance(mctx);
        return (RealType) pi.divide(new IntegerImpl(BigInteger.valueOf(divisor)));
    }

    private RealType piFraction(RationalType factor) {
        Pi pi = Pi.getInstance(mctx);
        return (RealType) pi.multiply(factor);
    }

    @Override
    public RealType apply(ArgVector<RealType> arguments) {
        ProxyFunction<RealType, RealType> proxy = obtainProxy();
        RealType arg = arguments.hasVariableName(getArgumentName()) ?
                arguments.forVariableName(getArgumentName()) : arguments.elementAt(0L);
        RealType result = proxy.applyWithoutInterpolation(arg);
        if (result == null) {
            result = MathUtils.sin(arg);
        }
        return result;
    }

    @Override
    public Range<RealType> inputRange(String argName) {
        return RangeUtils.ALL_REALS;
    }

    @Override
    public Class<RealType> getArgumentType() {
        return RealType.class;
    }

    /**
     * Map a given input value to the inner range of this function.
     * @param input the input value
     * @return a value mapped to the internal range of this function
     */
    protected RealType mapToInnerRange(RealType input) {
        if (internalRange.contains(input)) return input;

        final RealType period = period();
        RealType temp = input;
        while (internalRange.isBelow(temp)) {
            temp = (RealType) temp.add(period);
        }
        while (internalRange.isAbove(temp)) {
            temp = (RealType) temp.subtract(period);
        }
        return temp;
    }

    private static final RealType TEN = new RealImpl(BigDecimal.TEN, MathContext.UNLIMITED);

    @Override
    public ProxyFunction<RealType, RealType> obtainProxy() {
        final RealType epsilon = MathUtils.computeIntegerExponent(TEN, 1 - this.mctx.getPrecision(), this.mctx);
        return new ProxyFunction<>(getArgumentName(), knownValues, epsilon) {
            @Override
            protected RealType coerceToInnerRange(RealType input) {
                return mapToInnerRange(input);
            }
        };
    }

    private UnaryFunction<RealType, RealType> diffCache;
    private final Lock diffCacheLock = new ReentrantLock();

    @Differentiable
    public UnaryFunction<RealType, RealType> diff() {
        diffCacheLock.lock();
        try {
            if (diffCache == null) {
                diffCache = new Cos(getArgumentName(), mctx);
            }
            return diffCache;
        } finally {
            diffCacheLock.unlock();
        }
    }

    @Override
    public UnaryFunction<? super RealType, RealType> composeWith(UnaryFunction<? super RealType, RealType> before) {
        if (before instanceof Negate) {
            // sin(-x) = -sin(x)
            return this.andThen(Negate.getInstance(RealType.class));
        }
        return super.composeWith(before);
    }

    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder().append("sin");
        Optional<UnaryFunction<RealType, ? extends RealType>> encompassing = this.getComposingFunction();
        encompassing.ifPresent(f -> {
            if (f instanceof Pow) {
                Pow<?, ?> power = (Pow<?, ?>) f;
                Numeric exponent = power.getExponent();
                if (exponent instanceof IntegerType) {
                    int n = ((IntegerType) exponent).asBigInteger().intValueExact();
                    buf.append(UnicodeTextEffects.numericSuperscript(n));
                } else {
                    buf.append("^{").append(exponent).append("}\u2009"); // postpend thin space to help offset closing brace
                }
            } else if (f instanceof Negate) {
                buf.insert(0, '\u2212'); // insert a minus sign
            }
        });
        buf.append('(');
        getComposedFunction().ifPresentOrElse(buf::append,
                () -> buf.append(getArgumentName()));
        buf.append(')');

        return buf.toString();
    }

    @Override
    public Range<RealType> principalRange() {
        return RangeUtils.getAngularInstance(mctx);
    }

    @Override
    public RealType period() {
        final Pi pi = Pi.getInstance(mctx);
        final RealType two = new RealImpl(BigDecimal.valueOf(2L), mctx);
        return (RealType) pi.multiply(two);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Sin sin = (Sin) o;
        if (this.getComposedFunction().isPresent()) {
            if (sin.getComposedFunction().isEmpty()) return false;
            if (!sin.getComposedFunction().equals(this.getComposedFunction())) return false;
        } else {
            if (sin.getComposedFunction().isPresent()) return false;
        }
        return Objects.equals(mctx, sin.mctx) && Objects.equals(getArgumentName(), sin.getArgumentName());
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), mctx, getArgumentName());
    }
}
