package tungsten.types.functions;
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
import tungsten.types.exceptions.CoercionException;
import tungsten.types.numerics.RealType;

import java.util.Arrays;
import java.util.function.Function;

public abstract class NumericFunction<T extends Numeric, R extends Numeric> implements Function<ArgVector<T>, R> {
    protected final Class<R> returnType;

    protected NumericFunction(Class<R> returnType) {
        this.returnType = returnType;
    }

    @Override
    public abstract R apply(ArgVector<T> arguments);

    /**
     * Given an argument map, generate an {@code ArgVector}
     * and {@link #apply(ArgVector) apply} this function
     * to it.
     * @param arguments a mapping of argument names to values
     * @return the result of this function's evaluation
     */
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

    /**
     * Obtain the return type of this numeric function.
     * @return the type that this function returns
     * @since 0.6
     */
    public Class<R> getReturnType() {
        return returnType;
    }

    /**
     * Obtain the type of the arguments of this numeric function.
     * @return the type that this function consumes
     * @since 0.6
     */
    public abstract Class<T> getArgumentType();

    /**
     * Generate a function which performs the same computation as this
     * function, but with the given return type.
     * @param clazz the desired return type of the generated function
     * @return the new function
     * @param <R2> the return type
     */
    public <R2 extends Numeric> NumericFunction<T, R2> forReturnType(Class<R2> clazz) {
        if (clazz.isAssignableFrom(returnType)) return (NumericFunction<T, R2>) this;

        return new NumericFunction<>(clazz) {
            @Override
            public R2 apply(ArgVector<T> arguments) {
                try {
                    return (R2) NumericFunction.this.apply(arguments).coerceTo(clazz);
                } catch (CoercionException e) {
                    throw new IllegalStateException(e);
                }
            }

            @Override
            public long arity() {
                return NumericFunction.this.arity();
            }

            @Override
            public String[] expectedArguments() {
                return NumericFunction.this.expectedArguments();
            }

            @Override
            public Range<RealType> inputRange(String argName) {
                return NumericFunction.this.inputRange(argName);
            }

            @Override
            public Class<T> getArgumentType() {
                return NumericFunction.this.getArgumentType();
            }
        };
    }
}
