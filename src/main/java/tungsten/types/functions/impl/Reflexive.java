package tungsten.types.functions.impl;

import tungsten.types.Numeric;
import tungsten.types.Range;
import tungsten.annotations.Differentiable;
import tungsten.types.exceptions.CoercionException;
import tungsten.types.functions.ArgVector;
import tungsten.types.functions.UnaryFunction;
import tungsten.types.numerics.RealType;
import tungsten.types.numerics.impl.One;
import tungsten.types.util.RangeUtils;

import java.math.MathContext;

public class Reflexive<T extends Numeric> extends UnaryFunction<T, T> {
    final Range<RealType> range;
    final Class<T> clazz;

    public Reflexive(String argName, Range<RealType> range, Class<T> forClass) {
        super(argName);
        this.range = range;
        this.clazz = forClass;
    }

    public Reflexive(Class<T> forClass) {
        super("x");
        this.clazz = forClass;
        this.range = RangeUtils.ALL_REALS;
    }

    @Override
    public T apply(ArgVector<T> arguments) {
        T arg = arguments.elementAt(0L);
        if (RealType.class.isAssignableFrom(clazz) && !inputRange(getArgumentName()).contains((RealType) arg)) {
            throw new IllegalArgumentException("Value " + arg + " is out of input range " + inputRange(getArgumentName()));
        }
        return arg;
    }

    @Differentiable
    public UnaryFunction<T, T> diff() {
        try {
            return Const.getInstance((T) One.getInstance(MathContext.UNLIMITED).coerceTo(clazz));
        } catch (CoercionException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public <R2 extends T> UnaryFunction<T, R2> andThen(UnaryFunction<T, R2> after) {
        return after;
    }

    @Override
    public UnaryFunction<? super T, T> composeWith(UnaryFunction<? super T, T> before) {
        return before;
    }

    @Override
    public Range<RealType> inputRange(String argName) {
        if (range == null) return RangeUtils.ALL_REALS;
        return range;
    }
}
