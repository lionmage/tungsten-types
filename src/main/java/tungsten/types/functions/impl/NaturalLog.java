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

import tungsten.types.Numeric;
import tungsten.types.Range;
import tungsten.types.annotations.Differentiable;
import tungsten.types.exceptions.CoercionException;
import tungsten.types.functions.ArgVector;
import tungsten.types.functions.UnaryFunction;
import tungsten.types.numerics.IntegerType;
import tungsten.types.numerics.RationalType;
import tungsten.types.numerics.RealType;
import tungsten.types.numerics.Sign;
import tungsten.types.numerics.impl.IntegerImpl;
import tungsten.types.numerics.impl.RealImpl;
import tungsten.types.numerics.impl.RealInfinity;
import tungsten.types.util.MathUtils;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;

/**
 * A basic implementation of the natural logarithm function ln(x) for positive
 * real-valued arguments.  This implementation supports composing with another
 * function at construction-time, saving some effort.
 * @author Robert Poole, <a href="mailto:Tarquin.AZ@gmail.com">Gmail</a>
 */
public class NaturalLog extends UnaryFunction<RealType, RealType> {
    /**
     * Default no-arg constructor.  The resulting
     * function uses &ldquo;x&rdquo; as its argument.
     */
    public NaturalLog() {
        super("x", RealType.class);
    }

    /**
     * Construct the natural log with the given argument name.
     * @param argName the argument name
     */
    public NaturalLog(String argName) {
        super(argName, RealType.class);
    }

    /**
     * A convenience constructor to build a composed function
     * ln(&fnof;(x)).
     *
     * @param inner the inner function &fnof;(x) for composition
     */
    public NaturalLog(UnaryFunction<? super RealType, RealType> inner) {
        super(inner.expectedArguments()[0], RealType.class);
        composedFunction = inner;
    }

    @Override
    public RealType apply(ArgVector<RealType> arguments) {
        if (!checkArguments(arguments)) {
            throw new IllegalArgumentException("Expected argument "
                    + getArgumentName() + " is not present in input " + arguments + " or is out of range");
        }
        final RealType arg = arguments.hasVariableName(getArgumentName()) ?
                arguments.forVariableName(getArgumentName()) : arguments.elementAt(0L);
        RealType intermediate = getComposedFunction().isEmpty() ? arg : getComposedFunction().get().apply(arg);
        return MathUtils.ln(intermediate);
    }

    @Override
    public UnaryFunction<? super RealType, RealType> composeWith(UnaryFunction<? super RealType, RealType> before) {
        final String beforeArgName = before.expectedArguments()[0];
        if (before instanceof Exp) {
            return new Reflexive<>(beforeArgName, before.inputRange(beforeArgName), RealType.class);
        } else if (before instanceof Pow) {
            Numeric exponent = ((Pow<? super RealType, RealType>) before).getExponent();
            // log(x^y) = y*log(x)
            try {
                Const<RealType, RealType> myConst = Const.getInstance((RealType) exponent.coerceTo(RealType.class));
                return new Product<>(beforeArgName, myConst, this);
            } catch (CoercionException e) {
                throw new IllegalStateException("Exponent " + exponent + " is not coercible", e);
            }
        }
        return super.composeWith(before);
    }

    @Override
    public <R2 extends RealType> UnaryFunction<RealType, R2> andThen(UnaryFunction<RealType, R2> after) {
        if (after instanceof Exp) {
            Class<R2> rtnClass = after.getReturnType();
            if (rtnClass == null) rtnClass = (Class<R2>) RealType.class; // a reasonable default
            if (getComposedFunction().isPresent()) return (UnaryFunction<RealType, R2>) getComposedFunction().get().forReturnType(rtnClass);
            return new Reflexive<>(getArgumentName(), lnRange, RealType.class).forReturnType(rtnClass);
        }
        return super.andThen(after);
    }

    private static final Range<RealType> lnRange = new Range<>(new RealImpl(BigDecimal.ZERO),
            RealInfinity.getInstance(Sign.POSITIVE, MathContext.UNLIMITED),
            Range.BoundType.EXCLUSIVE);

