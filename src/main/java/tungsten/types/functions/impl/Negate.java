package tungsten.types.functions.impl;

import tungsten.types.Numeric;
import tungsten.types.Range;
import tungsten.types.annotations.Differentiable;
import tungsten.types.exceptions.CoercionException;
import tungsten.types.functions.ArgVector;
import tungsten.types.functions.NumericFunction;
import tungsten.types.functions.UnaryFunction;
import tungsten.types.numerics.RealType;
import tungsten.types.util.ClassTools;
import tungsten.types.util.OptionalOperations;
import tungsten.types.util.RangeUtils;

import java.util.List;

/**
 * A representation of the function &fnof;(x)&nbsp;=&nbsp;&minus;x
 * <br/>Not terribly useful by itself, but it is very handy for composition
 * of differentiable functions.
 *
 * @param <T> the type of the function's sole input parameter
 * @param <R> the type of the function's output
 */
public class Negate<T extends Numeric, R extends Numeric> extends UnaryFunction<T, R> {
    private Negate() {
        super("x");
    }

    public static <T extends Numeric, R extends Numeric> Negate<T, R> getInstance() {
        return new Negate<>() {};  // anonymous subclass to aid in reification of type parameters
    }

    @Override
    public R apply(ArgVector<T> arguments) {
        List<Class<?>> argClasses = ClassTools.getTypeArguments(NumericFunction.class, this.getClass());
        try {
            return (R) arguments.elementAt(0L).negate().coerceTo((Class<R>) argClasses.get(1));
        } catch (CoercionException e) {
            throw new ArithmeticException("Could not coerce result of negation.");
        }
    }


    @Override
    public UnaryFunction<? super T, R> composeWith(UnaryFunction<? super T, T> before) {
        if (before instanceof Negate && this.getComposedFunction().isEmpty()) {
            return (UnaryFunction<? super T, R>) this.getComposingFunction()
                    .orElse((UnaryFunction<R, ? extends R>) this.getOriginalFunction().orElseThrow());
        }
        return super.composeWith(before);
    }


    @Override
    public <R2 extends R> UnaryFunction<T, R2> andThen(UnaryFunction<R, R2> after) {
        if (after instanceof Negate) {
            List<Class<?>> argClasses = ClassTools.getTypeArguments(NumericFunction.class, after.getClass());
            return this.getOriginalFunction().orElseThrow().forReturnType((Class<R2>) argClasses.get(1));
        }
        return super.andThen(after);
    }

    @Differentiable
    public UnaryFunction<T, R> diff() {
        List<Class<?>> argClasses = ClassTools.getTypeArguments(NumericFunction.class, this.getClass());
        final R response = OptionalOperations.dynamicInstantiate((Class<? extends R>) argClasses.get(1), "-1");

        return Const.getInstance(response);
    }

    @Override
    public Range<RealType> inputRange(String argName) {
        return RangeUtils.ALL_REALS;
    }
}
