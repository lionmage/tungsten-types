package tungsten.types.functions.impl;
/*
 * The MIT License
 *
 * Copyright © 2022 Robert Poole <Tarquin.AZ@gmail.com>.
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
 */

import tungsten.types.Range;
import tungsten.types.annotations.Differentiable;
import tungsten.types.functions.ArgVector;
import tungsten.types.functions.UnaryFunction;
import tungsten.types.numerics.RealType;
import tungsten.types.numerics.impl.Euler;
import tungsten.types.numerics.impl.One;
import tungsten.types.numerics.impl.RealImpl;
import tungsten.types.util.MathUtils;
import tungsten.types.util.RangeUtils;
import tungsten.types.util.UnicodeTextEffects;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.List;
import java.util.Objects;

/**
 * Implementation of the sigmoid function &#x1D70E;(x).
 *
 * @see <a href="http://matlab.cheme.cmu.edu/2011/10/30/smooth-transitions-between-discontinuous-functions/">&ldquo;Smooth transitions
 *   between discontinuous functions&rdquo; by John Kitchin</a>
 * @author Robert Poole, <a href="mailto:Tarquin.AZ@gmail.com">Gmail</a>
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
        super(varName, RealType.class);
        this.x0 = x0;
        this.alpha = alpha.magnitude();  // this parameter should always be positive
    }

    public Sigmoid(RealType x0, RealType alpha) {
        this("x", x0, alpha);
    }

    @Override
    public RealType apply(ArgVector<RealType> arguments) {
        RealType arg = arguments.hasVariableName(getArgumentName()) ?
                arguments.forVariableName(getArgumentName()) : arguments.elementAt(0L);
        final MathContext ctx = arguments.getMathContext() != null ? arguments.getMathContext() : arg.getMathContext();
        final Euler e = Euler.getInstance(ctx);
        RealType exponent = (RealType) arg.subtract(x0).divide(alpha).negate();
        return (RealType) One.getInstance(ctx).add(e.exp(exponent)).inverse();
    }

    @Override
    public Range<RealType> inputRange(String argName) {
        return RangeUtils.ALL_REALS;
    }

    @Override
    public Class<RealType> getArgumentType() {
        return RealType.class;
    }

    /**
     * Obtain the centroid for the transition from 0 to 1,
     * typically denoted x<sub>0</sub>.
     * @return the centroid of this function's output
     */
    public RealType getCentroid() {
        return x0;
    }

    @Differentiable
    public UnaryFunction<RealType, RealType> diff() {
        final MathContext ctx = MathUtils.inferMathContext(List.of(x0, alpha));

        RealType one = new RealImpl(BigDecimal.ONE, ctx);
        Const<RealType, RealType> c1 = Const.getInstance(one);
        Sigmoid s1 = new Sigmoid(getArgumentName(), x0, alpha);
        Negate<RealType, RealType> negate = Negate.getInstance(RealType.class);
        Sum<RealType, RealType> difference = new Sum<>(c1, s1.andThen(negate));
        return new Product<>(getArgumentName(), s1, difference);
    }

    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder().append("\uD835\uDF0E");  // surrogate pair for U+1D70E, 𝜎
        buf.append('(').append(getArgumentName()).append(')');
        buf.append(", x").append(UnicodeTextEffects.numericSubscript(0)).append('=').append(x0);
        buf.append(", \uD835\uDEFC=").append(alpha);  // surrogate pair for U+1D6FC, 𝛼
        return buf.toString();
    }

    @Override
    public int hashCode() {
        return Objects.hash(getArgumentName(), x0, alpha);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof Sigmoid other) {
            return Objects.equals(getArgumentName(), other.getArgumentName()) &&
                    x0.equals(other.getCentroid()) &&
                    alpha.equals(other.alpha);
        }
        return false;
    }
}
