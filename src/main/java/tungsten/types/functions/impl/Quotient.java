package tungsten.types.functions.impl;

import tungsten.types.Numeric;
import tungsten.types.Range;
import tungsten.types.exceptions.CoercionException;
import tungsten.types.functions.ArgVector;
import tungsten.types.functions.UnaryFunction;
import tungsten.types.numerics.RealType;
import tungsten.types.numerics.impl.Zero;

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
