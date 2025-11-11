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
import tungsten.types.util.ClassTools;
import tungsten.types.util.MathUtils;

import java.util.Optional;
import java.util.function.Function;
import java.util.logging.Logger;

/**
 * A model of a unary function, that is, a function that takes a single scalar argument and returns a single
 * scalar result.
 * @param <T> the type consumed by this unary function
 * @param <R> the type returned by this unary function
 * @author Robert Poole, <a href="mailto:Tarquin.AZ@gmail.com">Gmail</a>
 */
public abstract class UnaryFunction<T extends Numeric, R extends Numeric> extends NumericFunction<T, R> {
    private final String argumentName;
    // if this function is f(x) and we have a composition of h(f(g(x)))
    // then g(x) is the composedFunction (inner function)
    // and h(x) is the composingFunction (outer function)
    protected UnaryFunction<? super T, T> composedFunction;
    protected UnaryFunction<R, ? extends R> composingFunction;
    private UnaryFunction<T, R> originalFunction;

    @Deprecated(since = "0.6")
    protected UnaryFunction(String varName) {
        super((Class<R>) ClassTools.getTypeArguments(NumericFunction.class, UnaryFunction.class).get(1));
        this.argumentName = varName;
    }

    protected UnaryFunction(String varName, Class<R> rtnType) {
        super(rtnType);
        this.argumentName = varName;
    }

    /**
     * A convenience method to apply this function to a single
     * argument.
     * @param argument the sole argument to this function
     * @return the result of applying this function
     */
    public R apply(T argument) {
        // only a single argument, so ordering doesn't matter
        final ArgMap<T> theArgument = new ArgMap<>();
        theArgument.put(argumentName, argument);
        return apply(theArgument);
    }

    /**
     * Obtain the inner function, if it exists.
     * If this function is f(g(x)), the inner function is g(x).
     * @return an {@code Optional} containing the inner function, or an empty {@code Optional} otherwise
     */
    public Optional<UnaryFunction<? super T, T>> getComposedFunction() {
        return Optional.ofNullable(composedFunction);
    }

    /**
     * Obtain the outer function, if it exists.
     * If this composed function is f(g(x)), the outer function is f(x).
     * @return an {@code Optional} containing the outer function, or an empty {@code Optional} otherwise
     */
    public Optional<UnaryFunction<R, ? extends R>> getComposingFunction() {
        return Optional.ofNullable(composingFunction);
    }

    /**
     * If this is a composed function, return the original function
     * from which this is composed. Note that if the returned {@link Optional}
     * is empty, this is the original function and there is no deeper composition.
     *
     * @return an {@link Optional} that contains a reference to the original function, if any
     */
    public Optional<UnaryFunction<T, R>> getOriginalFunction() {
        return Optional.ofNullable(originalFunction);
    }

    /**
     * Returns a composition of this function with the {@code before} function.
     * Note that this method was renamed to {@code composeWith} since
     * {@link java.util.function.Function#compose(Function)} has a similar
     * signature and can confuse Java.
     * <br>
     * The {@code before} function executes before this function, and its result
     * is fed into the input of this function.
     *
     * @param before the function to compose with this one
     * @return a new function that is the composition of {@code this} with {@code before}
     */
    public UnaryFunction<? super T, R> composeWith(UnaryFunction<? super T, T> before) {
        return new UnaryFunction<>(before.argumentName, getReturnType()) {
            {
                originalFunction = UnaryFunction.this;
                composedFunction = before;
            }

            @Override
            public R apply(ArgVector<T> arguments) {
                T arg = arguments.hasVariableName(getArgumentName()) ?
                        arguments.forVariableName(getArgumentName()) : arguments.elementAt(0L);
                return originalFunction.apply(before.apply(arg));
            }

            @Override
            public UnaryFunction<? super T, R> composeWith(UnaryFunction<? super T, T> before) {
                if (MathUtils.inverseFunctionFor(composedFunction.getClass()) == before.getClass()) {
                    return originalFunction;
                }
                return super.composeWith(before);
            }

            @Override
            public Range<RealType> inputRange(String argName) {
                return before.inputRange(argName);
            }

            @Override
            public Class<T> getArgumentType() {
                return UnaryFunction.this.getArgumentType();
            }
        };
    }

