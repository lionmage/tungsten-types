package tungsten.types.functions.impl;

import tungsten.types.Range;
import tungsten.types.annotations.Differentiable;
import tungsten.types.functions.UnaryFunction;
import tungsten.types.numerics.RealType;

import java.util.logging.Level;
import java.util.logging.Logger;

public class RealPiecewiseFunction extends PiecewiseFunction<RealType, RealType> {
    final RealType epsilon;

    public RealPiecewiseFunction(String argName, RealType epsilon) {
        super(argName);
        this.epsilon = epsilon;
    }

    public RealPiecewiseFunction(RealType epsilon) {
        super();
        this.epsilon = epsilon;
    }

    @Differentiable
    public UnaryFunction<RealType, RealType> diff() {
        final SimpleDerivative<RealType> diffMachine = new SimpleDerivative<>(epsilon);
        RealPiecewiseFunction result = new RealPiecewiseFunction(getArgumentName(), epsilon);
        viewOfFunctionMap().values().parallelStream().forEach(f -> {
            String f_argName = f.expectedArguments()[0];
            UnaryFunction<RealType, RealType> f_diff = diffMachine.apply(f);
            Range<RealType> f_range = Range.chooseNarrowest(f.inputRange(f_argName), f_diff.inputRange(f_argName));
            result.addFunctionForRange(f_range, f_diff);
        });

        if (!result.checkAggregateBounds()) {
            Logger.getLogger(RealPiecewiseFunction.class.getName()).log(Level.WARNING,
                    "Bounds check failed for derived piecewise function.");
        }
        return result;
    }
}
