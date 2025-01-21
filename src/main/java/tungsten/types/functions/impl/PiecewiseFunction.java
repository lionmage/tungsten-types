package tungsten.types.functions.impl;

import tungsten.types.Numeric;
import tungsten.types.Range;
import tungsten.types.exceptions.CoercionException;
import tungsten.types.functions.ArgVector;
import tungsten.types.functions.UnaryFunction;
import tungsten.types.numerics.RealType;

import java.util.*;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * A piecewise function that contains other functions, each of which has a specific range
 * of application.  The function does range checking so that coincident ranges are not
 * allowed.  Ideally, all ranges are contiguous, though points of singularity are allowed.
 * Two adjacent ranges which both have {@link tungsten.types.Range.BoundType#EXCLUSIVE}
 * at their shared bound would mean that bound is considered a singularity.
 * @param <T> the input type for this function
 * @param <R> the return type for this function
 */
public class PiecewiseFunction<T extends Numeric & Comparable<? super T>, R extends Numeric> extends UnaryFunction<T, R> {
    private final Map<Range<T>, UnaryFunction<T, R>> internalMap = new HashMap<>();
    private boolean boundsChecked = false;

    /**
     * Default constructor.  This will create an empty piecewise function
     * with a variable name of &ldquo;x&rdquo;.
     * @param rtnType the return type of this piecewise function
     */
    public PiecewiseFunction(Class<R> rtnType) {
        super("x", rtnType);
    }

    /**
     * Constructor which takes a variable name.
     * @param argName the name of the argument to this piecewise function
     * @param rtnType the return type of this function
     */
    public PiecewiseFunction(String argName, Class<R> rtnType) {
        super(argName, rtnType);
    }

    /**
     * Append a function that will be evaluated over any input values
     * that fall within the given range.
     * @param range the {@code Range} over which the function is valid
     * @param func  the function that is applied for values within {@code range}
     * @throws IllegalArgumentException if any existing function's range contains {@code range},
     *   or if the return type of {@code func} does not match that of this piecewise function
     */
    public void addFunctionForRange(Range<T> range, UnaryFunction<T, R> func) {
        if (internalMap.keySet().parallelStream().anyMatch(r -> r.contains(range))) {
            throw new IllegalArgumentException("Range " + range + " is a subset of an existing range");
        }
        if (!getReturnType().isAssignableFrom(func.getReturnType())) {
            throw new IllegalArgumentException("Return type of function does not match " + getReturnType().getTypeName());
        }
        internalMap.put(range, func);
        boundsChecked = false;  // mappings have changed, so force a re-check before using this function again
    }


    private final Supplier<IllegalArgumentException> outOfBounds =
            () -> new IllegalArgumentException("Function argument is not within any range bounds");

    @Override
    public R apply(ArgVector<T> arguments) {
        if (!boundsChecked) throw new IllegalStateException("checkAggregateBounds() must be called before applying this function");
        T arg = arguments.elementAt(0L);
        Range<T> key = internalMap.keySet().parallelStream().filter(r -> r.contains(arg)).findAny().orElseThrow(outOfBounds);
        return internalMap.get(key).apply(arg);
    }

    /**
     * Checks the current {@link Range} to {@link UnaryFunction} mappings to ensure
     * that ranges are non-overlapping and have complementary bound types
     * (i.e., open vs. closed) where they touch.
     * <br>
     * Note that this current implementation requires calling this method before
     * attempting to apply this function.  Without performing this check first,
     * it's possible to get completely undefined results, or no results at all.
     *
     * @return true if no problems are found, false otherwise
     */
    public boolean checkAggregateBounds() {
        if (internalMap.size() < 2) return true;  // no chance of conflicts, so bail out quickly
        List<Range<T>> sortedRanges = internalMap.keySet().stream().sorted(Comparator.comparing(Range::getLowerBound))
                .collect(Collectors.toList());
        // ensure that none of the ranges overlap
        for (int index = 1; index < sortedRanges.size(); index++) {
            if (sortedRanges.get(index).contains(sortedRanges.get(index - 1).getUpperBound())) {
                return false;
            }
        }
        // ensure that adjacent bounds have complementary bound types
        for (int index = 1; index < sortedRanges.size(); index++) {
            if (sortedRanges.get(index).getLowerBound().equals(sortedRanges.get(index - 1).getUpperBound())) {
                // we can't have ranges touching unless at least one bound is open
                if (sortedRanges.get(index).isLowerClosed() && sortedRanges.get(index - 1).isUpperClosed()) return false;
                // we should log a warning, however, if both bounds are open since this creates a discontinuity
                if (!sortedRanges.get(index).isLowerClosed() && !sortedRanges.get(index - 1).isUpperClosed()) {
                    Logger.getLogger(PiecewiseFunction.class.getName())
                            .log(Level.WARNING, "Bounds as specified will create a discontinuity at {0} = {1}.",
                            new Object[] {getArgumentName(), sortedRanges.get(index).getLowerBound()});
                }
            }
        }
        boundsChecked = true;
        return true; // if we passed the gantlet, return success
    }

    private final Supplier<IllegalStateException> noBoundsFound =
            () -> new IllegalStateException("Piecewise function has no ranges, or the ranges have improperly specified bounds");

    @Override
    public Range<RealType> inputRange(String argName) {
        T lowest = internalMap.keySet().parallelStream().map(Range::getLowerBound).min(T::compareTo).orElseThrow(noBoundsFound);
        T highest = internalMap.keySet().parallelStream().map(Range::getUpperBound).max(T::compareTo).orElseThrow(noBoundsFound);
        Range.BoundType lowerType = internalMap.keySet().stream().filter(r -> r.getLowerBound().equals(lowest)).findFirst()
                .orElseThrow().isLowerClosed() ? Range.BoundType.INCLUSIVE : Range.BoundType.EXCLUSIVE;
        Range.BoundType upperType = internalMap.keySet().stream().filter(r -> r.getUpperBound().equals(highest)).findFirst()
                .orElseThrow().isUpperClosed() ? Range.BoundType.INCLUSIVE : Range.BoundType.EXCLUSIVE;
        try {
            return new Range<>((RealType) lowest.coerceTo(RealType.class), lowerType, (RealType) highest.coerceTo(RealType.class), upperType);
        } catch (CoercionException e) {
            throw new IllegalStateException("Could not coerce bounds to real", e);
        }
    }

    @Override
    public Class<T> getArgumentType() {
        Iterator<UnaryFunction<T, R>> iter = internalMap.values().iterator();
        Class<T> result = iter.next().getArgumentType();
        while (iter.hasNext()) {
            Class<T> toCheck = iter.next().getArgumentType();
            if (toCheck != result) {
                Logger.getLogger(PiecewiseFunction.class.getName()).log(Level.WARNING,
                        "Mismatch in argument types: {0} vs. {1}",
                        new Object[] { result.getTypeName(), toCheck.getTypeName() });
                // prefer the superclass/superinterface
                if (toCheck.isAssignableFrom(result)) result = toCheck;
            }
        }
        return result;
    }

    /**
     * Obtain a read-only view of the internal map of ranges to their functions.
     * @return an unmodifiable {@code Map<Range, UnaryFunction>}
     */
    protected Map<Range<T>, UnaryFunction<T, R>> viewOfFunctionMap() {
        return Collections.unmodifiableMap(internalMap);
    }
}
