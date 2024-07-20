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
import tungsten.types.functions.support.Rewritable;
import tungsten.types.numerics.RealType;
import tungsten.types.numerics.impl.ExactZero;
import tungsten.types.numerics.impl.One;
import tungsten.types.numerics.impl.Zero;
import tungsten.types.util.RangeUtils;

import java.math.MathContext;
import java.util.Objects;

/**
 * A function that represents a constant.
 *
 * @param <T> the input parameter type, mostly ignored
 * @param <R> the output type
 */
public class Const<T extends Numeric, R extends Numeric> extends UnaryFunction<T, R> implements Rewritable {
    final R value;

    private Const(R init) {
        super("x");
        value = init;
    }

    public static <T extends Numeric, R extends Numeric> Const<T, R> getInstance(R init) {
        return new Const<>(init) {};  // anonymous subclass to aid in reification of type parameters
    }

    @Override
    public R apply(ArgVector<T> arguments) {
        return value;
    }

    public static boolean isConstEquivalent(UnaryFunction<?, ?> fn) {
        if (fn instanceof Const) return true;
        if (fn instanceof Product) {
            Product<?, ?> prod = (Product<?, ?>) fn;
            return prod.stream().allMatch(Const::isConstEquivalent);
        }
        if (fn instanceof Sum) {
            Sum<?, ?> sum = (Sum<?, ?>) fn;
            return sum.stream().allMatch(Const::isConstEquivalent);
        }
        if (fn instanceof Quotient) {
            Quotient<?, ?> q = (Quotient<?, ?>) fn;
            return isConstEquivalent(q.getNumerator()) && isConstEquivalent(q.getDenominator());
        }

        return false;
    }

    public static Const<? super RealType, RealType> getConstEquivalent(UnaryFunction<?, ?> fn) {
        if (!isConstEquivalent(fn)) throw new IllegalArgumentException("Argument is not constant-equivalent");
        try {
            if (fn instanceof Const) {
                RealType realVal = (RealType) ((Const<?, ?>) fn).inspect().coerceTo(RealType.class);
                return new Const<>(realVal);
            }
            if (fn instanceof Product) {
                Product<?, ?> prod = (Product<?, ?>) fn;
                Numeric val = prod.stream().map(Const::getConstEquivalent).map(Const::inspect)
                        .map(Numeric.class::cast)
                        .reduce(One.getInstance(MathContext.UNLIMITED), Numeric::multiply);
                return new Const<>((RealType) val.coerceTo(RealType.class));
            }
            if (fn instanceof Sum) {
                Sum<?, ?> sum = (Sum<?, ?>) fn;
                Numeric val = sum.stream().map(Const::getConstEquivalent).map(Const::inspect)
                        .map(Numeric.class::cast)
                        .reduce(ExactZero.getInstance(MathContext.UNLIMITED), Numeric::add);
                return new Const<>((RealType) val.coerceTo(RealType.class));
            }
            if (fn instanceof Quotient) {
                Quotient<?, ?> quotient = (Quotient<?, ?>) fn;
                Const<? super RealType, RealType> num = getConstEquivalent(quotient.getNumerator());
                Const<? super RealType, RealType> denom = getConstEquivalent(quotient.getDenominator());
                return new Const<>((RealType) num.inspect().divide(denom.inspect()));
            }
        } catch (CoercionException e) {
            throw new IllegalStateException(e);
        }
        throw new UnsupportedOperationException("No strategy found to convert " + fn + " into a Const");
    }

    @Override
    public Range<RealType> inputRange(String argName) {
        return RangeUtils.ALL_REALS;
    }

    public R inspect() {
        return value;
    }

    @Differentiable
    public UnaryFunction<T, R> diff() {
        if (Zero.isZero(value)) {
            return this;
        } else {
            final Class<R> resultClass = (Class<R>) value.getClass();
            try {
                return new Const<>((R) ExactZero.getInstance(value.getMathContext()).coerceTo(resultClass));
            } catch (CoercionException e) {
                throw new IllegalStateException("Unable to coerce zero to result type", e);
            }
        }
    }

    @Override
    public UnaryFunction<? super T, R> composeWith(UnaryFunction<? super T, T> before) {
        // a constant function will always return the same value, and composition will not change this behavior
        return this;
    }

    @Override
    public String toString() {
        return value.toString();
    }

    @Override
    public int hashCode() {
        return 9 * Objects.hashCode(value);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof Const) {
            return value.equals(((Const<?, ?>) obj).inspect());
        }
        return false;
    }

    @Override
    public Const<T, R> forArgName(String argName) {
        if (getArgumentName().equals(argName)) return this;
        return new Const<>(value) {
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
}
