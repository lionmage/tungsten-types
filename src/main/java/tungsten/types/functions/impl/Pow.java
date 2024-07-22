package tungsten.types.functions.impl;
/*
 * The MIT License
 *
 * Copyright © 2022 Robert Poole <Tarquin.AZ@gmail.com>.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

import tungsten.types.Numeric;
import tungsten.types.Range;
import tungsten.types.annotations.Differentiable;
import tungsten.types.exceptions.CoercionException;
import tungsten.types.functions.ArgVector;
import tungsten.types.functions.NumericFunction;
import tungsten.types.functions.UnaryFunction;
import tungsten.types.functions.support.Rewritable;
import tungsten.types.numerics.*;
import tungsten.types.numerics.impl.IntegerImpl;
import tungsten.types.numerics.impl.One;
import tungsten.types.numerics.impl.Zero;
import tungsten.types.util.ClassTools;
import tungsten.types.util.MathUtils;
import tungsten.types.util.RangeUtils;
import tungsten.types.util.UnicodeTextEffects;

import java.math.BigInteger;
import java.math.MathContext;
import java.util.List;
import java.util.Objects;

/**
 * A function that raises a value to a given power.  More formally, given x,
 * this function computes x<sup>n</sup>, where n is an integer or rational value.
 * <br>This function is intended for composition with other functions, and is
 * fully differentiable.
 *
 * @param <T> the input parameter type
 * @param <R> the output type
 */
public class Pow<T extends Numeric, R extends Numeric> extends UnaryFunction<T, R> implements Rewritable {
    private static final IntegerImpl ONE = new IntegerImpl(BigInteger.ONE);
    private final Class<R> outputClazz;
    private final Numeric exponent;

    public Pow(long n) {
        super("x");
        exponent = new IntegerImpl(BigInteger.valueOf(n));
        outputClazz = (Class<R>) ClassTools.getTypeArguments(NumericFunction.class, this.getClass()).get(1);
    }

    public Pow(long n, Class<R> rtnType) {
        super("x");
        exponent = new IntegerImpl(BigInteger.valueOf(n));
        outputClazz = rtnType;
    }

    public Pow(RationalType rationalExponent) {
        super("x");
        if (rationalExponent.isCoercibleTo(IntegerType.class)) {
            exponent = rationalExponent.reduce().numerator();
        } else {
            exponent = rationalExponent;
        }
        outputClazz = (Class<R>) ClassTools.getTypeArguments(NumericFunction.class, this.getClass()).get(1);
    }

    public Pow(RationalType rationalExponent, Class<R> rtnType) {
        super("x");
        if (rationalExponent.isCoercibleTo(IntegerType.class)) {
            exponent = rationalExponent.reduce().numerator();
        } else {
            exponent = rationalExponent;
        }
        outputClazz = rtnType;
    }

    public Pow(UnaryFunction<? super T, T> inner, Numeric exponent) {
        this(inner.expectedArguments()[0], exponent);
        composedFunction = inner;
    }

    public Pow(UnaryFunction<? super T, T> inner, Numeric exponent, Class<R> rtnType) {
        this(inner.expectedArguments()[0], exponent, rtnType);
        composedFunction = inner;
    }

    private static final List<Class<? extends Numeric>> supportedExponentTypes =
            List.of(IntegerType.class, RationalType.class);

    protected Pow(String argName, Numeric exponent) {
        super(argName);
        if (supportedExponentTypes.stream().noneMatch(t -> t.isAssignableFrom(exponent.getClass()))) {
            throw new IllegalArgumentException("Unsupported exponent type: " + exponent.getClass().getTypeName());
        }
        this.exponent = exponent;
        this.outputClazz = (Class<R>) ClassTools.getTypeArguments(NumericFunction.class, this.getClass()).get(1);
    }

    protected Pow(String argName, Numeric exponent, Class<R> rtnType) {
        super(argName);
        if (supportedExponentTypes.stream().noneMatch(t -> t.isAssignableFrom(exponent.getClass()))) {
            throw new IllegalArgumentException("Unsupported exponent type: " + exponent.getClass().getTypeName());
        }
        this.exponent = exponent;
        this.outputClazz = rtnType;
    }

    @Override
    public R apply(ArgVector<T> arguments) {
        final T arg = arguments.hasVariableName(getArgumentName()) ?
                arguments.forVariableName(getArgumentName()) : arguments.elementAt(0L);
        MathContext ctx = arguments.getMathContext() != null ? arguments.getMathContext() : arg.getMathContext();
        NumericHierarchy h = NumericHierarchy.forNumericType(arg.getClass());
        if (h == null) throw new ArithmeticException("Unable to compute exponent of " + arg);
        try {
            final T intermediate = getComposedFunction().isEmpty() ? arg : getComposedFunction().get().apply(arg);
            switch (h) {
                case COMPLEX:
                    return (R) MathUtils.generalizedExponent((ComplexType) intermediate, exponent, ctx).coerceTo(outputClazz);
                case REAL:
                    if (exponent instanceof ComplexType) {
                        return (R) MathUtils.generalizedExponent((RealType) intermediate, (ComplexType) exponent, ctx).coerceTo(outputClazz);
                    }
                    return (R) MathUtils.generalizedExponent((RealType) intermediate, exponent, ctx).coerceTo(outputClazz);
                default:
                    RealType coerced = (RealType) intermediate.coerceTo(RealType.class);
                    return (R) MathUtils.generalizedExponent(coerced, exponent, ctx).coerceTo(outputClazz);
            }
        } catch (CoercionException e) {
            throw new ArithmeticException("Type incompatibility while computing exponent");
        }
    }

