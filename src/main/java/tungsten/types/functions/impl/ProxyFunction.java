package tungsten.types.functions.impl;

import tungsten.types.Numeric;
import tungsten.types.Range;
import tungsten.types.exceptions.CoercionException;
import tungsten.types.functions.ArgVector;
import tungsten.types.functions.UnaryFunction;
import tungsten.types.numerics.RealType;
import tungsten.types.numerics.impl.ExactZero;
import tungsten.types.numerics.impl.One;
import tungsten.types.numerics.impl.RealImpl;
import tungsten.types.util.MathUtils;
import tungsten.types.util.RangeUtils;

import java.lang.reflect.ParameterizedType;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

public class ProxyFunction<T extends Numeric & Comparable<? super T>, R extends Numeric> extends UnaryFunction<T, R> {
    private final Class<T> inputClazz = (Class<T>) ((Class) ((ParameterizedType) getClass()
            .getGenericSuperclass()).getActualTypeArguments()[0]);
    private final Class<R> outputClazz = (Class<R>) ((Class) ((ParameterizedType) getClass()
            .getGenericSuperclass()).getActualTypeArguments()[1]);
    private final Range<T> epsilonRange;
    private final Map<T, R> valueMap;
    private final T epsilon;

    public ProxyFunction(String argName, Map<T, R> source, T epsilon) {
        super(argName);
        try {
            epsilonRange = new Range<>((T) ExactZero.getInstance(epsilon.getMathContext()).coerceTo(inputClazz),
                    (T) One.getInstance(epsilon.getMathContext()).coerceTo(inputClazz), Range.BoundType.EXCLUSIVE);
        } catch (CoercionException e) {
            throw new IllegalStateException(e);
        }
        if (!epsilonRange.contains(epsilon)) {
            throw new IllegalArgumentException("Epsilon value outside range: " + epsilonRange);
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
        // otherwise, find the closest-matching key
        Optional<R> candidate = valueMap.keySet().parallelStream()
                .filter(key -> MathUtils.areEqualToWithin(reify(key), reify(input), reify(epsilon)))
                .findAny().map(valueMap::get);
        return candidate.orElse(interpolate(input));
    }

    public R applyWithoutInterpolation(T input) {
        if (valueMap.containsKey(input)) {
            // we have an exact match, so return the exact value
            return valueMap.get(input);
        }
        // otherwise, find the closest-matching key
        Optional<R> candidate = valueMap.keySet().parallelStream()
                .filter(key -> MathUtils.areEqualToWithin(reify(key), reify(input), reify(epsilon)))
                .findAny().map(valueMap::get);
        return candidate.orElse(null);
    }

    public T closestKeyToInput(T input) {
        final RealType zero = new RealImpl(BigDecimal.ZERO, input.getMathContext());
        return valueMap.keySet().stream().min((a, b) -> reify((T) input.subtract(a).magnitude().subtract(input.subtract(b).magnitude()))
                .compareTo(zero)).orElse(null);
    }

    private RealType reify(T value) {
        try {
            return (RealType) value.coerceTo(RealType.class);
        } catch (CoercionException e) {
            throw new IllegalArgumentException("Cannot coerce " + value + " to real", e);
        }
    }

    private R interpolate(T x) {
        final List<T> keys = valueMap.keySet().stream().sorted().collect(Collectors.toList());
        T lower = keys.stream().filter(k -> x.compareTo(k) > 0).findFirst().orElseThrow();
        T upper = keys.get(keys.indexOf(lower) + 1);
        return  linearInterpolate(x, lower, valueMap.get(lower), upper, valueMap.get(upper));
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
     * <br/>
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
