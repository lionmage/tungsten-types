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
import tungsten.annotations.Differentiable;
import tungsten.types.exceptions.CoercionException;
import tungsten.types.functions.ArgVector;
import tungsten.types.functions.NumericFunction;
import tungsten.types.functions.UnaryFunction;
import tungsten.types.functions.support.Rewritable;
import tungsten.types.numerics.RealType;
import tungsten.types.numerics.impl.One;
import tungsten.types.numerics.impl.RealImpl;
import tungsten.types.util.ClassTools;
import tungsten.types.util.OptionalOperations;
import tungsten.types.util.RangeUtils;

import java.math.MathContext;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A representation of the function &fnof;(x)&nbsp;=&nbsp;&minus;x
 * <br/>Not terribly useful by itself, but it is very handy for composition
 * of differentiable functions.
 *
 * @param <T> the type of the function's sole input parameter
 * @param <R> the type of the function's output
 */
public class Negate<T extends Numeric, R extends Numeric> extends UnaryFunction<T, R> implements Rewritable {
    private Class<R> rtnClazz;

    private Negate() {
        super("x");
    }

    public static <T extends Numeric, R extends Numeric> Negate<T, R> getInstance(Class<R> clazz) {
        Negate<T, R> instance = new Negate<>();
        instance.rtnClazz = clazz;
        return instance;
    }

    @Override
    public R apply(ArgVector<T> arguments) {
        try {
            return (R) arguments.elementAt(0L).negate().coerceTo(rtnClazz);
        } catch (CoercionException e) {
            throw new ArithmeticException("Could not coerce result of negation.");
        }
    }

    private static final RealType NEGONE_CMP = new RealImpl("-1");

    public static boolean isNegateEquivalent(UnaryFunction<?, ?> fn) {
        if (fn instanceof Negate) return true;
        if (fn instanceof Product) {
            // for a Product to qualify, it has to have at least 1 non-constant term,
            // and the product of the constant terms must be -1
            Product<?, ?> prod = (Product<?, ?>) fn;
            if (prod.termCount() < 2L || prod.stream().allMatch(Const.class::isInstance)) return false;
            Numeric coeffProd =  prod.stream().filter(Const.class::isInstance).map(Const.class::cast)
                    .map(Const::inspect)
                    .reduce(One.getInstance(MathContext.UNLIMITED), Numeric::multiply);
            try {
                RealType realProd = (RealType) coeffProd.coerceTo(RealType.class);
                return realProd.compareTo(NEGONE_CMP) == 0;
            } catch (CoercionException e) {
                Logger.getLogger(Negate.class.getName()).log(Level.SEVERE,
                        "Product of all constant terms is {0}, but could not be coerced to RealType for comparison.",
                        coeffProd);
                throw new IllegalStateException(e);
            }
        }
        if (fn instanceof Quotient) {
            Quotient<?, ?> quotient = (Quotient<?, ?>) fn;
            UnaryFunction<?, ?> num = quotient.getNumerator();
            UnaryFunction<?, ?> denom = quotient.getDenominator();
            if (isNegateEquivalent(num) && isNegateEquivalent(denom)) {
                // if both the numerator and the denominator have a coefficient of -1, the quotient is positive
                return false;
            }
            return isNegateEquivalent(num) || isNegateEquivalent(denom);
        }

        return false;
    }


    @Override
    public UnaryFunction<? super T, R> composeWith(UnaryFunction<? super T, T> before) {
        if (before instanceof Negate) {
            final String beforeArgName = before.expectedArguments()[0];
            return new Reflexive<>(beforeArgName, before.inputRange(beforeArgName), Numeric.class).forReturnType(rtnClazz);
        }
        return super.composeWith(before);
    }


    @Override
    public <R2 extends R> UnaryFunction<T, R2> andThen(UnaryFunction<R, R2> after) {
        if (after instanceof Negate) {
            List<Class<?>> afterArgClasses = ClassTools.getTypeArguments(NumericFunction.class, after.getClass());
            List<Class<?>> argClasses = ClassTools.getTypeArguments(NumericFunction.class, this.getClass());
            Class<T> inputClass = argClasses.get(0) == null ? (Class<T>) Numeric.class : (Class<T>) argClasses.get(0);
            Class<R2> clazz = afterArgClasses.get(1) == null ? (Class<R2>) rtnClazz : (Class<R2>) afterArgClasses.get(1);
            return new Reflexive<>(getArgumentName(), inputRange(getArgumentName()), inputClass).forReturnType(clazz);
        }
        return super.andThen(after);
    }

    @Differentiable
    public UnaryFunction<T, R> diff() {
        final R response = OptionalOperations.dynamicInstantiate(rtnClazz, "-1");

        return Const.getInstance(response);
    }

    @Override
    public Negate<T, R> forArgName(String argName) {
        if (getArgumentName().equals(argName)) return this;
        final Class<R> returnType = this.rtnClazz;
        return new Negate<>() {
            {
                rtnClazz = returnType;
            }

            @Override
            protected String getArgumentName() {
                return argName;
            }

            @Override
            public String[] expectedArguments() {
                return new String[] {argName};
            }
        };
    }

    @Override
    public Range<RealType> inputRange(String argName) {
        return RangeUtils.ALL_REALS;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(rtnClazz) * 31;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof Negate) {
            Negate that = (Negate) obj;
            return this.rtnClazz.isAssignableFrom(that.rtnClazz);
        }
        return false;
    }
}
