package tungsten.types.functions.impl;

import tungsten.types.Range;
import tungsten.types.annotations.Differentiable;
import tungsten.types.functions.ArgVector;
import tungsten.types.functions.UnaryFunction;
import tungsten.types.numerics.RealType;
import tungsten.types.numerics.Sign;
import tungsten.types.numerics.impl.RealImpl;
import tungsten.types.numerics.impl.RealInfinity;
import tungsten.types.util.MathUtils;

import java.math.BigDecimal;
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
    public UnaryFunction<RealType, RealType> diff() {
        // The derivative of ln(x) is 1/x over the positive reals
        return new Pow<>(-1L) {
            @Override
            public Range<RealType> inputRange(String argName) {
                return lnRange;
            }

            @Override
            public String toString() {
                return "1/" + NaturalLog.this.getArgumentName();
            }
        };
    }

    @Override
    public String toString() {
        return "ln(" + getArgumentName() + ")";
    }
}
