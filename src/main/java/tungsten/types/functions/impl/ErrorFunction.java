/*
 * The MIT License
 *
 * Copyright © 2026 Robert Poole <Tarquin.AZ@gmail.com>.
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

import tungsten.types.Range;
import tungsten.types.annotations.Differentiable;
import tungsten.types.functions.ArgVector;
import tungsten.types.functions.UnaryFunction;
import tungsten.types.numerics.RealType;
import tungsten.types.numerics.impl.Pi;
import tungsten.types.numerics.impl.RealImpl;
import tungsten.types.util.RangeUtils;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/**
 * Implementation of the Gauss Error Function, erf(x) for real x.
 * @see <a href="https://en.wikipedia.org/wiki/Error_function">the Wikipedia article</a>
 * @author Robert Poole and Gemma&nbsp;4
 */
public class ErrorFunction extends UnaryFunction<RealType, RealType> {
    public ErrorFunction(String varName) {
        super(varName, RealType.class);
    }

    @Override
    public RealType apply(ArgVector<RealType> arguments) {
        RealType arg = arguments.hasVariableName(getArgumentName()) ?
                arguments.forVariableName(getArgumentName()) : arguments.elementAt(0L);
        final MathContext ctx = arguments.getMathContext() != null ? arguments.getMathContext() : arg.getMathContext();

        return new RealImpl(erf(arg.asBigDecimal(), ctx), ctx);
    }

    private static final BigDecimal TWO = BigDecimal.valueOf(2L);
    private static final BigDecimal HALF = new BigDecimal("0.5");

    /**
     * Computes erf(x) using a hybrid approach:
     * Taylor Series for small x (converges fast).
     * Continued Fraction for large x (avoids cancellation error).
     */
    public static BigDecimal erf(BigDecimal x, MathContext mc) {
        if (x.signum() == 0) return BigDecimal.ZERO;
        if (x.signum() == -1) return erf(x.negate(), mc).negate();

        // Threshold: Taylor series is stable for |x| < 2.
        // Beyond this, the Continued Fraction for erfc(x) is much more efficient.
        if (x.abs().compareTo(TWO) < 0) {
            return taylorErf(x, mc);
        } else {
            // erf(x) = 1 - erfc(x)
            BigDecimal erfcVal = erfcContinuedFraction(x, mc);
            return BigDecimal.ONE.subtract(erfcVal, mc);
        }
    }

    /**
     * Implementation of Taylor Series for small x.
     */
    private static BigDecimal taylorErf(BigDecimal x, MathContext mc) {
        final MathContext internalMc = new MathContext(mc.getPrecision() + 5, RoundingMode.HALF_UP);
        final BigDecimal threshold = BigDecimal.ONE.movePointLeft(mc.getPrecision() + 2);
        BigDecimal sum = BigDecimal.ZERO;
        BigDecimal term = x; // Initial term (n=0) is x
        long n = 0L;

        while (term.abs().compareTo(threshold) > 0) {
            sum = sum.add(term, internalMc);
            n++;
            // Ratio calculation for efficiency: term_n / term_{n-1}
            BigDecimal numerator = x.pow(2, internalMc).negate()
                    .multiply(BigDecimal.valueOf(2L * n - 1L), internalMc);
            BigDecimal denominator = BigDecimal.valueOf(n).multiply(BigDecimal.valueOf(2L * n + 1L), internalMc);
            term = term.multiply(numerator.divide(denominator, internalMc), internalMc);
            if (n > 1000L) break;
        }

        Pi pi = Pi.getInstance(internalMc);
        BigDecimal multiplier = TWO.divide(pi.asBigDecimal().sqrt(internalMc), internalMc);
        return multiplier.multiply(sum, mc);
    }

