package tungsten.types.functions.impl;

import tungsten.types.Numeric;
import tungsten.types.Range;
import tungsten.types.exceptions.CoercionException;
import tungsten.types.functions.ArgVector;
import tungsten.types.functions.UnaryFunction;
import tungsten.types.numerics.RealType;

import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class PiecewiseFunction<T extends Numeric & Comparable<? super T>, R extends Numeric> extends UnaryFunction<T, R> {
    private final Map<Range<T>, UnaryFunction<T, R>> internalMap = new HashMap<>();

    public PiecewiseFunction() {
        super("x");
    }

    public PiecewiseFunction(String argName) {
        super(argName);
    }

    public void addFunctionForRange(Range<T> range, UnaryFunction<T, R> func) {
        if (internalMap.keySet().parallelStream().anyMatch(r -> r.contains(range))) {
            throw new IllegalArgumentException("Range " + range + " is a subset of an existing range.");
        }
        internalMap.put(range, func);
    }


    private final Supplier<IllegalArgumentException> outOfBounds =
            () -> new IllegalArgumentException("Function argument is not within any range bounds.");

    @Override
    public R apply(ArgVector<T> arguments) {
        T arg = arguments.elementAt(0L);
        Range<T> key = internalMap.keySet().parallelStream().filter(r -> r.contains(arg)).findAny().orElseThrow(outOfBounds);
        return internalMap.get(key).apply(arg);
    }

    public boolean checkAggregateBounds() {
        if (internalMap.size() < 2) return true;
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
                // we can't have ranges touching unless one bound is closed and the complementary bound is open
                if (sortedRanges.get(index).isLowerClosed() == sortedRanges.get(index - 1).isUpperClosed()) return false;
            }
        }
        return true; // if we passed the gantlet, return success
    }

    private final Supplier<IllegalStateException> noBoundsFound =
            () -> new IllegalStateException("Piecewise function has no ranges, or the ranges have improperly specified bounds.");

    @Override
    public Range<RealType> inputRange(String argName) {
        T lowest = internalMap.keySet().parallelStream().map(Range::getLowerBound).min(T::compareTo).orElseThrow(noBoundsFound);
        T highest = internalMap.keySet().parallelStream().map(Range::getUpperBound).min(T::compareTo).orElseThrow(noBoundsFound);
        Range.BoundType lowerType = internalMap.keySet().stream().filter(r -> r.getLowerBound().equals(lowest)).findFirst()
                .orElseThrow().isLowerClosed() ? Range.BoundType.INCLUSIVE : Range.BoundType.EXCLUSIVE;
        Range.BoundType upperType = internalMap.keySet().stream().filter(r -> r.getUpperBound().equals(highest)).findFirst()
                .orElseThrow().isUpperClosed() ? Range.BoundType.INCLUSIVE : Range.BoundType.EXCLUSIVE;
        try {
            return new Range<>((RealType) lowest.coerceTo(RealType.class), lowerType, (RealType) highest.coerceTo(RealType.class), upperType);
        } catch (CoercionException e) {
            throw new IllegalStateException("Could not coerce bounds to real.", e);
        }
    }
}
