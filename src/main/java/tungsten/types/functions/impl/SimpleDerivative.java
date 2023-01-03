package tungsten.types.functions.impl;

import tungsten.types.Range;
import tungsten.types.annotations.Differentiable;
import tungsten.types.exceptions.CoercionException;
import tungsten.types.functions.*;
import tungsten.types.numerics.RealType;
import tungsten.types.numerics.impl.RealImpl;
import tungsten.types.util.ClassTools;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A simple metafunction that will compute the derivative of a function.
 * This implementation primarily deals with {@link UnaryFunction}s and
 * functions that can be curried to a single input variable.
 *<br/>
 * Subclasses are free to extend the strategy methods.
 *<br/>
 * If a derivative cannot be computed directly from a function, the
 * algorithm will use a numeric method to compute an approximation.
 * The {@code epsilon} parameter controls the size of the &ldquo;straddle&rdquo;
 * surrounding the point where the derivative is being approximated.
 *
 * @param <T> any subclass of RealType will work in this implementation
 */
public class SimpleDerivative<T extends RealType> extends MetaFunction<T, T, T> {
    final private T epsilon;
    final private Range<RealType> epsilonRange = new Range<>(new RealImpl(BigDecimal.ZERO),
            new RealImpl(BigDecimal.ONE), Range.BoundType.EXCLUSIVE);

    public SimpleDerivative(T epsilon) {
        super();
        this.epsilon = (T) epsilon.magnitude();
        if (!epsilonRange.contains(epsilon)) {
            Logger.getLogger(getClass().getName()).log(Level.WARNING,
                    "Epsilon value {} is out of recommended range {}", new Object[]{epsilon, epsilonRange});
        }
    }

    public SimpleDerivative(ArgMap<T> argsToCurry, T epsilon) {
        super(argsToCurry);
        this.epsilon = (T) epsilon.magnitude();
        if (!epsilonRange.contains(epsilon)) {
            Logger.getLogger(getClass().getName()).warning("Epsilon value is out of recommended range: " + epsilon);
        }
    }

    protected boolean hasDerivativeMethod(Class<? extends UnaryFunction> clazz) {
        return Arrays.stream(clazz.getMethods()).filter(m -> m.getParameterCount() < 2)
                .anyMatch(m -> m.isAnnotationPresent(Differentiable.class));
    }

    @Override
    public UnaryFunction<T, T> apply(NumericFunction<T, T> inputFunction) {
        final UnaryFunction<T, T> original;
        if (!(inputFunction instanceof UnaryFunction)) {
            // attempt to curry the function so that we only have one variable left
            NumericFunction<T, T> intermediate = curry(inputFunction);
            if (intermediate.arity() != 1L) {
                throw new IllegalStateException("Insufficient curry mappings to reduce argument list; " +
                        "expected a unary function but produced a function with arity " + intermediate.arity());
            }
            original = (UnaryFunction<T, T>) intermediate;
        } else {
            original = (UnaryFunction<T, T>) inputFunction;
        }
        // handling of composed functions
        if (original.getOriginalFunction().isPresent()) {
            if (original.getComposedFunction().isPresent()) {
                UnaryFunction<T, T> innerfunc = (UnaryFunction<T, T>) original.getComposedFunction().get();
                UnaryFunction<T, T> outerfunc = original.getOriginalFunction().get();
                if (original.getComposingFunction().isPresent()) {
                    // One more level of composition to deal with...
                    UnaryFunction<T, T> afterfunc = (UnaryFunction<T, T>) original.getComposingFunction().get();
                    UnaryFunction<T, T> intermediate = (UnaryFunction<T, T>) outerfunc.composeWith(innerfunc);

                    return chainRuleStrategy(afterfunc, intermediate);
                }
                return chainRuleStrategy(outerfunc, innerfunc);
            }
            if (original.getComposingFunction().isPresent()) {
                UnaryFunction<T, T> innerfunc = original.getOriginalFunction().get();
                UnaryFunction<T, T> outerfunc = (UnaryFunction<T, T>) original.getComposingFunction().get();
                return chainRuleStrategy(outerfunc, innerfunc);
            }
        }
        if (original instanceof Sum) return sumStrategy((Sum<T, T>) original);
        if (original instanceof Quotient) return quotientStrategy((Quotient<T, T>) original);
        if (original instanceof Product) return productStrategy((Product<T, T>) original);
        // check to see if the function has a method annotated with @Differentiable
        for (Method method : original.getClass().getMethods()) {
            if (method.isAnnotationPresent(Differentiable.class)) {
                // check that the method is zero-arg, or single-arg that takes a SimpleDerivative, and returns a UnaryFunction
                if (method.getParameterCount() > 1) continue;
                if (method.getParameterCount() == 1 &&
                        !method.getParameterTypes()[0].isAssignableFrom(SimpleDerivative.class)) continue;
                if (UnaryFunction.class.isAssignableFrom(method.getReturnType())) {
                    Object[] args = method.getParameterCount() == 0 ? null : new Object[] {this};
                    try {
                        return (UnaryFunction<T, T>) method.invoke(original, args);
                    } catch (IllegalAccessException | InvocationTargetException sevEx) {
                        Logger.getLogger(SimpleDerivative.class.getName())
                                .log(Level.SEVERE,
                                        "Reflection problem while accessing @Differentiable method",
                                        sevEx);
                        throw new IllegalStateException(sevEx);
                    }
                }
            }
        }
        // default to the base strategy if we don't have another one
        return baseStrategy(original);
    }

