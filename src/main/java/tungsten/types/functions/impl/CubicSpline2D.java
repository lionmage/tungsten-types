package tungsten.types.functions.impl;

import tungsten.types.Range;
import tungsten.types.functions.ArgVector;
import tungsten.types.functions.UnaryFunction;
import tungsten.types.numerics.RealType;
import tungsten.types.util.MathUtils;

/**
 * A representation of a cubic spline in 2 dimensions, intended for curve-fitting and
 * other applications where cubic splines may be appropriate.  For now, the input
 * argument is assumed to be a variable named <em>x</em>. A typical application
 * might involve multiple splines contained by a {@link PiecewiseFunction} with
 * no smoothing defined or enabled.<br>
 * The spline function is of the form:<br>
 * S(x) = a + b(x&minus;x<sub>0</sub>) + c(x&minus;x<sub>0</sub>)<sup>2</sup> + d(x&minus;x<sub>0</sub>)<sup>3</sup>
 * @see <a href="https://en.wikipedia.org/wiki/Spline_(mathematics)">the Wikipedia article on splines,
 *  which also gives excellent details on a basic algorithm for computing natural cubic splines</a>
 * @see PiecewiseFunction
 */
public class CubicSpline2D extends UnaryFunction<RealType, RealType> {
    private final RealType a, b, c, d;
    private final Range<RealType> scope;

    /**
     * Creates a cubic spline with the listed coefficients.<br>
     * These coefficients directly relate to the spline equation
     * a&nbsp;+&nbsp;b&sdot;(x&minus;x<sub>0</sub>)&nbsp;+&nbsp;c&sdot;(x&minus;x<sub>0</sub>)<sup>2</sup>&nbsp;+&nbsp;d&sdot;(x&minus;x<sub>0</sub>)<sup>3</sup>
     * @param a     the first spline parameter
     * @param b     the second spline parameter
     * @param c     the third spline parameter
     * @param d     the fourth spline parameter
     * @param scope the {@link Range} over which this spline is defined
     */
    public CubicSpline2D(RealType a, RealType b, RealType c, RealType d, Range<RealType> scope) {
        super("x");
        this.a = a;
        this.b = b;
        this.c = c;
        this.d = d;
        this.scope = scope;
    }

    @Override
    public RealType apply(ArgVector<RealType> arguments) {
        if (checkArguments(arguments)) {
            RealType x = arguments.elementAt(0L);
            RealType diff = (RealType) x.subtract(scope.getLowerBound()); // x - x0
            return (RealType) a.add(b.multiply(diff))
                    .add(c.multiply(MathUtils.computeIntegerExponent(diff, 2)))
                    .add(d.multiply(MathUtils.computeIntegerExponent(diff, 3)));
        }
        throw new ArithmeticException("Spline defined over range " + scope +
                " cannot be evaluated for arguments " + arguments);
    }

    @Override
    public Range<RealType> inputRange(String argName) {
        if (getArgumentName().equals(argName)) return scope;
        return null;
    }
}
