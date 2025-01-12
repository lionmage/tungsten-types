package tungsten.types.functions.impl;

import tungsten.types.Numeric;
import tungsten.types.Range;
import tungsten.types.exceptions.CoercionException;
import tungsten.types.functions.ArgVector;
import tungsten.types.functions.NumericFunction;
import tungsten.types.functions.UnaryFunction;
import tungsten.types.numerics.RealType;
import tungsten.types.numerics.impl.ExactZero;
import tungsten.types.numerics.impl.One;
import tungsten.types.numerics.impl.RealImpl;
import tungsten.types.util.ClassTools;
import tungsten.types.util.MathUtils;
import tungsten.types.util.RangeUtils;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

import static tungsten.types.util.MathUtils.Re;

/**
 * A function that can act as a proxy for another function.
 * Rather than computing values, this function uses a lookup table of
 * values and uses basic interpolation based on a supplied &#x1D700;.
 * Interpolation can be avoided with the use of a special apply method.
 * @param <T> the input type for this proxy function
 * @param <R> the return type for this proxy function
 */
public class ProxyFunction<T extends Numeric & Comparable<? super T>, R extends Numeric> extends UnaryFunction<T, R> {
    private final Class<R> outputClazz = (Class<R>) ClassTools.getTypeArguments(NumericFunction.class, getClass()).get(1);
    private final Range<T> epsilonRange;
    private final Map<T, R> valueMap;
    private final T epsilon;

    /**
     * Basic constructor which takes a variable name, a {@code Map} of
     * discrete input values to discrete output values, and an
     * &#x1D700; value for matching input values inexactly.
     * @param argName the variable name for this function's argument
     * @param source  the mapping of input to output values
     * @param epsilon the tolerance for inexact value matching
     */
    public ProxyFunction(String argName, Map<T, R> source, T epsilon) {
        super(argName);
        try {
            Class<T> inputClazz = (Class<T>) ClassTools.getTypeArguments(NumericFunction.class, getClass()).get(0);
            epsilonRange = new Range<>((T) ExactZero.getInstance(epsilon.getMathContext()).coerceTo(inputClazz),
                    (T) One.getInstance(epsilon.getMathContext()).coerceTo(inputClazz), Range.BoundType.EXCLUSIVE);
        } catch (CoercionException e) {
            throw new IllegalStateException("While constructing an \uD835\uDF00 range", e);
        }
        if (!epsilonRange.contains(epsilon)) {
            throw new IllegalArgumentException("\uD835\uDF00 value outside range: " + epsilonRange);
        }
        this.epsilon = epsilon;
        valueMap = Collections.unmodifiableMap(source);
    }

    @Override
    public R apply(ArgVector<T> arguments) {
        T input = coerceToInnerRange(arguments.elementAt(0L));
        if (valueMap.containsKey(input)) {
            // we have an exact match, so return the exact value
            return valueMap.get(input);
        }
        // otherwise, find the closest-matching key within epsilon
        Optional<R> candidate = valueMap.keySet().parallelStream()
                .filter(key -> MathUtils.areEqualToWithin(Re(key), Re(input), Re(epsilon)))
                .findAny().map(valueMap::get);
        return candidate.orElse(interpolate(input));
    }

    /**
     * Like {@link #apply(ArgVector)}, but just takes a single value input
     * rather than an {@code ArgVector} and does not perform any interpolation
     * whatsoever.  Input values with no mapping will result in {@code null}.
     * @param input the single-value input for this function
     * @return a mapped return value, if one is found, otherwise {@code null}
     */
    public R applyWithoutInterpolation(T input) {
        if (valueMap.containsKey(input)) {
            // we have an exact match, so return the exact value
            return valueMap.get(input);
        }
        // otherwise, find the closest-matching key
        Optional<R> candidate = valueMap.keySet().parallelStream()
                .filter(key -> MathUtils.areEqualToWithin(Re(key), Re(input), Re(epsilon)))
                .findAny().map(valueMap::get);
        return candidate.orElse(null);
    }

    /**
     * Find the closest key in the internal value map to the supplied
     * input value.
     * @param input the input
     * @return the nearest key present in the value map
     */
    public T closestKeyToInput(T input) {
        final RealType zero = new RealImpl(BigDecimal.ZERO, input.getMathContext());
        return valueMap.keySet().stream()
                .min((a, b) -> Re(input.subtract(a).magnitude().subtract(input.subtract(b).magnitude()))
                .compareTo(zero)).orElse(null);
    }

    private R interpolate(T x) {
        final List<T> keys = valueMap.keySet().stream().sorted().collect(Collectors.toList());
        T lower = keys.stream().filter(k -> x.compareTo(k) > 0).findFirst().orElseThrow();
        T upper = keys.get(keys.indexOf(lower) + 1);
        return linearInterpolate(x, lower, valueMap.get(lower), upper, valueMap.get(upper));
    }

    private R linearInterpolate(T x, T x1, R y1, T x2, R y2) {
        assert x.compareTo(x1) >= 0 && x.compareTo(x2) <= 0;
        try {
            RealType rise = (RealType) y2.subtract(y1).coerceTo(RealType.class);
            RealType run = (RealType) x2.subtract(x1).coerceTo(RealType.class);
            RealType intermediate = (RealType) x.subtract(x1).coerceTo(RealType.class);
            return  (R) intermediate.multiply(rise).divide(run).add(y1).coerceTo(outputClazz);
        } catch (CoercionException e) {
            throw new IllegalArgumentException("Unable to coerce intermediate values", e);
        }
    }

    /**
     * Remap input value to fall within a particular range.  This is
     * especially useful for things like periodic functions.
     * <br>
     * The default behavior is to return the input exactly. Subclasses
     * should override this method if this behavior is not desired.
     *
     * @param input an input value to this function
     * @return the remapped input value
     */
    protected T coerceToInnerRange(T input) {
        return input;
    }

    @Override
    public Range<RealType> inputRange(String argName) {
        return RangeUtils.ALL_REALS;
    }
}
