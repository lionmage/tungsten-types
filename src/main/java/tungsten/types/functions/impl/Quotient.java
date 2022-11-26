package tungsten.types.functions.impl;

import tungsten.types.Numeric;
import tungsten.types.Range;
import tungsten.types.exceptions.CoercionException;
import tungsten.types.functions.ArgVector;
import tungsten.types.functions.UnaryFunction;
import tungsten.types.numerics.IntegerType;
import tungsten.types.numerics.RationalType;
import tungsten.types.numerics.RealType;
import tungsten.types.numerics.impl.One;
import tungsten.types.numerics.impl.Zero;
import tungsten.types.util.OptionalOperations;

import java.lang.reflect.ParameterizedType;
import java.util.logging.Logger;

public class Quotient<T extends Numeric, R extends Numeric> extends UnaryFunction<T, R> {
    private final Class<R> outputClazz = (Class<R>) ((Class) ((ParameterizedType) getClass()
            .getGenericSuperclass()).getActualTypeArguments()[1]);
    private final UnaryFunction<T, R> numerator;
    private final UnaryFunction<T, R> denominator;

    public Quotient(String argName, UnaryFunction<T, R> numerator, UnaryFunction<T, R> denominator) {
        super(argName);
        String numArg = numerator.expectedArguments()[0];
        if (!numArg.equals(argName)) {
            Logger.getLogger(getClass().getTypeName())
                    .warning("Mapping Quotient arg " + argName + " to numerator arg " + numArg);
        }
        String denomArg = denominator.expectedArguments()[0];
        if (!denomArg.equals(argName)) {
            Logger.getLogger(getClass().getTypeName())
                    .warning("Mapping Quotient arg " + argName + " to denominator arg " + denomArg);
        }
        this.numerator = numerator;
        this.denominator = denominator;
    }

    public Quotient(UnaryFunction<T, R> numerator, UnaryFunction<T, R> denominator) {
        this("x", numerator, denominator);
    }

    @Override
    public R apply(ArgVector<T> arguments) {
        R denomResult = denominator.apply(arguments);
        if (Zero.isZero(denomResult)) throw new ArithmeticException("Divide by zero encountered");
        R numResult = numerator.apply(arguments);
        try {
            return (R) numResult.divide(denomResult).coerceTo(outputClazz);
        } catch (CoercionException e) {
            throw new IllegalStateException(e);
        }
    }

    public UnaryFunction<T, R> simplify() {
        if (Negate.isNegateEquivalent(numerator)) {
            if (numerator instanceof Product) {
                return new Quotient<>(getArgumentName(), ((Product<T, R>) numerator).simplify(), denominator);
            }
        } else if (Negate.isNegateEquivalent(denominator)) {
            if (denominator instanceof Product) {
                return new Quotient<>(getArgumentName(), numerator, ((Product<T, R>) denominator).simplify());
            }
        }
        final boolean numIsConst = Const.isConstEquivalent(numerator);
        final boolean denomIsConst = Const.isConstEquivalent(denominator);
        try {
            if (numIsConst && denomIsConst) {
                // special case where the entire quotient can be reduced to a single constant
                Const<? super RealType, RealType> numEq = Const.getConstEquivalent(numerator);
                Const<? super RealType, RealType> denEq = Const.getConstEquivalent(denominator);
                if (Zero.isZero(denEq.inspect())) throw new ArithmeticException("Division by zero while reducing quotient.");
                R qVal = (R) numEq.inspect().divide(denEq.inspect()).coerceTo(outputClazz);
                return Const.getInstance(qVal);
            }
            if (numIsConst) {
                Const<? super RealType, RealType> equiv = Const.getConstEquivalent(numerator);
                R eqInR = (R) equiv.inspect().coerceTo(outputClazz);
                return new Quotient<>(getArgumentName(), Const.getInstance(eqInR), denominator);
            }
            if (denomIsConst) {
                Const<? super RealType, RealType> equiv = Const.getConstEquivalent(denominator);
                if (One.isUnity(equiv.inspect())) return numerator;
                if (Zero.isZero(equiv.inspect())) throw new ArithmeticException("Denominator of quotient reduces to zero.");
                R eqInR = (R) equiv.inspect().coerceTo(outputClazz);
                return new Quotient<>(getArgumentName(), numerator, Const.getInstance(eqInR));
            }
        } catch (CoercionException e) {
            throw new IllegalStateException(e);
        }
        if (numerator instanceof Pow && denominator instanceof Pow) {
            Numeric numExponent = ((Pow<T, R>) numerator).getExponent();
            Numeric denomExponent = ((Pow<T, R>) denominator).getExponent();
            Numeric diffExponent = numExponent.subtract(denomExponent);
            if (Zero.isZero(diffExponent)) return Const.getInstance(OptionalOperations.dynamicInstantiate(outputClazz, "1"));
            if (diffExponent instanceof IntegerType) {
                return new Pow<>(((IntegerType) diffExponent).asBigInteger().longValueExact());
            }
            return new Pow<>((RationalType) diffExponent);
        }

        // If all else fails, return this
        return this;
    }

    public UnaryFunction<T, R> getNumerator() {
        return numerator;
    }

    public UnaryFunction<T, R> getDenominator() {
        return denominator;
    }

    @Override
    public Range<RealType> inputRange(String argName) {
        if (!getArgumentName().equals(argName)) {
            throw new IllegalArgumentException("Argument " + argName + " does not exist for this function.");
        }
        return Range.chooseNarrowest(numerator.inputRange(argName), denominator.inputRange(argName));
    }
}
