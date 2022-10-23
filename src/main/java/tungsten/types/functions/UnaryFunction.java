package tungsten.types.functions;

import tungsten.types.Numeric;
import tungsten.types.Range;
import tungsten.types.exceptions.CoercionException;
import tungsten.types.numerics.RealType;

import java.lang.reflect.ParameterizedType;
import java.util.Optional;
import java.util.function.Function;
import java.util.logging.Logger;

public abstract class UnaryFunction<T extends Numeric, R extends Numeric> extends NumericFunction<T, R> {
    private final String argumentName;
    // if this function is f(x) and we have a composition of h(f(g(x)))
    // then g(x) is the composedFunction (inner function)
    // and h(x) is the composingFunction (outer function)
    private UnaryFunction<? super T, T> composedFunction;
    private UnaryFunction<R, ? extends R> composingFunction;
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
     * <br/>
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
                final Class<T> argClass = (Class<T>)
                        ((Class) ((ParameterizedType) UnaryFunction.this.getClass()
                                .getGenericSuperclass()).getActualTypeArguments()[0]);
                try {
                    T arg = (T) arguments.elementAt(0L).coerceTo(argClass);
                    return originalFunction.apply(before.apply(arg));
                } catch (CoercionException e) {
                    throw new IllegalArgumentException("Argument " + arguments.elementAt(0L) +
                            " could not be coerced to an appropriate argument type", e);
                }
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
        return new UnaryFunction<>(UnaryFunction.this.argumentName) {
            {
                originalFunction = UnaryFunction.this;
                composingFunction = after;
            }

            @Override
            public R2 apply(ArgVector<T> arguments) {
                return after.apply(originalFunction.apply(arguments.elementAt(0L)));
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

    protected void setOriginalFunction(UnaryFunction<T, R> f) {
        if (originalFunction == null) {
            originalFunction = f;
        }
        throw new IllegalStateException("Cannot set the original function reference more than once.");
    }

    protected void setComposedFunction(UnaryFunction<? super T, T> before) {
        composedFunction = before;
    }

    protected void setComposingFunction(UnaryFunction<R, ? extends R> after) {
        composingFunction = after;
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
    public String[] expectedArguments() {
        final String[] response = {argumentName};
        return response;
    }
}
