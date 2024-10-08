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

package tungsten.types.numerics.impl;

import tungsten.types.Numeric;
import tungsten.types.Set;
import tungsten.types.exceptions.CoercionException;
import tungsten.types.numerics.*;
import tungsten.types.util.MathUtils;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A real-valued mega constant.
 * @since 0.4
 */
public class RealMegaConstant extends MegaConstant<RealType> implements RealType {
    public RealMegaConstant(RationalType coefficient) {
        super(RealType.class, coefficient);
    }

    public RealMegaConstant(RationalType coefficient, Numeric[] constants, Long[] exponents) {
        super(RealType.class, coefficient, constants, exponents);
    }

    public RealMegaConstant(RationalType coefficient, Numeric... constants) {
        super(RealType.class, coefficient, constants);
    }

    /**
     * Convenience constructor that takes an integer value instead of
     * a rational value as the leading coefficient.
     * @param coefficient any integer value
     * @param constants   zero or more constants with assumed exponents of one
     */
    public RealMegaConstant(IntegerType coefficient, Numeric... constants) {
        this(new RationalImpl(coefficient.asBigInteger(), BigInteger.ONE, MathUtils.inferMathContext(Arrays.asList(constants))),
                constants);
    }

    @Override
    protected RealMegaConstant doCombine(MegaConstant<RealType> other) throws CoercionException {
        RationalType combinedCoeff = (RationalType) rationalCoefficient.multiply(other.leadingCoefficient())
                .coerceTo(RationalType.class);
        List<Numeric> values = new ArrayList<>(constants);
        List<Long> powers = new ArrayList<>(exponents);
        // obtain the corresponding values from other and incorporate into a new RealMegaConstant
        for (ConstantTuple tuple : other.innerView()) {
            if (values.contains(tuple.getConstantValue())) {
                int index = values.indexOf(tuple.getConstantValue());
                // Note: if any exponents are reduced to 0, the constructor
                // should filter those out
                powers.set(index, powers.get(index) + tuple.getExponent());
            } else {
                values.add(tuple.getConstantValue());
                powers.add(tuple.getExponent());
            }
        }
        return new RealMegaConstant(combinedCoeff,
                values.toArray(Numeric[]::new),
                powers.toArray(Long[]::new));
    }

    @Override
    public boolean isIrrational() {
        return anyIrrational();
    }

    @Override
    public RealType magnitude() {
        return getValue().magnitude();
    }

    @Override
    public boolean isExact() {
        boolean exactness = constants.stream().allMatch(Numeric::isExact);
        return rationalCoefficient.isExact() && exactness;
    }

    @Override
    public boolean isCoercibleTo(Class<? extends Numeric> numtype) {
        return RealType.class.isAssignableFrom(numtype) ||
                ComplexType.class.isAssignableFrom(numtype);
    }

    @Override
    public Numeric coerceTo(Class<? extends Numeric> numtype) throws CoercionException {
        if (RealType.class.isAssignableFrom(numtype)) return this;
        return getValue().coerceTo(numtype);
    }

    @Override
    public RealType negate() {
        return new RealMegaConstant(rationalCoefficient.negate(),
                constants.toArray(Numeric[]::new),
                exponents.toArray(Long[]::new));
    }

    @Override
    public Numeric add(Numeric addend) {
        return getValue().add(addend);
    }

    @Override
    public Numeric subtract(Numeric subtrahend) {
        return getValue().subtract(subtrahend);
    }

    @Override
    public Numeric multiply(Numeric multiplier) {
        if (multiplier instanceof RealMegaConstant) {
            return (Numeric) this.combine((RealMegaConstant) multiplier);
        }
        if (multiplier.isCoercibleTo(RationalType.class)) {
            try {
                RationalType prodCoeff = (RationalType) rationalCoefficient.multiply(multiplier)
                        .coerceTo(RationalType.class);
                return new RealMegaConstant(prodCoeff,
                        constants.toArray(Numeric[]::new),
                        exponents.toArray(Long[]::new));
            } catch (CoercionException e) {
                throw new IllegalStateException("Multiplying rationals should yield a rational result", e);
            }
        }
        return getValue().multiply(multiplier);
    }

    @Override
    public Numeric divide(Numeric divisor) {
        if (divisor.isCoercibleTo(RationalType.class) || divisor instanceof RealMegaConstant) {
            return this.multiply(divisor.inverse());
        }
        return getValue().divide(divisor);
    }

    @Override
    public Numeric inverse() {
        try {
            RationalType invCoeff = (RationalType) rationalCoefficient.inverse()
                    .coerceTo(RationalType.class);
            return new RealMegaConstant(invCoeff,
                    constants.toArray(Numeric[]::new),
                    exponents.stream().map(x -> -x).toArray(Long[]::new));
        } catch (CoercionException e) {
            Logger.getLogger(RealMegaConstant.class.getName()).log(Level.WARNING,
                    "Computing inverse of {0} failed; falling back to inverse of aggregate value.",
                    rationalCoefficient);
            return getValue().inverse();
        }
    }

    @Override
    public Numeric sqrt() {
        return getValue().sqrt();
    }

    @Override
    public MathContext getMathContext() {
        List<Numeric> values = new ArrayList<>(constants);
        values.add(rationalCoefficient);
        return MathUtils.inferMathContext(values);
    }

    @Override
    public BigDecimal asBigDecimal() {
        return getValue().asBigDecimal();
    }

    @Override
    public Sign sign() {
        return getValue().sign();
    }

    @Override
    public IntegerType floor() {
        return getValue().floor();
    }

    @Override
    public IntegerType ceil() {
        return getValue().ceil();
    }

    @Override
    public Set<ComplexType> nthRoots(IntegerType n) {
        return getValue().nthRoots(n);
    }

    @Override
    public int compareTo(RealType o) {
        return getValue().compareTo(o);
    }
}
