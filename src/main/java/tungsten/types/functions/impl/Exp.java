package tungsten.types.functions.impl;

import tungsten.types.Range;
import tungsten.types.annotations.Differentiable;
import tungsten.types.functions.ArgVector;
import tungsten.types.functions.UnaryFunction;
import tungsten.types.numerics.RealType;
import tungsten.types.numerics.impl.Euler;
import tungsten.types.util.RangeUtils;

import java.lang.reflect.ParameterizedType;

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
    public UnaryFunction<? super RealType, RealType> composeWith(UnaryFunction<? super RealType, RealType> before) {
        if (before instanceof NaturalLog) {
            final String beforeArgName = before.expectedArguments()[0];
            return new Reflexive<>(beforeArgName, before.inputRange(beforeArgName), RealType.class);
        }
        return super.composeWith(before);
    }

    @Override
    public <R2 extends RealType> UnaryFunction<RealType, R2> andThen(UnaryFunction<RealType, R2> after) {
        if (after instanceof NaturalLog) {
            Class<R2> rtnClass = (Class<R2>) ((Class) ((ParameterizedType) after.getClass()
                    .getGenericSuperclass()).getActualTypeArguments()[1]);
            if (rtnClass == null) rtnClass = (Class<R2>) RealType.class;
            return new Reflexive<>(getArgumentName(), RangeUtils.ALL_REALS, RealType.class).forReturnType(rtnClass);
        }
        return super.andThen(after);
    }

    @Override
    public Range<RealType> inputRange(String argName) {
        return RangeUtils.ALL_REALS;
    }

    @Differentiable
    public UnaryFunction<RealType, RealType> diff(SimpleDerivative<RealType> diffEngine) {
        if (getComposingFunction().isPresent()) {
            UnaryFunction<RealType, RealType> outer = (UnaryFunction<RealType, RealType>) getComposingFunction().get();
            return diffEngine.chainRuleStrategy(outer, this.getOriginalFunction().orElseThrow(IllegalStateException::new));
        }
        if (getComposedFunction().isEmpty()) return this;
        final UnaryFunction<RealType, RealType> inner = (UnaryFunction<RealType, RealType>) getComposedFunction().get();
        // Note that if we got here, "this" refers to a composed function of Exp and inner, exactly what we want.
        return new Product<>(diffEngine.apply(inner), this);
    }

    @Override
    public String toString() {
        if (getComposedFunction().isEmpty()) return "\u212Fˣ";
        return "exp(\u2009" + getComposedFunction().get() + "\u2009)";
    }
}
