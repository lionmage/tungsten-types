package tungsten.types.functions.impl;

import tungsten.types.Numeric;
import tungsten.types.Range;
import tungsten.types.annotations.Differentiable;
import tungsten.types.exceptions.CoercionException;
import tungsten.types.functions.ArgVector;
import tungsten.types.functions.UnaryFunction;
import tungsten.types.numerics.*;
import tungsten.types.numerics.impl.IntegerImpl;
import tungsten.types.numerics.impl.One;
import tungsten.types.numerics.impl.Zero;
import tungsten.types.util.MathUtils;
import tungsten.types.util.RangeUtils;
import tungsten.types.util.UnicodeTextEffects;

import java.lang.reflect.ParameterizedType;
import java.math.BigInteger;
import java.math.MathContext;
import java.util.Optional;

/**
 * A function that raises a value to a given power.  More formally, given x,
 * this function computes x<sup>n</sup>, where n is an integer or rational value.
 * <br/>This function is intended for composition with other functions, and is
 * fully differentiable.
 *
 * @param <T> the input parameter type
 * @param <R> the output type
 */
public class Pow<T extends Numeric, R extends Numeric> extends UnaryFunction<T, R> {
    private final Class<R> outputClazz = (Class<R>) ((Class) ((ParameterizedType) getClass()
            .getGenericSuperclass()).getActualTypeArguments()[1]);
    private final Numeric exponent;

    public Pow(long n) {
        super("x");
        exponent = new IntegerImpl(BigInteger.valueOf(n));
    }

    public Pow(RationalType rationalExponent) {
        super("x");
        if (rationalExponent.isCoercibleTo(IntegerType.class)) {
            exponent = rationalExponent.reduce().numerator();
        } else {
            exponent = rationalExponent;
        }
    }

    @Override
    public R apply(ArgVector<T> arguments) {
        T arg = arguments.elementAt(0L);
        MathContext ctx = arguments.getMathContext() != null ? arguments.getMathContext() : arg.getMathContext();
        NumericHierarchy h = NumericHierarchy.forNumericType(arg.getClass());
        try {
            switch (h) {
                case COMPLEX:
                    return (R) MathUtils.generalizedExponent((ComplexType) arg, exponent, ctx).coerceTo(outputClazz);
                case REAL:
                    return (R) MathUtils.generalizedExponent((RealType) arg, exponent, ctx).coerceTo(outputClazz);
                default:
                    RealType coerced = (RealType) arg.coerceTo(RealType.class);
                    return (R) MathUtils.generalizedExponent(coerced, exponent, ctx).coerceTo(outputClazz);
            }
        } catch (CoercionException e) {
            throw new ArithmeticException("Type incompatibility while computing exponent.");
        }
    }

    public Numeric getExponent() {
        return exponent;
    }

    @Differentiable
    public UnaryFunction<T, R> diff() {
        final Numeric diffExponent = exponent.subtract(new IntegerImpl(BigInteger.ONE));
        try {
            R coeff = (R) exponent.coerceTo(outputClazz);
            if (Zero.isZero(diffExponent)) return Const.getInstance(coeff);
            if (diffExponent instanceof RationalType) {
                return new Product<>(Const.getInstance(coeff),
                        new Pow<>((RationalType) diffExponent));
            } else {
                final long n = ((IntegerType) diffExponent.coerceTo(IntegerType.class)).asBigInteger().longValueExact();
                return new Product<>(Const.getInstance(coeff), new Pow<>(n));
            }
        } catch (CoercionException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public UnaryFunction<? super T, R> composeWith(UnaryFunction<? super T, T> before) {
        return super.composeWith(before);
    }

    @Override
    public <R2 extends R> UnaryFunction<T, R2> andThen(UnaryFunction<R, R2> after) {
        if (after instanceof Pow) {
            final Pow<R, R2> afterPow = (Pow<R, R2>) after;
            Numeric expProd = this.exponent.multiply(afterPow.getExponent());
            Optional<UnaryFunction<T, R>> orig = this.getOriginalFunction();
            if (orig.isPresent()) {
                if (One.isUnity(expProd)) {
                    return (UnaryFunction<T, R2>) orig.get();
                }
                // create a new instance of Pow with a merged exponent
                Pow<T, R2> pow;
                if (expProd instanceof RationalType) {
                    pow = new Pow<>((RationalType) expProd);
                } else {
                    pow = new Pow<>(((IntegerType) expProd).asBigInteger().longValueExact());
                }
                pow.setOriginalFunction((UnaryFunction<T, R2>) orig.get());
                afterPow.getComposingFunction().ifPresent(pow::setComposingFunction);
                orig.get().setComposingFunction((UnaryFunction<R, ? extends R2>) pow);
                return pow;
            }
        }
        return super.andThen(after);
    }

    @Override
    public Range<RealType> inputRange(String argName) {
        return RangeUtils.ALL_REALS;
    }

    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder().append(getArgumentName());
        if (exponent instanceof IntegerType) {
            buf.append(UnicodeTextEffects.numericSuperscript(((IntegerType) exponent).asBigInteger().intValueExact()));
        } else {
            buf.append('^').append(exponent).append('\u2009');
        }
        return buf.toString();
    }
}
