package tungsten.types.functions.impl;

import tungsten.types.Numeric;
import tungsten.types.Range;
import tungsten.types.functions.ArgVector;
import tungsten.types.functions.Periodic;
import tungsten.types.functions.UnaryFunction;
import tungsten.types.numerics.IntegerType;
import tungsten.types.numerics.RealType;
import tungsten.types.numerics.Sign;
import tungsten.types.numerics.impl.Pi;
import tungsten.types.numerics.impl.RealImpl;
import tungsten.types.numerics.impl.RealInfinity;
import tungsten.types.numerics.impl.Zero;
import tungsten.types.util.MathUtils;
import tungsten.types.util.UnicodeTextEffects;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.Optional;

public class Tan extends Quotient<RealType, RealType> implements Periodic {
    private final RealType epsilon;
    private final RealType halfPi;

    public Tan(String argName, RealType epsilon) {
        super(argName, new Sin(argName, epsilon), new Cos(argName, epsilon));
        this.epsilon = epsilon;
        MathContext ctx = new MathContext(epsilon.getMathContext().getPrecision() + 1,
                epsilon.getMathContext().getRoundingMode());
        halfPi = (RealType) Pi.getInstance(ctx).divide(new RealImpl(BigDecimal.valueOf(2L), ctx));
        tanRange = new Range<>(halfPi.negate(), halfPi, Range.BoundType.EXCLUSIVE);
    }

    public Tan(RealType epsilon) {
        this(MathUtils.THETA, epsilon);
    }

    private final Range<RealType> tanRange;

    @Override
    public RealType apply(ArgVector<RealType> arguments) {
        RealType arg = arguments.elementAt(0L);
        // for the initial test of the argument, we want a range that includes the singularities at both ends
        Range<RealType> tanRangeExt = new Range<>(tanRange.getLowerBound(), tanRange.getUpperBound(), Range.BoundType.INCLUSIVE);
        if (!tanRangeExt.contains(arg) ) {
            final Pi pi = Pi.getInstance(arg.getMathContext());
            // check to see if arg is an integer multiple of pi
            RealType argOverPi = (RealType) arg.divide(pi).magnitude();
            if (((RealType) argOverPi.subtract(argOverPi.floor())).compareTo(epsilon) <= 0) {
                // tan(x) has zero crossings periodically at x=k𝜋 ∀ k ∈ 𝕴
                return new RealImpl(BigDecimal.ZERO, arg.getMathContext());
            }
            while (tanRange.isAbove(arg)) {
                if (MathUtils.areEqualToWithin(arg, halfPi, epsilon)) break;
                arg = (RealType) arg.subtract(pi);
            }
            while (tanRange.isBelow(arg)) {
                if (MathUtils.areEqualToWithin(arg, halfPi.negate(), epsilon)) break;
                arg = (RealType) arg.add(pi);
            }
        }
        // principal zero-crossing is at 0
        if (Zero.isZero(arg)) return new RealImpl(BigDecimal.ZERO, arg.getMathContext());
        if (MathUtils.areEqualToWithin(arg, halfPi, epsilon)) {
            return RealInfinity.getInstance(Sign.POSITIVE, arg.getMathContext());
        } else if (MathUtils.areEqualToWithin(arg, halfPi.negate(), epsilon)) {
            return RealInfinity.getInstance(Sign.NEGATIVE, arg.getMathContext());
        }

        return super.apply(arguments);
    }

    @Override
    public Range<RealType> principalRange() {
        return tanRange;
    }

    @Override
    public RealType period() {
        return Pi.getInstance(tanRange.getUpperBound().getMathContext());
    }

    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder().append("tan");
        Optional<UnaryFunction<RealType, ? extends RealType>> encompassing = this.getComposingFunction();
        encompassing.ifPresent(f -> {
            if (f instanceof Pow) {
                Pow<RealType, RealType> power = (Pow<RealType, RealType>) f;
                Numeric exponent = power.getExponent();
                if (exponent instanceof IntegerType) {
                    int n = ((IntegerType) exponent).asBigInteger().intValueExact();
                    buf.append(UnicodeTextEffects.numericSuperscript(n));
                }
                // TODO figure out what to do with rational exponents
            } else if (f instanceof Negate) {
                buf.insert(0, '\u2212'); // insert a minus sign
            }
        });
        buf.append('(').append(getArgumentName()).append(')');

        return buf.toString();
    }
}
