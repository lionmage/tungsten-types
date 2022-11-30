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
    private static final IntegerImpl ONE = new IntegerImpl(BigInteger.ONE);
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
        final Numeric diffExponent = exponent.subtract(ONE);
        try {
            final R coeff = (R) exponent.coerceTo(outputClazz);
            if (Zero.isZero(diffExponent)) return Const.getInstance(coeff);

            if (diffExponent instanceof RationalType) {
                return new Product<>(Const.getInstance(coeff),
                        new Pow<>((RationalType) diffExponent));
            } else {
                final long n = ((IntegerType) diffExponent.coerceTo(IntegerType.class)).asBigInteger().longValueExact();
                return new Product<>(Const.getInstance(coeff), new Pow<>(n));
            }
        } catch (CoercionException e) {
            throw new IllegalStateException("Computing derivative failed", e);
        }
    }

    @Override
    public UnaryFunction<? super T, R> composeWith(UnaryFunction<? super T, T> before) {
        if (before instanceof Pow) {
            Numeric expProd = ((Pow<? super T, T>) before).getExponent().multiply(exponent);
            if (Zero.isZero(expProd)) {
                try {
                    return Const.getInstance((R) One.getInstance(MathContext.UNLIMITED).coerceTo(outputClazz));
                } catch (CoercionException e) {
                    throw new RuntimeException(e);
                }
            } else if (One.isUnity(expProd)) {
                final Class<T> myArgClazz = (Class<T>) ((Class) ((ParameterizedType) this.getClass()
                        .getGenericSuperclass()).getActualTypeArguments()[0]);

                return new Reflexive<>(getArgumentName(), RangeUtils.ALL_REALS, myArgClazz).forReturnType(outputClazz);
            }
            // create a new instance of Pow with a merged exponent
            Pow<? super T, R> pow;
            if (expProd instanceof RationalType) {
                pow = new Pow<>((RationalType) expProd);
            } else {
                pow = new Pow<>(((IntegerType) expProd).asBigInteger().longValueExact());
            }
            return pow;
        }
        return super.composeWith(before);
    }

    @Override
    public <R2 extends R> UnaryFunction<T, R2> andThen(UnaryFunction<R, R2> after) {
        final Class<R2> myOutputClazz = (Class<R2>) ((Class) ((ParameterizedType) after.getClass()
                .getGenericSuperclass()).getActualTypeArguments()[1]);
        if (after instanceof Pow) {
            final Pow<R, R2> afterPow = (Pow<R, R2>) after;
            Numeric expProd = this.exponent.multiply(afterPow.getExponent());
            if (One.isUnity(expProd)) {
                final Class<T> myArgClazz = (Class<T>) ((Class) ((ParameterizedType) this.getClass()
                        .getGenericSuperclass()).getActualTypeArguments()[0]);
                return new Reflexive<>(getArgumentName(), RangeUtils.ALL_REALS, myArgClazz).forReturnType(myOutputClazz);
            } else if (Zero.isZero(expProd)) {
                try {
                    return Const.getInstance((R2) One.getInstance(MathContext.UNLIMITED).coerceTo(myOutputClazz));
                } catch (CoercionException e) {
                    throw new IllegalStateException("Could not coerce unity to " + myOutputClazz.getTypeName());
                }
            }
            // create a new instance of Pow with a merged exponent
            Pow<T, R2> pow;
            if (expProd instanceof RationalType) {
                pow = new Pow<>((RationalType) expProd);
            } else {
                pow = new Pow<>(((IntegerType) expProd).asBigInteger().longValueExact());
            }
            return pow;
        } else if ((UnaryFunction) after instanceof NaturalLog) {
            // log(x^y) = y*log(x)
            try {
                return new Product<>(getArgumentName(),
                        Const.getInstance((R2) this.exponent.coerceTo(myOutputClazz)),
                        (UnaryFunction<T, R2>) after);
            } catch (CoercionException e) {
                throw new IllegalStateException("While coercing exponent to constant coefficient", e);
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
        StringBuilder buf = new StringBuilder();
        getComposedFunction().ifPresentOrElse(f -> buf.append('(').append(f).append(')'),
                () -> buf.append(getArgumentName()));
        if (exponent instanceof IntegerType) {
            buf.append(UnicodeTextEffects.numericSuperscript(((IntegerType) exponent).asBigInteger().intValueExact()));
        } else {
            buf.append('^').append(exponent).append('\u2009');
        }
        return buf.toString();
    }
}
