package tungsten.types.functions.impl;

import tungsten.types.Range;
import tungsten.types.functions.ArgVector;
import tungsten.types.functions.UnaryFunction;
import tungsten.types.numerics.RealType;
import tungsten.types.numerics.impl.Euler;
import tungsten.types.numerics.impl.One;
import tungsten.types.util.RangeUtils;
import tungsten.types.util.UnicodeTextEffects;

import java.math.MathContext;
import java.util.Objects;

/**
 * Implementation of the sigmoid function &#x1D70E;(x).
 *
 * @see <a href="http://matlab.cheme.cmu.edu/2011/10/30/smooth-transitions-between-discontinuous-functions/">&ldquo;Smooth transitions
 *   between discontinuous functions&rdquo; by John Kitchin</a>
 */
public class Sigmoid extends UnaryFunction<RealType, RealType> {
    private final RealType x0;
    private final RealType alpha;

    /**
     * Construct a simple sigmoid function, &#x1D70E;(x)
     * centered around x<sub>0</sub> with the width of the
     * transition from 0 to 1 determined by &#x1D6FC; (denoted by {@code alpha}).
     *
     * @param varName the variable name for this sigmoid function
     * @param x0      the crossover point where this function's output transitions from 0 to 1
     * @param alpha   the value which determines the width of this function's transition from 0 to 1
     */
    public Sigmoid(String varName, RealType x0, RealType alpha) {
        super(varName);
        this.x0 = x0;
        this.alpha = alpha.magnitude();  // this parameter should always be positive
    }

    public Sigmoid(RealType x0, RealType alpha) {
        this("x", x0, alpha);
    }

    @Override
    public RealType apply(ArgVector<RealType> arguments) {
        RealType arg = arguments.elementAt(0L);
        final MathContext ctx = arguments.getMathContext() != null ? arguments.getMathContext() : arg.getMathContext();
        final Euler e = Euler.getInstance(ctx);
        RealType exponent = (RealType) arg.subtract(x0).divide(alpha).negate();
        return (RealType) One.getInstance(ctx).add(e.exp(exponent)).inverse();
    }

    @Override
    public Range<RealType> inputRange(String argName) {
        return RangeUtils.ALL_REALS;
    }

    public RealType getCentroid() {
        return x0;
    }

    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder().append("\uD835\uDF0E");  // surrogate pair for U+1D70E, ????
        buf.append('(').append(getArgumentName()).append(')');
        buf.append(", x").append(UnicodeTextEffects.numericSubscript(0)).append('=').append(x0);
        buf.append(", \uD835\uDEFC=").append(alpha);  // surrogate pair for U+1D6FC, ????
        return buf.toString();
    }

    @Override
    public int hashCode() {
        return Objects.hash(getArgumentName(), x0, alpha);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof Sigmoid) {
            Sigmoid other = (Sigmoid) obj;
            return Objects.equals(getArgumentName(), other.getArgumentName()) &&
                    x0.equals(other.getCentroid()) &&
                    alpha.equals(other.alpha);
        }
        return false;
    }
}
