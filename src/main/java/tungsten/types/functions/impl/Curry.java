package tungsten.types.functions.impl;

import tungsten.types.Numeric;
import tungsten.types.exceptions.CoercionException;
import tungsten.types.functions.ArgMap;
import tungsten.types.functions.MetaFunction;
import tungsten.types.functions.NumericFunction;
import tungsten.types.numerics.RealType;

import java.util.Arrays;

/**
 * A simple meta-function which curries the inputs of a {@link NumericFunction} and
 * generates a new {@link NumericFunction} with the same input types and return type,
 * but with fewer declared arguments.  Functions with only one remaining argument
 * are returned as a {@link tungsten.types.functions.UnaryFunction}, while functions
 * with no remaining arguments are returned as a {@link Const}.
 *
 * @param <T> the input parameter type of both input and returned functions
 * @param <R> the return type of both input and returned functions
 */
public class Curry<T extends Numeric, R extends Numeric> extends MetaFunction<T, R, R> {
    private long numberOfMappings;

    public Curry(ArgMap<T> curriedArguments) {
        super(curriedArguments);
        numberOfMappings = curriedArguments.size();
    }

    public void insertOrUpdateCurryMapping(String varName, T value) {
        boolean update = super.containsCurryMapping(varName);
        super.setCurryMapping(varName, value);
        if (!update) numberOfMappings++;
    }

    @Override
    public NumericFunction<T, R> apply(NumericFunction<T, R> inputFunction) {
        final ArgMap<T> mappedArgs = mappedArgsView();
        // we must check that our curried values all fall within the input range of their respective argument
        if (! mappedArgs.keySet().stream().filter(argName -> inputFunction.inputRange(argName) != null)
                .allMatch(argName -> inputFunction.inputRange(argName).contains(coerce(mappedArgs.get(argName)))) ) {
            String argName = mappedArgs.keySet().stream().filter(arg -> inputFunction.inputRange(arg) != null)
                    .filter(arg -> inputFunction.inputRange(arg).contains(coerce(mappedArgs.get(arg))))
                    .findFirst().orElseThrow();
            throw new IllegalArgumentException("Variable " + argName +
                    " has a curry value that is out of range " + inputFunction.inputRange(argName));
        }
        if (numberOfMappings >= inputFunction.arity() &&
                mappedArgs.keySet().containsAll(Arrays.asList(inputFunction.expectedArguments()))) {
            return Const.getInstance(inputFunction.apply(mappedArgs));
        }
        return curry(inputFunction);
    }

    private RealType coerce(Numeric argument) {
        try {
            return (RealType) argument.coerceTo(RealType.class);
        } catch (CoercionException e) {
            throw new IllegalArgumentException("Cannot convert " + argument + " to real for range comparison.");
        }
    }
}
