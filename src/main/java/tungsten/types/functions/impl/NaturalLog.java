package tungsten.types.functions.impl;

import tungsten.types.Numeric;
import tungsten.types.Range;
import tungsten.types.annotations.Differentiable;
import tungsten.types.exceptions.CoercionException;
import tungsten.types.functions.ArgVector;
import tungsten.types.functions.UnaryFunction;
import tungsten.types.numerics.IntegerType;
import tungsten.types.numerics.RationalType;
import tungsten.types.numerics.RealType;
import tungsten.types.numerics.Sign;
import tungsten.types.numerics.impl.IntegerImpl;
import tungsten.types.numerics.impl.RealImpl;
import tungsten.types.numerics.impl.RealInfinity;
import tungsten.types.util.MathUtils;

import java.lang.reflect.ParameterizedType;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;

/**
 * A basic implementation of the natural logarithm function ln(x) for positive
 * real-valued arguments.  This implementation supports composing with another
 * function at construction-time, saving some effort.
 */
public class NaturalLog extends UnaryFunction<RealType, RealType> {
    public NaturalLog() {
        super("x");
    }

    public NaturalLog(String argName) {
        super(argName);
    }

    /**
     * A convenience constructor to build a composed function
     * ln(&fnof;(x)).
     *
     * @param inner the inner function &fnof;(x) for composition
     */
    public NaturalLog(UnaryFunction<? super RealType, RealType> inner) {
        super(inner.expectedArguments()[0]);
        composedFunction = inner;
    }

    @Override
    public RealType apply(ArgVector<RealType> arguments) {
        if (!checkArguments(arguments)) {
            throw new IllegalArgumentException("Expected argument "
                    + getArgumentName() + " is not present in input or is out of range.");
        }
        final RealType arg = arguments.elementAt(0L);
        RealType intermediate = getComposedFunction().isEmpty() ? arg : getComposedFunction().get().apply(arg);
        return MathUtils.ln(intermediate);
    }

    @Override
    public UnaryFunction<? super RealType, RealType> composeWith(UnaryFunction<? super RealType, RealType> before) {
        final String beforeArgName = before.expectedArguments()[0];
        if (before instanceof Exp) {
            return new Reflexive<>(beforeArgName, before.inputRange(beforeArgName), RealType.class);
        } else if (before instanceof Pow) {
            Numeric exponent = ((Pow<? super RealType, RealType>) before).getExponent();
            // log(x^y) = y*log(x)
            try {
                Const<RealType, RealType> myConst = Const.getInstance((RealType) exponent.coerceTo(RealType.class));
                return new Product<>(beforeArgName, myConst, this);
            } catch (CoercionException e) {
                throw new IllegalStateException("Exponent " + exponent + " is not coercible", e);
            }
        }
        return super.composeWith(before);
    }

    @Override
    public <R2 extends RealType> UnaryFunction<RealType, R2> andThen(UnaryFunction<RealType, R2> after) {
        if (after instanceof Exp) {
            Class<R2> rtnClass = (Class<R2>) ((Class) ((ParameterizedType) after.getClass()
                    .getGenericSuperclass()).getActualTypeArguments()[1]);
            if (rtnClass == null) rtnClass = (Class<R2>) RealType.class;
            return new Reflexive<>(getArgumentName(), lnRange, RealType.class).forReturnType(rtnClass);
        }
        return super.andThen(after);
    }

    private static final Range<RealType> lnRange = new Range<>(new RealImpl(BigDecimal.ZERO),
            RealInfinity.getInstance(Sign.POSITIVE, MathContext.UNLIMITED),
            Range.BoundType.EXCLUSIVE);

    @Override
    public Range<RealType> inputRange(String argName) {
        return lnRange;
    }

    @Override
    protected boolean checkArguments(ArgVector<RealType> arguments) {
        return super.checkArguments(arguments) && lnRange.contains(arguments.elementAt(0L));
    }

    @Differentiable
    public UnaryFunction<RealType, RealType> diff(SimpleDerivative<RealType> diffEngine) {
        BigInteger numerator = BigInteger.ONE;
        if (getComposedFunction().isPresent()) {
            if (getComposedFunction().get() instanceof Pow &&
                    getComposedFunction().get().getComposedFunction().isEmpty()) {
                Numeric exponent = ((Pow<?, ?>) getComposedFunction().get()).getExponent();
                if (exponent instanceof IntegerType) numerator = ((IntegerType) exponent).asBigInteger();
                else {
                    final RationalType scalar = (RationalType) exponent;
                    try {
                        return new Quotient<>(Const.getInstance((RealType) scalar.numerator().coerceTo(RealType.class)),
                                new Product<>(Const.getInstance((RealType) scalar.denominator().coerceTo(RealType.class)),
                                        new Reflexive<>(expectedArguments()[0], lnRange, RealType.class)));
                    } catch (CoercionException fatal) {
                        throw new IllegalStateException(fatal);
                    }
                }
            } else {
                // for any other composed function, use the chain rule
                UnaryFunction<RealType, RealType> inner = (UnaryFunction<RealType, RealType>) getComposedFunction().get();
                UnaryFunction<RealType, RealType> outerDiff = lnDiff(new IntegerImpl(BigInteger.ONE));
                UnaryFunction<RealType, RealType> innerdiff = diffEngine.apply(inner);
                return new Product<>((UnaryFunction<RealType, RealType>) outerDiff.composeWith(inner), innerdiff);
            }
        }
        // The derivative of ln(x) is 1/x over the positive reals, ln(x^2) is 2/x, etc.
        final IntegerType scale = new IntegerImpl(numerator);
        return lnDiff(scale);
    }

    private UnaryFunction<RealType, RealType> lnDiff(IntegerType scale) {
        return new Pow<>(-1L) {
            @Override
            public RealType apply(ArgVector<RealType> arguments) {
                return (RealType) super.apply(arguments).multiply(scale);
            }

            @Override
            public Range<RealType> inputRange(String argName) {
                return lnRange;
            }

            @Override
            public String toString() {
                StringBuilder buf = new StringBuilder();
                buf.append(scale).append('/').append(NaturalLog.this.getArgumentName()); // was: innerToString(false)
                return buf.toString();
            }
        };
    }

    @Override
    public String toString() {
        return "ln" + innerToString(true);
    }

    private String innerToString(boolean alwaysUseParens) {
        final boolean useParens = alwaysUseParens || getComposedFunction().isPresent();
        StringBuilder buf = new StringBuilder();
        if (useParens) buf.append('(');
        getComposedFunction().ifPresentOrElse(buf::append,
                () -> buf.append(getArgumentName()));
        if (useParens) buf.append(')');
        return buf.toString();
    }
}
