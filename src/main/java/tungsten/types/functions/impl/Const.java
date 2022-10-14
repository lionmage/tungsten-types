package tungsten.types.functions.impl;

import tungsten.types.Numeric;
import tungsten.types.Range;
import tungsten.types.annotations.Differentiable;
import tungsten.types.exceptions.CoercionException;
import tungsten.types.functions.ArgVector;
import tungsten.types.functions.UnaryFunction;
import tungsten.types.numerics.RealType;
import tungsten.types.numerics.impl.ExactZero;
import tungsten.types.numerics.impl.Zero;
import tungsten.types.util.RangeUtils;

import java.lang.reflect.ParameterizedType;

public class Const<T extends Numeric, R extends Numeric> extends UnaryFunction<T, R> {
    final R value;

    public Const(R init) {
        super("x");
        value = init;
    }

    @Override
    public R apply(ArgVector<T> arguments) {
        return value;
    }

    @Override
    public Range<RealType> inputRange(String argName) {
        return RangeUtils.ALL_REALS;
    }

    public R inspect() {
        return value;
    }

    @Differentiable
    public UnaryFunction<T, R> diff() {
        if (Zero.isZero(value)) {
            return this;
        } else {
            final Class<R> resultClass = (Class<R>)
                    ((Class) ((ParameterizedType) this.getClass()
                            .getGenericSuperclass()).getActualTypeArguments()[1]);
            try {
                return new Const<>((R) ExactZero.getInstance(value.getMathContext()).coerceTo(resultClass));
            } catch (CoercionException e) {
                throw new IllegalStateException("Unable to instantiate the Zero function.", e);
            }
        }
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
