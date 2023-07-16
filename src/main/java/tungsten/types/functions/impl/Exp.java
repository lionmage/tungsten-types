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
import tungsten.annotations.Differentiable;
import tungsten.types.functions.ArgVector;
import tungsten.types.functions.UnaryFunction;
import tungsten.types.numerics.RealType;
import tungsten.types.numerics.impl.Euler;
import tungsten.types.util.RangeUtils;

import java.lang.reflect.ParameterizedType;

/**
 * A basic implementation of the exponential function &#x212F;<sup>x</sup> for
 * real-valued arguments.  This implementation supports composing with another
 * function at construction-time, saving some effort.
 */
public class Exp extends UnaryFunction<RealType, RealType> {
    public Exp() {
        super("x");
    }

    public Exp(String varName) {
        super(varName);
    }

    /**
     * A convenience constructor to build a composed function
     * exp(&fnof;(x)).
     *
     * @param inner the inner function &fnof;(x) for composition
     */
    public Exp(UnaryFunction<? super RealType, RealType> inner) {
        super(inner.expectedArguments()[0]);
        composedFunction = inner;
    }

    @Override
    public RealType apply(ArgVector<RealType> arguments) {
        if (!checkArguments(arguments)) {
            throw new IllegalArgumentException("Expected argument " + getArgumentName() +
                    " is not present in the input vector " + arguments);
        }
        final RealType arg = arguments.elementAt(0L);
        RealType intermediate = getComposedFunction().isEmpty() ? arg : getComposedFunction().get().apply(arg);
        final Euler e = Euler.getInstance(arg.getMathContext());
        return e.exp(intermediate);
    }

    @Override
    public UnaryFunction<? super RealType, RealType> composeWith(UnaryFunction<? super RealType, RealType> before) {
        if (before instanceof NaturalLog) {
            final String beforeArgName = before.expectedArguments()[0];
            return new Reflexive<>(beforeArgName, before.inputRange(beforeArgName), RealType.class);
        }
        return super.composeWith(before);
    }

    @Override
    public <R2 extends RealType> UnaryFunction<RealType, R2> andThen(UnaryFunction<RealType, R2> after) {
        if (after instanceof NaturalLog) {
            Class<R2> rtnClass = (Class<R2>) ((Class) ((ParameterizedType) after.getClass()
                    .getGenericSuperclass()).getActualTypeArguments()[1]);
            if (rtnClass == null) rtnClass = (Class<R2>) RealType.class;
            return new Reflexive<>(getArgumentName(), RangeUtils.ALL_REALS, RealType.class).forReturnType(rtnClass);
        }
        return super.andThen(after);
    }

    @Override
    public Range<RealType> inputRange(String argName) {
        if (getComposedFunction().isPresent()) return  getComposedFunction().get().inputRange(argName);
        return RangeUtils.ALL_REALS;
    }

    @Differentiable
    public UnaryFunction<RealType, RealType> diff(SimpleDerivative<RealType> diffEngine) {
        if (getComposingFunction().isPresent()) {
            UnaryFunction<RealType, RealType> outer = (UnaryFunction<RealType, RealType>) getComposingFunction().get();
            return diffEngine.chainRuleStrategy(outer, new Exp(getArgumentName()));
        }
        if (getComposedFunction().isEmpty()) return this;
        final UnaryFunction<RealType, RealType> inner = (UnaryFunction<RealType, RealType>) getComposedFunction().get();
        // Note that if we got here, "this" refers to a composed function of Exp and inner, exactly what we want.
        return new Product<>(diffEngine.apply(inner), this);
    }

    @Override
    public String toString() {
        if (getComposedFunction().isEmpty()) return "\u212Fˣ";
        return "exp(\u2009" + getComposedFunction().get() + "\u2009)";
    }
}
