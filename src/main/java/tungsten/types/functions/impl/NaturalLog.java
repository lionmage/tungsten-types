package tungsten.types.functions.impl;

import tungsten.types.Numeric;
import tungsten.types.Range;
import tungsten.types.annotations.Differentiable;
import tungsten.types.functions.ArgVector;
import tungsten.types.functions.UnaryFunction;
import tungsten.types.numerics.IntegerType;
import tungsten.types.numerics.RealType;
import tungsten.types.numerics.Sign;
import tungsten.types.numerics.impl.IntegerImpl;
import tungsten.types.numerics.impl.RealImpl;
import tungsten.types.numerics.impl.RealInfinity;
import tungsten.types.util.MathUtils;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;

public class NaturalLog extends UnaryFunction<RealType, RealType> {
    public NaturalLog() {
        super("x");
    }

    public NaturalLog(String argName) {
        super(argName);
    }

    @Override
    public RealType apply(ArgVector<RealType> arguments) {
        if (!checkArguments(arguments)) {
            throw new IllegalArgumentException("Expected argument "
                    + getArgumentName() + " is not present in input or is out of range.");
        }
        return MathUtils.ln(arguments.elementAt(0L));
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
        if (getComposedFunction().isPresent() && getComposedFunction().get() instanceof Pow &&
                getComposedFunction().get().getComposedFunction().isEmpty()) {
            Numeric exponent = ((Pow<?, ?>) getComposedFunction().get()).getExponent();
            if (exponent instanceof IntegerType) numerator = ((IntegerType) exponent).asBigInteger();
            // TODO what to do about rational exponents?
        } else if (getComposedFunction().isPresent()) {
            // for any other composed function, use the chain rule
            UnaryFunction<RealType, RealType> inner = (UnaryFunction<RealType, RealType>) getComposedFunction().get();
            UnaryFunction<RealType, RealType> outerDiff = lnDiff(new IntegerImpl(BigInteger.ONE));
            UnaryFunction<RealType, RealType> innerdiff = diffEngine.apply(inner);
            return new Product<>((UnaryFunction<RealType, RealType>) outerDiff.composeWith(inner), innerdiff);
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
                buf.append(scale).append('/').append(NaturalLog.this.innerToString(false));
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
