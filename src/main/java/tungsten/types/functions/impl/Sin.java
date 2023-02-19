package tungsten.types.functions.impl;

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
import java.util.Optional;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * A function that implements sin(x) for real-valued inputs.
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
     * @param mctx the {@link MathContext} governing the accuracy of this function
     */
    public Sin(String argName, MathContext mctx) {
        super(argName);
        this.mctx = mctx;
        RealType decTwo = new RealImpl(BigDecimal.valueOf(2L), mctx);
        // since the table of internal values is specified over the range 0 to 2𝜋,
        // set up the internal range appropriately
        internalRange = new Range<>(new RealImpl(BigDecimal.ZERO, mctx), Range.BoundType.INCLUSIVE,
                (RealType) Pi.getInstance(mctx).multiply(decTwo), Range.BoundType.EXCLUSIVE);
        initializeTable();
    }

    /**
     * Constructor which takes an epsilon value and uses the default value for
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
        final IntegerType four = new IntegerImpl("4");
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
        RealType arg = arguments.elementAt(0L);
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
    public String toString() {
        StringBuilder buf = new StringBuilder().append("sin");
        Optional<UnaryFunction<RealType, ? extends RealType>> encompassing = this.getComposingFunction();
        encompassing.ifPresent(f -> {
            if (f instanceof Pow) {
                Pow<RealType, RealType> power = (Pow<RealType, RealType>) f;
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
}