    public UnaryFunction<T, T> chainRuleStrategy(UnaryFunction<T, T> outer, UnaryFunction<T, T> inner) {
        final UnaryFunction<T, T> outerDerivative = SimpleDerivative.this.apply(outer);
        final UnaryFunction<T, T> innerDerivative = SimpleDerivative.this.apply(inner);

        return new UnaryFunction<>(inner.expectedArguments()[0]) {
            @Override
            public T apply(ArgVector<T> arguments) {
                T arg = arguments.elementAt(0L);
                T intermediate = inner.apply(arg);
                return (T) outerDerivative.apply(intermediate).multiply(innerDerivative.apply(arg));
            }

            @Override
            public Range<RealType> inputRange(String argName) {
                final String varName = inner.expectedArguments()[0];
                if (varName.equals(argName)) {
                    return inner.inputRange(argName);
                }
                return null;
            }
        };
    }

    protected UnaryFunction<T, T> sumStrategy(Sum<T, T> summation) {
        Sum<T, T> diffResult = new Sum<>();
        summation.stream().map(this::apply).forEach(diffResult::appendTerm);
        return diffResult;
    }

    protected UnaryFunction<T, T> quotientStrategy(Quotient<T, T> quotient) {
        // f(x) = g(x)/h(x)
        // f'(x) = (g'(x)h(x) - g(x)h'(x)) / (h(x) ^ 2)
        UnaryFunction<T, T> numDiff = this.apply(quotient.getNumerator());
        UnaryFunction<T, T> denomDiff = this.apply(quotient.getDenominator());
        UnaryFunction<T, T> combinedDenom = quotient.getDenominator().andThen(new Pow<>(2L));
        List<Class<?>> argClasses = ClassTools.getTypeArguments(NumericFunction.class, quotient.getClass());
        Class<T> clazz = argClasses.get(1) == null ?  (Class<T>) RealType.class : (Class<T>) argClasses.get(1);
        UnaryFunction<T, T> combinedNumerator =
                new Sum<>(new Product<>(numDiff, quotient.getDenominator()),
                        new Product<>(quotient.getNumerator(), denomDiff).andThen(Negate.getInstance(clazz)));
        return new Quotient<>(combinedNumerator, combinedDenom).simplify();
    }

    protected UnaryFunction<T, T> productStrategy(Product<T, T> product) {
        final String argName = product.expectedArguments()[0];
        if (product.termCount() == 1L) {
            // degenerate case
            return product.stream().map(this::apply).findFirst().orElseThrow();
        } else if (product.termCount() == 2L) {
            // optimized base case for product of two functions
            UnaryFunction<T, T>[] functions = product.stream().toArray(UnaryFunction[]::new);
            assert functions.length == 2;
            UnaryFunction<T, T>[] derivatives = product.stream().map(this::apply).toArray(UnaryFunction[]::new);
            return new Sum<>(new Product<>(argName, functions[0], derivatives[1]),
                    new Product<>(argName, functions[1], derivatives[0]));
        } else {
            // generalized product rule
            Sum<T, T> sumTerm = new Sum<>(argName);
            // f'/f
            product.parallelStream().map(f -> new Quotient<>(argName, this.apply(f), f))
                    .map(Quotient::simplify)
                    .forEach(sumTerm::appendTerm);
            return new Product<>(argName, product, sumTerm);
        }
    }

    protected UnaryFunction<T, T> baseStrategy(UnaryFunction<T, T> input) {
        final String varName = input.expectedArguments()[0];
        final RealType two = new RealImpl(BigDecimal.valueOf(2L), epsilon.getMathContext());

        return new UnaryFunction<>(varName) {
            @Override
            public T apply(ArgVector<T> arguments) {
                T arg = arguments.forVariableName(varName);
                try {
                    // we should be able to get away with this since T is declared as extending RealType
                    return (T) input.apply((T) arg.add(epsilon)).subtract(input.apply((T) arg.subtract(epsilon)))
                            .divide(two.multiply(epsilon)).coerceTo(RealType.class);
                } catch (CoercionException e) {
                    throw new IllegalStateException(e);
                }
            }

            @Override
            public Range<RealType> inputRange(String argName) {
                if (varName.equals(argName)) {
                    return input.inputRange(argName);
                }
                return null;
            }
        };
    }
}