    @Override
    public Range<RealType> inputRange(String argName) {
        return getComposedFunction().isPresent() ? getComposedFunction().get().inputRange(argName) : lnRange;
    }

    @Override
    public Class<RealType> getArgumentType() {
        return RealType.class;
    }

    @Override
    protected boolean checkArguments(ArgVector<RealType> arguments) {
        if (!super.checkArguments(arguments)) return false;
        final RealType arg = arguments.hasVariableName(getArgumentName()) ?
                arguments.forVariableName(getArgumentName()) : arguments.elementAt(0L);
        return lnRange.contains(arg);
    }

    @Differentiable
    public UnaryFunction<RealType, RealType> diff(SimpleDerivative<RealType> diffEngine) {
        BigInteger numerator = BigInteger.ONE;
        if (getComposedFunction().isPresent()) {
            if (getComposedFunction().get() instanceof Pow &&
                    getComposedFunction().get().getComposedFunction().isEmpty()) {
                Numeric exponent = ((Pow<?, ?>) getComposedFunction().get()).getExponent();
                if (exponent instanceof IntegerType) numerator = ((IntegerType) exponent).asBigInteger();
                else {
                    final RationalType scalar = (RationalType) exponent;
                    try {
                        return new Quotient<>(Const.getInstance((RealType) scalar.numerator().coerceTo(RealType.class)),
                                new Product<>(Const.getInstance((RealType) scalar.denominator().coerceTo(RealType.class)),
                                        new Reflexive<>(expectedArguments()[0], lnRange, RealType.class)));
                    } catch (CoercionException fatal) {
                        throw new IllegalStateException(fatal);
                    }
                }
            } else {
                // for any other composed function, use the chain rule
                UnaryFunction<RealType, RealType> inner = (UnaryFunction<RealType, RealType>) getComposedFunction().get();
                UnaryFunction<RealType, RealType> outerDiff = lnDiff(new IntegerImpl(BigInteger.ONE));
                UnaryFunction<RealType, RealType> innerdiff = diffEngine.apply(inner);
                return new Product<>((UnaryFunction<RealType, RealType>) outerDiff.composeWith(inner), innerdiff) {
                    @Override
                    public String toString() {
                        if (this.termCount() == 2L) {
                            // this Product has not been modified post-construction, so emit a representation of this derivative
                            StringBuilder buf = new StringBuilder();
                            buf.append('(').append("1\u2215").append(innerToString(false)).append(')');
                            buf.append('\u22C5'); // dot operator
                            buf.append('(').append(innerdiff).append(')');
                            return buf.toString();
                        }
                        return super.toString();
                    }
                };
            }
        }
        // The derivative of ln(x) is 1/x over the positive reals, ln(x^2) is 2/x, etc.
        final IntegerType scale = new IntegerImpl(numerator);
        return lnDiff(scale);
    }

    private UnaryFunction<RealType, RealType> lnDiff(IntegerType scale) {
        return new Pow<>(-1L, RealType.class) {
            @Override
            protected String getArgumentName() {
                return NaturalLog.this.getArgumentName();
            }

            @Override
            public String[] expectedArguments() {
                return new String[] { getArgumentName() };
            }

            @Override
            public RealType apply(ArgVector<RealType> arguments) {
                return (RealType) super.apply(arguments).multiply(scale);
            }

            @Override
            public Range<RealType> inputRange(String argName) {
                return lnRange;
            }

            @Override
            public String toString() {
                StringBuilder buf = new StringBuilder();
                buf.append(scale).append('\u2215').append(getArgumentName());  // U+2215 = division slash
                return buf.toString();
            }
        };
    }

    @Override
    public String toString() {
        return "ln" + innerToString(true);
    }

    private String innerToString(boolean alwaysUseParens) {
        final boolean useParens = alwaysUseParens || getComposedFunction().isPresent();
        StringBuilder buf = new StringBuilder();
        if (useParens) buf.append('(');
        else buf.append('\u2009');
        getComposedFunction().ifPresentOrElse(buf::append,
                () -> buf.append(getArgumentName()));
        if (useParens) buf.append(')');
        return buf.toString();
    }
}
