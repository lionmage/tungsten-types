package tungsten.types.functions.impl;

import tungsten.types.Numeric;
import tungsten.types.annotations.Differentiable;
import tungsten.types.exceptions.CoercionException;
import tungsten.types.functions.ArgVector;
import tungsten.types.functions.Term;
import tungsten.types.numerics.IntegerType;
import tungsten.types.numerics.RationalType;
import tungsten.types.numerics.impl.ExactZero;
import tungsten.types.numerics.impl.One;
import tungsten.types.numerics.impl.Zero;
import tungsten.types.util.ClassTools;
import tungsten.types.util.OptionalOperations;

import java.util.List;
import java.util.Objects;

/**
 * A representation of a constant polynomial term.
 *
 * @param <T> the input parameter type, which is ignored
 * @param <R> the output parameter type, which refers to the constant value
 */
public class ConstantTerm<T extends Numeric, R extends Numeric> extends Term<T, R> {
    private final R value;

    public ConstantTerm(R init) {
        super((Class<R>) ClassTools.getInterfaceTypeFor(init.getClass()));
        value = init;
    }

    public ConstantTerm(String init, Class<R> clazz) {
        super(clazz);
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
            try {
                R prod = (R) value.multiply(multiplier.coefficient()).coerceTo(getReturnClass());
                return new ConstantTerm<>(prod);
            } catch (CoercionException e) {
                throw new ArithmeticException("Unable to compute the product of two constants");
            }
        }

        // default behavior is to make use of commutativity
        return multiplier.multiply(this);
    }

    @Override
    public Term<T, R> multiply(Pow<T, R> multiplier) {
        if (Zero.isZero(value)) return this;
        final String varName = multiplier.expectedArguments()[0];
        final Numeric exponent = multiplier.getExponent();
        if (exponent instanceof IntegerType) {
            long iExponent = ((IntegerType) exponent).asBigInteger().longValueExact();
            return new PolyTerm<>(value, List.of(varName), List.of(iExponent));
        } else {
            // Pow only supports integer and rational exponents, so it must be a rational
            return new RationalExponentPolyTerm<>(value, List.of(varName), List.of((RationalType) exponent));
        }
    }

    @Override
    public Term<T, R> scale(R multiplier) {
        if (One.isUnity(multiplier)) return this;
        return new ConstantTerm<>((R) value.multiply(multiplier));
    }

    @Differentiable
    public Term<T, R> differentiate(String varName) {
        final Class<R> clazz = getReturnClass();
        try {
            return new ConstantTerm<>((R) ExactZero.getInstance(value.getMathContext()).coerceTo(clazz));
        } catch (CoercionException e) {
            throw new IllegalStateException("While differentiating a constant term", e);
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