    @Override
    public <V> Function<V, R> compose(Function<? super V, ? extends ArgVector<T>> before) {
        // I have tried and tried to get before cast to the appropriate UnaryFunction type
        // so that I can delegate to composeWith(), but I can't seem to get the bounds right.
        // So instead of disabling this method, let's emit a warning because compose()
        // bypasses the code to track who's composed with whom.
        Logger.getLogger(this.getClass().getTypeName())
                .warning("Calling compose() instead of composeWith() may have unintended results.");
        // The user may still want to do this anyway, especially if they need to compose with a
        // Java function that isn't part of the Tungsten framework.
        return super.compose(before);
    }

    /**
     * Compose this function f(x) with another &ldquo;after&rdquo; function g(x)
     * to create a function g(f(x)).
     * @param after the function which takes the output of this function as its input
     * @return the composed function
     * @param <R2> the return type of the {@code after} and composed functions
     */
    public <R2 extends R> UnaryFunction<T, R2> andThen(UnaryFunction<R, R2> after) {
        final Class<R2> rtnClass = after.getReturnType();

        return new UnaryFunction<>(UnaryFunction.this.argumentName, rtnClass) {
            {
                originalFunction = UnaryFunction.this;
                // TODO see if we can do this a better way
                composingFunction = (UnaryFunction<R2, ? extends R2>) after;
            }

            @Override
            public R2 apply(ArgVector<T> arguments) {
                return after.apply(originalFunction.apply(arguments));
            }

            @Override
            public <R3 extends R2> UnaryFunction<T, R3> andThen(UnaryFunction<R2, R3> after) {
                final Class<R3> r3Class = (Class<R3>) ClassTools.getTypeArguments(NumericFunction.class, after.getClass()).get(1);
                if (MathUtils.inverseFunctionFor(composingFunction.getClass()) == after.getClass()) {
                    return originalFunction.forReturnType(r3Class);
                }
                return super.andThen(after.forReturnType(rtnClass)).forReturnType(r3Class);
            }

            @Override
            public Range<RealType> inputRange(String argName) {
                if (!originalFunction.argumentName.equals(argName)) {
                    throw new IllegalArgumentException("Argument " + argName + " does not exist for this function");
                }
                return originalFunction.inputRange(originalFunction.argumentName);
            }

            @Override
            public Class<T> getArgumentType() {
                return UnaryFunction.this.getArgumentType();
            }
        };
    }

    protected String getArgumentName() {
        return argumentName;
    }

    @Override
    protected boolean checkArguments(ArgVector<T> arguments) {
        // if the input vector doesn't have our expected argument by name,
        // then at least ensure it has exactly 1 unambiguous value in it
        return arguments.arity() == 1L || arguments.hasVariableName(argumentName);
    }

    @Override
    public long arity() {
        return 1L;
    }

    @Override
    public <R2 extends Numeric> UnaryFunction<T, R2> forReturnType(Class<R2> clazz) {
        final Class<R> rtnClass = getReturnType();
        if (rtnClass != null && (clazz == rtnClass || clazz.isAssignableFrom(rtnClass))) {
            // R and R2 are the same, or R2 is a supertype of R
            return (UnaryFunction<T, R2>) this;
        }

        return new UnaryFunction<>(argumentName, clazz) {
            @Override
            public R2 apply(ArgVector<T> arguments) {
                try {
                    return (R2) UnaryFunction.this.apply(arguments).coerceTo(clazz);
                } catch (CoercionException e) {
                    throw new ArithmeticException("Cannot convert function return value: " +
                            e.getMessage());
                }
            }

            @Override
            public Range<RealType> inputRange(String argName) {
                return UnaryFunction.this.inputRange(argName);
            }

            @Override
            public Class<T> getArgumentType() {
                return UnaryFunction.this.getArgumentType();
            }
        };
    }

    @Override
    public String[] expectedArguments() {
        final String[] response = {argumentName};
        return response;
    }
}
