package tungsten.types.functions.impl;

import tungsten.types.Numeric;
import tungsten.types.annotations.Differentiable;
import tungsten.types.exceptions.CoercionException;
import tungsten.types.functions.ArgVector;
import tungsten.types.functions.Term;
import tungsten.types.numerics.impl.ExactZero;
import tungsten.types.numerics.impl.Zero;
import tungsten.types.util.OptionalOperations;

import java.lang.reflect.ParameterizedType;
import java.util.Objects;

public class ConstantTerm<T extends Numeric, R extends Numeric> extends Term<T, R> {
    private final R value;

    public ConstantTerm(R init) {
        super();
        value = init;
    }

    public ConstantTerm(String init) {
        super();
        final Class<R> clazz = (Class<R>) ((Class) ((ParameterizedType) getClass()
                .getGenericSuperclass()).getActualTypeArguments()[1]);  // class for R
        value = OptionalOperations.dynamicInstantiate(clazz, init);
    }

    @Override
    public R apply(ArgVector<T> arguments) {
        return value;
    }

    @Override
    public Term<T, R> multiply(Term<T, R> multiplier) {
        if (Zero.isZero(value)) {
            return this;
        }
        if (multiplier instanceof ConstantTerm) {
            return new ConstantTerm<T, R>((R) value.multiply(multiplier.coefficient()));
        }
        // TODO add a few other special cases?

        // default behavior is to make use of commutativity
        return multiplier.multiply(this);
    }

    @Differentiable
    public Term<T, R> differentiate(String varName) {
        final Class<R> clazz = (Class<R>) ((Class) ((ParameterizedType) getClass()
                .getGenericSuperclass()).getActualTypeArguments()[1]);  // class for R
        try {
            return new ConstantTerm<T, R>((R) ExactZero.getInstance(value.getMathContext()).coerceTo(clazz));
        } catch (CoercionException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public R coefficient() {
        return value;
    }

    @Override
    public long order(String varName) {
        return 0;
    }

    @Override
    public boolean isConstant() {
        return true;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ConstantTerm<?, ?> that = (ConstantTerm<?, ?>) o;
        return value.equals(that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
