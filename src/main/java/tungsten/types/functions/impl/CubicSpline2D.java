package tungsten.types.functions.impl;

import tungsten.types.Range;
import tungsten.types.functions.ArgVector;
import tungsten.types.functions.UnaryFunction;
import tungsten.types.numerics.RealType;
import tungsten.types.util.MathUtils;

/**
 * A representation of a cubic spline in 2 dimensions, intended for curve-fitting and
 * other applications where cubic splines may be appropriate.  For now, the input
 * argument is assumed to be a variable named <em>x</em>.<br/>
 * The spline function is of the form:<br/>
 * S(x) = a + b(x&minus;x<sub>0</sub>) + c(x&minus;x<sub>0</sub>)<sup>2</sup> + d(x&minus;x<sub>0</sub>)<sup>3</sup>
 */
public class CubicSpline2D extends UnaryFunction<RealType, RealType> {
    private final RealType a, b, c, d;
    private final Range<RealType> scope;

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
