package tungsten.types.functions.impl;

import tungsten.types.Numeric;
import tungsten.types.Range;
import tungsten.types.annotations.Differentiable;
import tungsten.types.functions.ArgVector;
import tungsten.types.functions.Proxable;
import tungsten.types.functions.UnaryFunction;
import tungsten.types.functions.support.CompositeKey;
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
import java.util.*;

/**
 * A function that implements sin(x) for real-valued inputs.
 */
public class Sin extends UnaryFunction<RealType, RealType> implements Proxable<RealType, RealType> {
    private final RealType epsilon;
    private final Pi pi;
    private final Range<RealType> internalRange;

    private final Map<RealType, RealType> knownValues = new LinkedHashMap<>();

    /**
     * Constructor for this function which takes an argument name and
     * an epsilon value.  Note that {@code epsilon} not only provides
     * the delta for determining if a pre-calculated value is &ldquo;close enough,&rdquo;
     * but also provides the {@link java.math.MathContext} which is used for all
     * internal calculations.  Be sure to construct an {@code epsilon} with both
     * a sufficiently small value and a sufficiently large precision when
     * using this function.
     *
     * @param argName the name of the sole argument to this function
     * @param epsilon the epsilon value for determining closeness for value matching
     */
    public Sin(String argName, RealType epsilon) {
        super(argName);
        this.epsilon = epsilon;
        pi = Pi.getInstance(new MathContext(epsilon.getMathContext().getPrecision() + 1,
                epsilon.getMathContext().getRoundingMode()));
        internalRange = new Range<>(new RealImpl(BigDecimal.ZERO),
                Range.BoundType.INCLUSIVE,
                (RealType) pi.multiply(new RealImpl(BigDecimal.valueOf(2L), epsilon.getMathContext())),
                Range.BoundType.EXCLUSIVE);
        initializeTable();
        derivatives.put(0L, this);  // f0 is this function itself
    }

    /**
     * Constructor which takes an epsilon value and uses the default value for
     * this function's argument name, i.e. &theta;
     *
     * @param epsilon the epsilon value for determining closeness for value matching
     */
    public Sin(RealType epsilon) {
        this(MathUtils.THETA, epsilon);
    }

    private void initializeTable() {
        final RealType zero = new RealImpl(BigDecimal.ZERO);
        final RealType one = new RealImpl(BigDecimal.ONE, epsilon.getMathContext());
        final RealType two = new RealImpl(BigDecimal.valueOf(2L), epsilon.getMathContext());
        final IntegerType four = new IntegerImpl("4");
        final RealType ten = new RealImpl(BigDecimal.TEN, epsilon.getMathContext());
        final RationalType oneHalf = new RationalImpl("1/2", epsilon.getMathContext());
        final RealType sqrtTwo = (RealType) two.sqrt();
        final RealType sqrtThree = (RealType) new RealImpl(BigDecimal.valueOf(3L), epsilon.getMathContext()).sqrt();
        final RealType sqrtFive = (RealType) new RealImpl(BigDecimal.valueOf(5L), epsilon.getMathContext()).sqrt();

        knownValues.put(zero, zero);
        knownValues.put(piDividedBy(24L), (RealType) sqrtThree.add(two).sqrt().negate()
                .add(two).sqrt().multiply(oneHalf));
        knownValues.put(piDividedBy(12L), (RealType) sqrtThree.subtract(one)
                .multiply(sqrtTwo).divide(four));
        knownValues.put(piDividedBy(10L), (RealType) sqrtFive.subtract(one).divide(four));
        knownValues.put(piDividedBy(8L), (RealType) two.subtract(sqrtTwo).sqrt().multiply(oneHalf));
        knownValues.put(piDividedBy(6L), new RealImpl(oneHalf, epsilon.getMathContext()));
        knownValues.put(piDividedBy(5L), (RealType) ten.subtract(two.multiply(sqrtFive)).sqrt().divide(four));
        knownValues.put(piDividedBy(4L), (RealType) sqrtTwo.divide(two));
        knownValues.put(piFraction(new RationalImpl("3/10")),
                (RealType) one.add(sqrtFive).divide(four));
        knownValues.put(piDividedBy(3L), (RealType) sqrtThree.divide(two));
        knownValues.put(piFraction(new RationalImpl("3/8")),
                (RealType) sqrtTwo.add(two).sqrt().divide(two));
        knownValues.put(piFraction(new RationalImpl("2/5")),
                (RealType) ten.add(two.multiply(sqrtFive)).sqrt().divide(four));
        knownValues.put(piFraction(new RationalImpl("5/12", pi.getMathContext())),
                (RealType) sqrtThree.add(one).multiply(sqrtTwo).divide(four));
        knownValues.put(piDividedBy(2L), one);
        knownValues.put(piFraction(new RationalImpl("7/12", pi.getMathContext())),
                (RealType) sqrtThree.add(one).multiply(sqrtTwo).divide(four));
        knownValues.put(piFraction(new RationalImpl("2/3", pi.getMathContext())), (RealType) sqrtThree.divide(two));
        knownValues.put(piFraction(new RationalImpl("3/4")), (RealType) sqrtTwo.divide(two));
        knownValues.put(piFraction(new RationalImpl("5/6", pi.getMathContext())), new RealImpl(oneHalf, pi.getMathContext()));
        knownValues.put(piFraction(new RationalImpl("11/12", pi.getMathContext())),
                (RealType) sqrtThree.subtract(one).multiply(sqrtTwo).divide(four));
        knownValues.put(pi, zero);
        knownValues.put(piFraction(new RationalImpl("13/12", pi.getMathContext())),
                (RealType) sqrtThree.subtract(one).divide(two.multiply(sqrtTwo)).negate());
        knownValues.put(piFraction(new RationalImpl("7/6", pi.getMathContext())),
                new RealImpl(oneHalf.negate(), pi.getMathContext()));
        knownValues.put(piFraction(new RationalImpl("5/4")), (RealType) sqrtTwo.divide(two).negate());
        knownValues.put(piFraction(new RationalImpl("4/3", pi.getMathContext())), (RealType) sqrtThree.divide(two).negate());
        knownValues.put(piFraction(new RationalImpl("17/12", pi.getMathContext())),
                ((RealType) sqrtThree.add(one).multiply(sqrtTwo).divide(four)).negate());
        knownValues.put(piFraction(new RationalImpl("3/2")), one.negate());
        knownValues.put(piFraction(new RationalImpl("19/12", pi.getMathContext())),
                ((RealType) sqrtThree.add(one).multiply(sqrtTwo).divide(four)).negate());
        knownValues.put(piFraction(new RationalImpl("5/3", pi.getMathContext())), (RealType) sqrtThree.divide(two).negate());
        knownValues.put(piFraction(new RationalImpl("7/4")), (RealType) sqrtTwo.divide(two).negate());
        knownValues.put(piFraction(new RationalImpl("11/6", pi.getMathContext())),
                new RealImpl(oneHalf.negate(), pi.getMathContext()));
        knownValues.put(piFraction(new RationalImpl("23/12", pi.getMathContext())),
                ((RealType) sqrtThree.subtract(one).multiply(sqrtTwo).divide(four)).negate());
        knownValues.put((RealType) pi.multiply(two), zero);
    }

