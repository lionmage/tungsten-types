package tungsten.types.functions.impl;

import tungsten.types.Range;
import tungsten.types.annotations.Differentiable;
import tungsten.types.functions.ArgVector;
import tungsten.types.functions.UnaryFunction;
import tungsten.types.numerics.RealType;
import tungsten.types.numerics.Sign;
import tungsten.types.numerics.impl.One;
import tungsten.types.numerics.impl.RealImpl;

import java.math.BigDecimal;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * A real-valued piecewise function.  The {@code epsilon} argument serves two functions:
 * to support differentiation, and to be used in smoothing transitions between functions.
 * For {@link SmoothingType#SIGMOID}, the {@code alpha} parameter can override this.
 */
public class RealPiecewiseFunction extends PiecewiseFunction<RealType, RealType> {
    public enum SmoothingType { NONE, LINEAR, SIGMOID }

    final RealType epsilon;
    RealType alpha;
    SmoothingType smoothing = SmoothingType.NONE;
    List<Range<RealType>> transitionZones = Collections.emptyList();
    List<Sigmoid> sigmoids = Collections.emptyList();

    public RealPiecewiseFunction(String argName, RealType epsilon) {
        super(argName);
        this.epsilon = epsilon;
    }

    public RealPiecewiseFunction(RealType epsilon) {
        super();
        this.epsilon = epsilon;
    }

    public RealPiecewiseFunction(String argName, RealType epsilon, SmoothingType smoothing) {
        this(argName, epsilon);
        this.smoothing = smoothing;
    }

    public RealPiecewiseFunction(RealType epsilon, SmoothingType smoothing) {
        this(epsilon);
        this.smoothing = smoothing;
    }

    private void computeTransitionZones() {
        final RealType TWO = new RealImpl(BigDecimal.valueOf(2L), epsilon.getMathContext());
        RealType alpha0 = alpha == null ? (RealType) TWO.multiply(epsilon) : alpha;
        List<Range<RealType>> zones = new ArrayList<>();
        Map<Range<RealType>, UnaryFunction<RealType, RealType>> fnMap = viewOfFunctionMap();
        List<Range<RealType>> ranges = fnMap.keySet().stream().sorted().collect(Collectors.toList());
        List<Sigmoid> sigFunctions = new ArrayList<>();
        for (int i = 0; i < ranges.size() - 1; i++) {
            // taking the average between these values will help if there's a gap
            RealType x0 = (RealType) ranges.get(i).getUpperBound().add(ranges.get(i + 1).getLowerBound()).divide(TWO);
            RealType delta = (RealType) ranges.get(i + 1).getLowerBound().subtract(ranges.get(i).getUpperBound());
            if (delta.compareTo(alpha0) >= 0) alpha0 = (RealType) TWO.multiply(delta);  // keep the transition zone wider than the gap

            zones.add(new Range<>((RealType) x0.subtract(alpha0), (RealType) x0.add(alpha0), Range.BoundType.INCLUSIVE));
            if (smoothing == SmoothingType.SIGMOID) {
                sigFunctions.add(new Sigmoid(x0, alpha0));
            }
        }
        Logger.getLogger(RealPiecewiseFunction.class.getName()).log(Level.INFO,
                "Generated {} transition zones for {} distinct function domains, final alpha={}",
                new Object[] { zones.size(), ranges.size(), alpha0 });
        if (smoothing == SmoothingType.SIGMOID) {
            Logger.getLogger(RealPiecewiseFunction.class.getName()).log(Level.FINE,
                    "Generated {} sigmoid functions for x0 values {}",
                    new Object[] { sigFunctions.size(),
                            sigFunctions.stream().map(Sigmoid::getCentroid).collect(Collectors.toList()) });
        }
        transitionZones = zones;
        sigmoids = sigFunctions;
    }

    /**
     * Sets the &#x1D6FC; value which governs transition zone width. This value is ignored for
     * {@link SmoothingType#NONE}.  For {@link SmoothingType#LINEAR}, the current implementation
     * treats &#x1D6FC; as an override for &#x1D700; (denoted {@code epsilon} in method and function
     * arguments).<br/>
     * In the case of {@link SmoothingType#SIGMOID}, &#x1D6FC; is directly supplied as the
     * {@code alpha} parameter to the {@link Sigmoid} constructor. For the sake of simplicity, the
     * same &#x1D6FC; value is applied to all internally generated {@link Sigmoid} instances unless
     * a gap between ranges is encountered which requires broader coverage.
     *
     * @param alpha the value of &#x1D6FC;
     */
    public void setAlpha(RealType alpha) {
        if (alpha == null) throw new IllegalArgumentException("Supplied alpha must be non-null");
        if (smoothing != SmoothingType.SIGMOID) {
            // I originally intended to throw an IllegalStateException here, but alpha can be
            // useful for swamping epsilon, so a warning will suffice for now.
            Logger.getLogger(RealPiecewiseFunction.class.getName())
                    .warning("Alpha value mainly applies when smoothing = SIGMOID; currently smoothing = " + smoothing);
        }
        if (alpha.sign() != Sign.POSITIVE) {
            throw new IllegalArgumentException("Alpha must be a positive value.");
        }
        this.alpha = alpha;
        if (viewOfFunctionMap().size() > 1) {
            computeTransitionZones();
        }
    }

    /**
     * Set the smoothing type for this piecewise function.
     * This method will also reset internal state on any collections containing
     * transition zone ranges or sigmoid instances if the chosen smoothing
     * type does not need them.
     *
     * @param smoothing the chosen type of smoothing to apply
     */
    public void setSmoothing(SmoothingType smoothing) {
        if (smoothing == SmoothingType.NONE) {
            transitionZones = Collections.emptyList();
        }
        if (smoothing != SmoothingType.SIGMOID) {
            sigmoids = Collections.emptyList();
        }
        this.smoothing = smoothing;
        if (alpha != null && smoothing == SmoothingType.SIGMOID) {
            Logger.getLogger(RealPiecewiseFunction.class.getName())
                    .warning("Potential stale \uD835\uDEFC value: " + alpha);
        }
    }

    @Override
    public RealType apply(ArgVector<RealType> arguments) {
        if ((smoothing != SmoothingType.NONE && transitionZones.size() != viewOfFunctionMap().size() - 1) ||
                (smoothing == SmoothingType.SIGMOID && sigmoids.size() != transitionZones.size())) {
            computeTransitionZones();
        }

        RealType arg = arguments.elementAt(0);

        Optional<Range<RealType>> rangeForTransition = smoothing == SmoothingType.NONE ? Optional.empty() :
                transitionZones.stream().filter(range -> range.contains(arg)).findFirst();
        switch (smoothing) {
            case NONE:
                return super.apply(arguments);
            case LINEAR:
                if (rangeForTransition.isEmpty()) {
                    return super.apply(arguments);
                } else {
                    Range<RealType> rr = rangeForTransition.get();
                    return linearInterpolate(arg, rr.getLowerBound(), super.apply(rr.getLowerBound()),
                            rr.getUpperBound(), super.apply(rr.getUpperBound()));
                }
            case SIGMOID:
                if (rangeForTransition.isEmpty()) {
                    return super.apply(arguments);
                }
                int index = transitionZones.indexOf(rangeForTransition.get());
                Sigmoid sig = sigmoids.get(index);
                List<Range<RealType>> orderedRanges = viewOfFunctionMap().keySet().stream().sorted().collect(Collectors.toList());
                UnaryFunction<RealType, RealType> f1 = viewOfFunctionMap().get(orderedRanges.get(index));
                UnaryFunction<RealType, RealType> f2 = viewOfFunctionMap().get(orderedRanges.get(index + 1));
                return sigmoidInterpolate(arg, sig, f1, f2);
            default:
                throw new IllegalStateException("No strategy for smoothing type " + smoothing);
        }
    }

    private RealType linearInterpolate(RealType x, RealType x1, RealType y1, RealType x2, RealType y2) {
        assert x.compareTo(x1) >= 0 && x.compareTo(x2) <= 0;
        RealType rise = (RealType) y2.subtract(y1);
        RealType run = (RealType) x2.subtract(x1);
        RealType intermediate = (RealType) x.subtract(x1);
        return  (RealType) intermediate.multiply(rise).divide(run).add(y1);
    }

    private RealType sigmoidInterpolate(RealType x, Sigmoid s,
                                        UnaryFunction<RealType, RealType> f1, UnaryFunction<RealType, RealType> f2) {
        RealType weight2 = s.apply(x);
        RealType weight1 = (RealType) One.getInstance(epsilon.getMathContext()).subtract(weight2);
        return (RealType) f1.apply(x).multiply(weight1).add(f2.apply(x).multiply(weight2));
    }

    @Differentiable
    public UnaryFunction<RealType, RealType> diff() {
        final SimpleDerivative<RealType> diffMachine = new SimpleDerivative<>(epsilon);
        RealPiecewiseFunction result = new RealPiecewiseFunction(getArgumentName(), epsilon);
        viewOfFunctionMap().entrySet().parallelStream().forEach(entry -> {
            UnaryFunction<RealType, RealType> f = entry.getValue();
            String f_argName = f.expectedArguments()[0];
            UnaryFunction<RealType, RealType> f_diff = diffMachine.apply(f);
            Range<RealType> f_range = Range.chooseNarrowest(f.inputRange(f_argName), f_diff.inputRange(f_argName));
            // now grab the original Range mapped to the original function and clip it with the range computed above
            Range<RealType> mapRange = Range.chooseNarrowest(entry.getKey(), f_range);
            result.addFunctionForRange(mapRange, f_diff);
        });

        if (!result.checkAggregateBounds()) {
            Logger.getLogger(RealPiecewiseFunction.class.getName()).log(Level.WARNING,
                    "Bounds check failed for derived piecewise function.");
        }
        return result;
    }
}
