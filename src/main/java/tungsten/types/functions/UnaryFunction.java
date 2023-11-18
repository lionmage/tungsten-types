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
import tungsten.types.util.MathUtils;

import java.lang.reflect.ParameterizedType;
import java.util.Optional;
import java.util.function.Function;
import java.util.logging.Logger;

public abstract class UnaryFunction<T extends Numeric, R extends Numeric> extends NumericFunction<T, R> {
    private final String argumentName;
    // if this function is f(x) and we have a composition of h(f(g(x)))
    // then g(x) is the composedFunction (inner function)
    // and h(x) is the composingFunction (outer function)
    protected UnaryFunction<? super T, T> composedFunction;
    protected UnaryFunction<R, ? extends R> composingFunction;
    private UnaryFunction<T, R> originalFunction;

    protected UnaryFunction(String varName) {
        this.argumentName = varName;
    }

    public R apply(T argument) {
        // only a single argument, so ordering doesn't matter
        final ArgMap<T> theArgument = new ArgMap<>();
        theArgument.put(argumentName, argument);
        return apply(theArgument);
    }

    public Optional<UnaryFunction<? super T, T>> getComposedFunction() {
        return Optional.ofNullable(composedFunction);
    }

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
     * @return a new function that is the composition with this and {@code before}
     */
    public UnaryFunction<? super T, R> composeWith(UnaryFunction<? super T, T> before) {
        return new UnaryFunction<>(before.argumentName) {
            {
                originalFunction = UnaryFunction.this;
                composedFunction = before;
            }

            @Override
            public R apply(ArgVector<T> arguments) {
                T arg = arguments.elementAt(0L);
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
                return before.inputRange(before.argumentName);
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

    public <R2 extends R> UnaryFunction<T, R2> andThen(UnaryFunction<R, R2> after) {
        final Class<R2> rtnClass = (Class<R2>)
                ((Class) ((ParameterizedType) after.getClass()
                        .getGenericSuperclass()).getActualTypeArguments()[1]);

        return new UnaryFunction<>(UnaryFunction.this.argumentName) {
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
                final Class<R3> r3Class = (Class<R3>)
                        ((Class) ((ParameterizedType) after.getClass()
                                .getGenericSuperclass()).getActualTypeArguments()[1]);
                if (MathUtils.inverseFunctionFor(composingFunction.getClass()) == after.getClass()) {
                    return originalFunction.forReturnType(r3Class);
                }
                return super.andThen(after.forReturnType(rtnClass)).forReturnType(r3Class);
            }

            @Override
            public Range<RealType> inputRange(String argName) {
                if (!originalFunction.argumentName.equals(argName)) {
                    throw new IllegalArgumentException("Argument " + argName + " does not exist for this function.");
                }
                return originalFunction.inputRange(originalFunction.argumentName);
            }
        };
    }

    protected String getArgumentName() {
        return argumentName;
    }

    @Override
    protected boolean checkArguments(ArgVector<T> arguments) {
        return arguments.arity() == 1L && arguments.hasVariableName(argumentName);
    }

    @Override
    public long arity() {
        return 1L;
    }

    @Override
    public <R2 extends Numeric> UnaryFunction<T, R2> forReturnType(Class<R2> clazz) {
        final Class<R> rtnClass = (Class<R>)
                ((Class) ((ParameterizedType) this.getClass()
                        .getGenericSuperclass()).getActualTypeArguments()[1]);
        if (clazz == rtnClass || clazz.isAssignableFrom(rtnClass)) {
            // R and R2 are the same, or R2 is a supertype of R
            return (UnaryFunction<T, R2>) this;
        }

        return new UnaryFunction<>(argumentName) {
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
        };
    }

    @Override
    public String[] expectedArguments() {
        final String[] response = {argumentName};
        return response;
    }
}