    private RealType piDividedBy(long divisor) {
        return (RealType) pi.divide(new IntegerImpl(BigInteger.valueOf(divisor)));
    }

    private RealType piFraction(RationalType factor) {
        return (RealType) pi.multiply(factor);
    }

    private final Map<CompositeKey, TaylorPolynomial<RealType, RealType>> polynomialMap =
            new TreeMap<>();

    @Override
    public RealType apply(ArgVector<RealType> arguments) {
        ProxyFunction<RealType, RealType> proxy = obtainProxy();
        RealType arg = arguments.elementAt(0L);
        RealType inrangeArg = mapToInnerRange(arg);
        RealType result = proxy.applyWithoutInterpolation(inrangeArg);
        if (result == null) {
            long order = arg.getMathContext().getPrecision();
            RealType a0 = proxy.closestKeyToInput(arg);
            CompositeKey key = new CompositeKey(order, a0);
            // find the appropriate Taylor polynomial, or generate it if it doesn't exist
            TaylorPolynomial<RealType, RealType> p = polynomialMap.get(key);
            if (p != null) return p.apply(arg);
            // if we got here, we have no choice but to compute a new polynomial
            p = generateTaylorPolynomial(a0).getForNTerms(order);
            polynomialMap.put(key, p);
            result = p.apply(arg);
        }
        return result;
    }

    private final Map<Long, UnaryFunction<RealType, RealType>> derivatives = new TreeMap<>();

    protected TaylorPolynomial<RealType, RealType> generateTaylorPolynomial(RealType a0) {
        final SimpleDerivative<RealType> diffEngine = new SimpleDerivative<>(epsilon);

        return new TaylorPolynomial<>(getArgumentName(), this, a0) {
            @Override
            protected UnaryFunction<RealType, RealType> f_n(long n) {
                UnaryFunction<RealType, RealType> diff = derivatives.get(n);
                if (diff == null) {
                    long start = derivatives.keySet().stream().max(Long::compareTo).map(l -> l + 1L).orElse(1L);
                    assert start <= n;
                    // we already populated this function into the 0th entry, so the above should be sound
                    for (long index = start; index <= n; index++) {
                        derivatives.put(index, diffEngine.apply(derivatives.get(index - 1L)));
                    }
                    diff = derivatives.get(n);
                }
                return diff;
            }
        };
    }

    @Override
    public Range<RealType> inputRange(String argName) {
        return RangeUtils.ALL_REALS;
    }

    protected RealType mapToInnerRange(RealType input) {
        if (internalRange.contains(input)) return input;

        final RealType twoPi = (RealType) pi.multiply(new RealImpl(BigDecimal.valueOf(2L),
                epsilon.getMathContext()));
        RealType temp = input;
        while (internalRange.isBelow(temp)) {
            temp = (RealType) temp.add(twoPi);
        }
        while (internalRange.isAbove(temp)) {
            temp = (RealType) temp.subtract(twoPi);
        }
        return temp;
    }

    @Override
    public ProxyFunction<RealType, RealType> obtainProxy() {
        return new ProxyFunction<>(getArgumentName(), knownValues, epsilon) {
            @Override
            protected RealType coerceToInnerRange(RealType input) {
                return mapToInnerRange(input);
            }
        };
    }

    @Differentiable
    public UnaryFunction<RealType, RealType> diff() {
        return new Cos(getArgumentName(), epsilon);
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
                }
                // TODO figure out what to do with rational exponents
            } else if (f instanceof Negate) {
                buf.insert(0, '\u2212'); // insert a minus sign
            }
        });
        buf.append('(').append(getArgumentName()).append(')');

        return buf.toString();
    }
}
