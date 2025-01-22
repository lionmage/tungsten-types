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

import tungsten.types.Range;
import tungsten.types.annotations.Differentiable;
import tungsten.types.functions.ArgVector;
import tungsten.types.functions.UnaryFunction;
import tungsten.types.numerics.RealType;
import tungsten.types.numerics.impl.Pi;
import tungsten.types.numerics.impl.RealImpl;
import tungsten.types.numerics.impl.Zero;
import tungsten.types.util.MathUtils;
import tungsten.types.util.RangeUtils;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.Objects;

/**
 * The sinc function, &fnof;(x) = sin(x)/x<br>
 * This implementation is differentiable and continuous
 * across all real values.
 * @see <a href="https://en.wikipedia.org/wiki/Sinc_function">the Wikipedia article on sinc(x)</a>
 * @author Robert Poole, <a href="mailto:Tarquin.AZ@gmail.com">Gmail</a>
 * @since 0.4
 */
public class Sinc extends UnaryFunction<RealType, RealType> {
    private final MathContext mctx;

    protected Sinc(String varName, MathContext ctx) {
        super(varName, RealType.class);
        this.mctx = ctx;
    }

    /**
     * Obtain an instance of the normalized sinc function,
     * &fnof;(x) = sin(&pi;x)/&pi;x
     *
     * @return the normalized sinc function
     */
    public UnaryFunction<RealType, RealType> obtainNormalized() {
        UnaryFunction<RealType, RealType> scaleByPi =
                new Product<>(getArgumentName(),
                        Const.getInstance(Pi.getInstance(mctx)),
                        new Reflexive<>(getArgumentName(), RangeUtils.ALL_REALS, RealType.class));
        // I really don't like this cast, but the alternative is to declare that this
        // method returns a UnaryFunction<? super RealType, RealType>, which implies that any
        // Numeric could satisfy the conditions of the input. Anything that isn't a RealType
        // would be a problem, so tell the world that our normalized function only takes a real.
        // It's also worth noting that the non-normalized sinc function expects a real argument,
        // so this is consistent.
        return (UnaryFunction<RealType, RealType>) this.composeWith(scaleByPi);
    }

    @Differentiable
    public UnaryFunction<RealType, RealType> diff() {
        return new Quotient<>(Sinc.this.getArgumentName(),
                Sum.of(new Cos(Sinc.this.getArgumentName(), mctx),
                        new Sinc(Sinc.this.getArgumentName(), mctx).andThen(Negate.getInstance(RealType.class))),
                new Reflexive<>(Sinc.this.getArgumentName(), RangeUtils.ALL_REALS, RealType.class)) {
            @Override
            public RealType apply(ArgVector<RealType> arguments) {
                // the derivative is discontinuous at 0, so add a special case here
                if (Zero.isZero(arguments.forVariableName(getArgumentName()))) {
                    return new RealImpl(BigDecimal.ZERO, mctx);
                }
                return super.apply(arguments);
            }
        };
    }

    @Override
    public RealType apply(ArgVector<RealType> arguments) {
        RealType arg = arguments.forVariableName(getArgumentName());
        if (Zero.isZero(arg)) return new RealImpl(BigDecimal.ONE, mctx);
        return (RealType) MathUtils.sin(arg).divide(arg);
    }

    @Override
    public Range<RealType> inputRange(String argName) {
        if (getArgumentName().equals(argName)) return RangeUtils.ALL_REALS;
        return null;
    }

    @Override
    public Class<RealType> getArgumentType() {
        return RealType.class;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Sinc sinc = (Sinc) o;
        return Objects.equals(mctx, sinc.mctx) && Objects.equals(getArgumentName(), sinc.getArgumentName());
    }

    @Override
    public int hashCode() {
        return Objects.hash(mctx, getArgumentName());
    }

    @Override
    public String toString() {
        String arg = this.getComposedFunction().isPresent() ? getComposedFunction().get().toString() : getArgumentName();
        return "sinc(" + arg + ")";
    }
}
