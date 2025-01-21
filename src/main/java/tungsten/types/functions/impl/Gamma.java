/*
 * The MIT License
 *
 * Copyright © 2024 Robert Poole <Tarquin.AZ@gmail.com>.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 *
 */

package tungsten.types.functions.impl;

import tungsten.types.Numeric;
import tungsten.types.Range;
import tungsten.types.functions.ArgVector;
import tungsten.types.functions.UnaryFunction;
import tungsten.types.numerics.ComplexType;
import tungsten.types.numerics.IntegerType;
import tungsten.types.numerics.RealType;
import tungsten.types.numerics.impl.Euler;
import tungsten.types.numerics.impl.One;
import tungsten.types.numerics.impl.Pi;
import tungsten.types.numerics.impl.Zero;
import tungsten.types.util.MathUtils;
import tungsten.types.util.RangeUtils;

import java.math.MathContext;

import static tungsten.types.util.MathUtils.*;

/**
 * A generic implementation of &#x1D6AA;(z), the Gamma function.
 * Most calculations use the Stirling approximation to compute
 * ln&#x1D6AA;(z) and exponentiate the result. Rare corner
 * cases may be handled using Weierstrass' formula, which is
 * both slower and less accurate.
 * Since Stirling's approximation is only valid for Re(z)&nbsp;&gt;&nbsp;0,
 * other values must be computed using the relationship:<br>
 * &#x1D6AA;(z)&sdot;&#x1D6AA;(1&nbsp;&minus;&nbsp;z) = &pi;/sin(&pi;z)
 * <br>The default variable name, if none is provided, is <em>z</em>. Note
 * that this class is essentially a functional wrapper for
 * existing methods in {@link MathUtils}.
 * @see MathUtils#lnGamma(Numeric) the implementation of ln&#x1D6AA;(z)
 * @see MathUtils#gamma(Numeric) the implementation of &#x1D6AA;(z)
 * @since 0.4
 */
public class Gamma extends UnaryFunction<Numeric, Numeric> {
    private final Range<RealType> argRange = RangeUtils.getGammaArgumentInstance();

    public Gamma(String varName) {
        super(varName, Numeric.class);
    }

    public Gamma() {
        super("z", Numeric.class);
    }

    @Override
    public Numeric apply(ArgVector<Numeric> arguments) {
        Numeric z = arguments.hasVariableName(this.getArgumentName()) ?
                arguments.forVariableName(this.getArgumentName()) :
                arguments.elementAt(0L);
        if (!argRange.contains(Arg(z))) {
            // can't use Stirling's approximation, so fall back to Weierstrass
            return MathUtils.gamma(z);
        }
        switch (Re(z).sign()) {
            case ZERO:
                if (Zero.isZero(Im(z))) {
                    throw new ArithmeticException("\uD835\uDEAA(z) is not analytic at 0");
                }
                // can't use Stirling's approximation here, so use the slow way to compute Gamma
                return MathUtils.gamma(z);
            case POSITIVE:
                final Euler e = Euler.getInstance(z.getMathContext());
                Numeric logGamma = MathUtils.lnGamma(z);
                return logGamma instanceof ComplexType ? e.exp((ComplexType) logGamma) :
                        e.exp(Re(logGamma));
            case NEGATIVE:
                if (z.isCoercibleTo(IntegerType.class)) {
                    // Gamma is not analytic at negative integers
                    throw new ArithmeticException("\uD835\uDEAA(z) is not analytic for negative integers");
                }
                // we need to use the reflection formula here
                return computeForNegativeReal(z);
        }
        throw new ArithmeticException("Unable to compute \uD835\uDEAA(" + z + ")");
    }

    private Numeric computeForNegativeReal(Numeric z) {
        final MathContext ctx = z.getMathContext();
        final Euler e = Euler.getInstance(ctx);
        final RealType pi = Pi.getInstance(ctx);
        Numeric piz = z.multiply(pi); // this will be either complex or real
        Numeric factor = pi.divide(piz instanceof ComplexType ?
                sin((ComplexType) piz) : sin((RealType) piz));
        // compute 𝚪(1 − z) using ln𝚪
        Numeric lng1minz = MathUtils.lnGamma(One.getInstance(ctx).subtract(z));
        return factor.divide(lng1minz instanceof ComplexType ?
                e.exp((ComplexType) lng1minz) : e.exp(Re(lng1minz)));
    }

    @Override
    public Range<RealType> inputRange(String argName) {
        if (argName != null && argName.startsWith(getArgumentName())) {
            // covers z.arg, the argument of a complex number
            if (argName.endsWith(".arg")) return argRange;
            // covers z, z.re, z.im, etc.
            return RangeUtils.ALL_REALS;
        }
        return null;
    }

    @Override
    public Class<Numeric> getArgumentType() {
        return Numeric.class;
    }
}
