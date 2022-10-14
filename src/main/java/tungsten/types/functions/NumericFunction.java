package tungsten.types.functions;

import tungsten.types.Numeric;
import tungsten.types.Range;
import tungsten.types.numerics.RealType;

import java.util.Arrays;
import java.util.function.Function;

public abstract class NumericFunction<T extends Numeric, R extends Numeric> implements Function<ArgVector<T>, R> {
    @Override
    public abstract R apply(ArgVector<T> arguments);

    public R apply(ArgMap<T> arguments) {
        final ArgVector<T> args = new ArgVector<>(expectedArguments(), arguments);
        return apply(args);
    }

    /**
     * Returns the argument count for this function.
     *
     * @return this function's arity
     */
    public abstract long arity();

    /**
     * A method to check whether a given argument vector meets
     * basic requirements for the inputs of this function.
     * Subclasses should perform range checking on individual arguments.
     *
     * @param arguments the vectorized argument list
     * @return true if arguments are sound, false otherwise
     */
    protected boolean checkArguments(ArgVector<T> arguments) {
        if (arguments.arity() != this.arity()) return false;
        return Arrays.stream(expectedArguments()).allMatch(arguments::hasVariableName);
    }

    /**
     * Returns a list of the names of expected arguments for this function,
     * in expectation order.
     *
     * @return an array of argument names
     */
    public abstract String[] expectedArguments();

    /**
     * Returns the input range of a named input parameter.
     * If the named parameter is of {@link tungsten.types.numerics.ComplexType},
     * the convention is to return the real range using {@code "argname.re"}
     * and the imaginary range using {@code "argname.im"}.
     * Alternately, polar coordinates can be referenced using {@code "argname.mag"}
     * for magnitude and {@code "argname.arg"} for the argument (polar angle
     * on the complex plane).
     *
     * @param argName the name of an argument, used as a variable in this function
     * @return a representation of the valid input range for the given parameter name,
     *  or null if no range is defined for this argument
     */
    public abstract Range<RealType> inputRange(String argName);
}