    public Numeric getExponent() {
        return exponent;
    }

    @Override
    public Pow<T, R> forArgName(String argName) {
        if (getArgumentName().equals(argName)) return this;
        if (getComposedFunction().isPresent()) {
            if (getComposedFunction().get().expectedArguments()[0].equals(argName)) {
                // inner function is already in the correct form, so just rewrite the outer
                UnaryFunction<? super T, T> inner = getComposedFunction().get();
                return new Pow<>(argName, exponent) {
                    {
                        composedFunction = inner;
                    }
                };
            }
            if (getComposedFunction().get() instanceof Rewritable) {
                UnaryFunction<? super T, T> inner = (UnaryFunction<? super T, T>) getComposedFunction()
                        .map(Rewritable.class::cast)
                        .map(rw -> rw.forArgName(argName)).orElseThrow();
                return new Pow<>(inner, exponent);
            }
            throw new UnsupportedOperationException("Cannot rewrite inner function in terms of " + argName);
        }
        return new Pow<>(argName, exponent);
    }

    @Differentiable
    public UnaryFunction<T, R> diff(SimpleDerivative<RealType> diffEngine) {
        final Class<T> myArgClazz = (Class<T>) ClassTools.getTypeArguments(NumericFunction.class, this.getClass()).get(0);
        final Numeric diffExponent = exponent.subtract(ONE);
        try {
            final R coeff = (R) exponent.coerceTo(outputClazz);
            UnaryFunction<T, R> outerdiff;
            if (Zero.isZero(diffExponent)) outerdiff = Const.getInstance(coeff);
            else if (diffExponent instanceof RationalType) {
                outerdiff = new Product<>(getArgumentName(), Const.getInstance(coeff),
                        new Pow<>((RationalType) diffExponent));
            } else {
                final long n = ((IntegerType) diffExponent.coerceTo(IntegerType.class)).asBigInteger().longValueExact();
                outerdiff = new Product<>(getArgumentName(), Const.getInstance(coeff), new Pow<>(n));
            }
            if (getComposedFunction().isPresent()) {
                if (RealType.class.isAssignableFrom(myArgClazz)) {
                    UnaryFunction<RealType, RealType> inner = (UnaryFunction<RealType, RealType>) getComposedFunction().get();
                    UnaryFunction<RealType, RealType> innerdiff = diffEngine.apply(inner);
                    return (UnaryFunction<T, R>) new Product<>(getArgumentName(),
                            (UnaryFunction<RealType, RealType>) outerdiff.composeWith((UnaryFunction<? super T, T>) inner),
                            innerdiff).forReturnType(outputClazz);
                } else {
                    throw new UnsupportedOperationException("Differentiation of inner function not supported for type " + myArgClazz.getTypeName());
                }
            }
            // fall through
            return outerdiff;
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
                final Class<T> myArgClazz = (Class<T>) ClassTools.getTypeArguments(NumericFunction.class, this.getClass()).get(0);

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
        final Class<R2> myOutputClazz = (Class<R2>) ClassTools.getTypeArguments(NumericFunction.class, after.getClass()).get(1);
        if (after instanceof Pow) {
            final Pow<R, R2> afterPow = (Pow<R, R2>) after;
            Numeric expProd = this.exponent.multiply(afterPow.getExponent());
            if (One.isUnity(expProd)) {
                final Class<T> myArgClazz = (Class<T>) ClassTools.getTypeArguments(NumericFunction.class, this.getClass()).get(0);
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
        } else if (NaturalLog.class.isAssignableFrom(after.getClass())) {
            // log(x^y) = y*log(x)
            try {
                return new Product<>(getArgumentName(),
                        Const.getInstance((R2) this.exponent.coerceTo(myOutputClazz)),
                        (UnaryFunction<T, R2>) after);
            } catch (CoercionException e) {
                throw new IllegalStateException("While converting exponent to constant coefficient", e);
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
            // since the exponent cannot be superscripted, add a thin space after
            buf.append('^').append(exponent).append('\u2009');
        }
        return buf.toString();
    }

    @Override
    public int hashCode() {
        return Objects.hash(exponent, outputClazz);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof Pow) {
            Class<? extends Numeric> otherReturnType = ((Pow<?, ?>) obj).outputClazz;
            if (otherReturnType != null && this.outputClazz != null) {
                if (!outputClazz.isAssignableFrom(otherReturnType)) return false;
            }
            Numeric otherExponent = ((Pow<?, ?>) obj).getExponent();
            return this.exponent.equals(otherExponent);
        }
        return false;
    }
}
