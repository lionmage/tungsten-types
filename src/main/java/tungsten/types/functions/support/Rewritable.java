package tungsten.types.functions.support;

import tungsten.types.Numeric;
import tungsten.types.functions.UnaryFunction;

/**
 * An interface for {@link UnaryFunction}s that allows them
 * to be rewritten in terms of a different variable name.
 */
public interface Rewritable {
    /**
     * This method takes a new argument name as its parameter and returns
     * an instance of {@link UnaryFunction} which performs the same function
     * as this (the original) function, rewritten in terms of the new variable
     * name.</br>
     * It is strongly encouraged that implementing classes take advantage of
     * covariant return types to make the return signature as specific as possible.
     * @param argName the new variable name
     * @return this function, rewritten in terms of {@code argName}
     */
    UnaryFunction<? extends Numeric, ? extends Numeric> forArgName(String argName);
}