    /**
     * Implementation of erfc(x) using Continued Fraction and Modified Lentz's Method.
     * Formula: erfc(x) = (e^-x^2 / sqrt(pi)) * [ 1 / (x + 1/2 / (x + 1 / (x + 3/2 / ...))) ]
     */
    private static BigDecimal erfcContinuedFraction(BigDecimal x, MathContext mc) {
        MathContext internalMc = new MathContext(mc.getPrecision() + 5, RoundingMode.HALF_UP);

        // The CF part: We evaluate the fraction K(x) using Lentz's Method
        // a_n = (n/2), b_n = x
        BigDecimal K = lentzMethodCF(x, internalMc);

        // Calculate e^(-x^2)
        BigDecimal xSquaredNeg = x.multiply(x, internalMc).negate();
        BigDecimal expPart = exp(xSquaredNeg, internalMc);

        // Calculate 1/sqrt(pi)
        Pi pi = Pi.getInstance(internalMc);
        BigDecimal sqrtPiInv = BigDecimal.ONE.divide(pi.asBigDecimal().sqrt(internalMc), internalMc);

        return expPart.multiply(sqrtPiInv, internalMc).multiply(K, internalMc).round(mc);
    }

    /**
     * Modified Lentz's Method for evaluating Continued Fractions.
     * Evaluates the CF: b0 + a1/(b1 + a2/(b2 + ...))
     */
    private static BigDecimal lentzMethodCF(BigDecimal x, MathContext mc) {
        final BigDecimal tiny = BigDecimal.ONE.movePointLeft(mc.getPrecision() + 5);
        final BigDecimal threshold = BigDecimal.ONE.movePointLeft(mc.getPrecision() + 2);

        // In our specific CF for erfc: a_n = n/2, b_n = x
        // We start with the first term of the expansion
        BigDecimal f = x;
        BigDecimal C = x;
        BigDecimal D = BigDecimal.ZERO;

        // We iterate through terms n=2, 3, 4...
        // The pattern for a_n is: a2=0.5, a3=1.0, a4=1.5, etc. => a_n = (n-1)/2
        for (long n = 2L; n < 500L; n++) {
            BigDecimal an = BigDecimal.valueOf(n - 1L).multiply(HALF, mc);
            BigDecimal bn = x;

            // Note: This is a simplified version of Lentz's for the specific structure
            // where all b_i are x and a_i increments.
            D = bn.add(an.multiply(D, mc).divide(bn, mc), mc); // D = b_n + a_n/D_{n-1} (simplified)
            if (D.abs().compareTo(tiny) < 0) D = tiny;
            C = bn.add(an.multiply(C, mc).divide(bn, mc), mc);
            if (C.abs().compareTo(tiny) < 0) C = tiny;

            BigDecimal delta = C.multiply(D, mc).divide(bn, mc); // Simplified ratio update
            // Note: In the standard Lentz version for b_n/a_n, we track the product of deltas.
            // For this specific CF structure, we calculate the convergence step:
            f = f.multiply(delta, mc);

            if (delta.subtract(BigDecimal.ONE).abs().compareTo(threshold) < 0) {
                break;
            }
        }

        return f;
    }

    /**
     * Taylor Series for e<sup>x</sup>, highly efficient for negative x.
     */
    private static BigDecimal exp(BigDecimal z, MathContext mc) {
        final BigDecimal threshold = BigDecimal.ONE.movePointLeft(mc.getPrecision() + 5);
        BigDecimal sum = BigDecimal.ONE;
        BigDecimal term = BigDecimal.ONE;
        for (long i = 1; i < 500L; i++) {
            term = term.multiply(z, mc).divide(BigDecimal.valueOf(i), mc);
            sum = sum.add(term, mc);
            if (term.abs().compareTo(threshold) < 0) break;
        }
        return sum;
    }

    @Differentiable
    public UnaryFunction<RealType, RealType> derivative() {
        final RealType two = new RealImpl(TWO, MathContext.DECIMAL128);
        final Pi pi = Pi.getInstance(MathContext.DECIMAL128);

        Const<RealType, RealType> coeff = Const.getInstance((RealType) two.divide(pi.sqrt()));
        Pow<RealType, RealType> xsq = new Pow<>(2L, RealType.class);
        Exp exp = new Exp(xsq.andThen(Negate.getInstance(RealType.class)));
        return new Product<>(getArgumentName(), coeff, exp);
    }

    @Override
    public Range<RealType> inputRange(String argName) {
        return RangeUtils.ALL_REALS;
    }

    @Override
    public Class<RealType> getArgumentType() {
        return RealType.class;
    }
}
