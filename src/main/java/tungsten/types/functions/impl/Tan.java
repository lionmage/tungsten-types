package tungsten.types.functions.impl;

import tungsten.types.Numeric;
import tungsten.types.Range;
import tungsten.types.functions.ArgVector;
import tungsten.types.functions.Periodic;
import tungsten.types.functions.UnaryFunction;
import tungsten.types.numerics.IntegerType;
import tungsten.types.numerics.RealType;
import tungsten.types.numerics.impl.Pi;
import tungsten.types.util.MathUtils;
import tungsten.types.util.RangeUtils;
import tungsten.types.util.UnicodeTextEffects;

import java.math.MathContext;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A function that implements tangent for real-valued inputs.
 * This function delegates to {@link MathUtils#tan(RealType)} for
 * calculation, and delegates to {@link Quotient} for
 * computing a derivative and for the purposes of composition.
 * @author Robert Poole, <a href="mailto:Tarquin.AZ@gmail.com">Gmail</a>
 */
public class Tan extends Quotient<RealType, RealType> implements Periodic {
    private final MathContext mctx;

    /**
     * Constructor that takes a variable name and a {@code MathContext}.
     * @param argName the variable name
     * @param mctx    a {@code MathContext} that determines accuracy
     */
    public Tan(String argName, MathContext mctx) {
        super(argName, new Sin(argName, mctx), new Cos(argName, mctx));
        this.mctx = mctx;
    }

    /**
     * Constructor that takes a {@code MathContext}.
     * @param mctx the {@code MathContext} governing accuracy
     */
    public Tan(MathContext mctx) {
        this(MathUtils.THETA, mctx);
    }

    @Override
    public RealType apply(ArgVector<RealType> arguments) {
        RealType arg = arguments.hasVariableName(getArgumentName()) ?
                arguments.forVariableName(getArgumentName()) : arguments.elementAt(0L);

        try {
            return MathUtils.tan(arg);
        } catch (RuntimeException ex) {
            Logger.getLogger(Tan.class.getName()).log(Level.WARNING,
                    "MathUtils.tan() failed for argument " + arg, ex);
            Logger.getLogger(Tan.class.getName()).info("Using superclass to compute result as a fallback.");
            return super.apply(arguments);
        }
    }

    @Override
    public Range<RealType> principalRange() {
        return RangeUtils.getTangentInstance(mctx);
    }

    @Override
    public RealType period() {
        return Pi.getInstance(mctx);
    }

    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder().append("tan");
        Optional<UnaryFunction<RealType, ? extends RealType>> encompassing = this.getComposingFunction();
        encompassing.ifPresent(f -> {
            if (f instanceof Pow) {
                Pow<?, ?> power = (Pow<?, ?>) f;
                Numeric exponent = power.getExponent();
                if (exponent instanceof IntegerType) {
                    int n = ((IntegerType) exponent).asBigInteger().intValueExact();
                    buf.append(UnicodeTextEffects.numericSuperscript(n));
                } else {
                    buf.append("^{").append(exponent).append("}\u2009"); // postpend thin space to help offset closing brace
                }
            } else if (f instanceof Negate) {
                buf.insert(0, '\u2212'); // insert a minus sign
            }
        });
        buf.append('(');
        getComposedFunction().ifPresentOrElse(buf::append,
                () -> buf.append(getArgumentName()));
        buf.append(')');

        return buf.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;  // super handles argument name
        Tan tan = (Tan) o;
        return Objects.equals(mctx, tan.mctx);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), mctx);
    }
}
