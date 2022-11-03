package tungsten.types.functions.impl;

import tungsten.types.Range;
import tungsten.types.annotations.Differentiable;
import tungsten.types.functions.ArgVector;
import tungsten.types.functions.NumericFunction;
import tungsten.types.functions.UnaryFunction;
import tungsten.types.numerics.RealType;
import tungsten.types.numerics.impl.Euler;
import tungsten.types.util.RangeUtils;

public class Exp extends UnaryFunction<RealType, RealType> {
    public Exp() {
        super("x");
    }

    public Exp(String varName) {
        super(varName);
    }

    @Override
    public RealType apply(ArgVector<RealType> arguments) {
        if (!checkArguments(arguments)) {
            throw new IllegalArgumentException("Expected argument " + getArgumentName() + " is not present in input vector.");
        }
        final RealType arg = arguments.elementAt(0L);
        final Euler e = Euler.getInstance(arg.getMathContext());
        return e.exp(arg);
    }

    @Override
    public Range<RealType> inputRange(String argName) {
        return RangeUtils.ALL_REALS;
    }

    @Differentiable
    public UnaryFunction<RealType, RealType> diff(SimpleDerivative<RealType> diffEngine) {
        if (getComposedFunction().isEmpty()) return this;
        return new Product<>(diffEngine.apply((NumericFunction<RealType, RealType>) getComposedFunction().get()),
                this);
    }

    @Override
    public String toString() {
        if (getComposedFunction().isEmpty()) return "\u212Fˣ";
        return "exp(\u2009" + getComposedFunction().get() + "\u2009)";
    }
}
