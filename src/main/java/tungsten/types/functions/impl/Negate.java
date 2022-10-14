package tungsten.types.functions.impl;

import tungsten.types.Numeric;
import tungsten.types.Range;
import tungsten.types.annotations.Differentiable;
import tungsten.types.exceptions.CoercionException;
import tungsten.types.functions.ArgVector;
import tungsten.types.functions.UnaryFunction;
import tungsten.types.numerics.RealType;
import tungsten.types.util.RangeUtils;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.ParameterizedType;

/**
 * A representation of the function &fnof;(x)&nbsp;=&nbsp;&minus;x
 * <br/>Not terribly useful by itself, but it is very handy for composition
 * of differentiable functions.
 *
 * @param <T> the type of the function's sole input parameter
 * @param <R> the type of the function's output
 */
public class Negate<T extends Numeric, R extends Numeric> extends UnaryFunction<T, R> {
    private final Class<R> outputClazz = (Class<R>) ((Class) ((ParameterizedType) getClass()
            .getGenericSuperclass()).getActualTypeArguments()[1]);

    public Negate() {
        super("x");
    }

    @Override
    public R apply(ArgVector<T> arguments) {
        try {
            return (R) arguments.elementAt(0L).negate().coerceTo(outputClazz);
        } catch (CoercionException e) {
            throw new ArithmeticException("Could not coerce result of negation.");
        }
    }


    @Override
    public UnaryFunction<? super T, R> composeWith(UnaryFunction<? super T, T> before) {
        if (before instanceof Negate) {
            return this.getOriginalFunction().isPresent() ?
                    this.getOriginalFunction().get() :
                    (UnaryFunction<? super T, R>) this.getComposingFunction().orElseThrow();
        }
        return super.composeWith(before);
    }


    @Override
    public <R2 extends R> UnaryFunction<T, R2> andThen(UnaryFunction<R, R2> after) {
        if (after instanceof Negate) {
            return (UnaryFunction<T, R2>) after.getOriginalFunction().orElseThrow();
        }
        return super.andThen(after);
    }

    @Differentiable
    public UnaryFunction<T, R> diff() {
        final R response;
        try {
            response = outputClazz.getConstructor(String.class).newInstance("-1");
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
            throw new IllegalStateException("Unable to obtain -1 for type " + outputClazz.getTypeName(), e);
        }

        return new UnaryFunction<T, R>("x") {
            @Override
            public R apply(ArgVector<T> arguments) {
                return response;
            }

            @Override
            public Range<RealType> inputRange(String argName) {
                return RangeUtils.ALL_REALS;
            }
        };
    }

    @Override
    public Range<RealType> inputRange(String argName) {
        return RangeUtils.ALL_REALS;
    }
}
