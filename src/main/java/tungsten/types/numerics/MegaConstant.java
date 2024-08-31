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

package tungsten.types.numerics;

import tungsten.types.Numeric;
import tungsten.types.annotations.Constant;
import tungsten.types.exceptions.CoercionException;
import tungsten.types.numerics.impl.IntegerImpl;
import tungsten.types.util.MathUtils;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static tungsten.types.util.UnicodeTextEffects.numericSuperscript;

public abstract class MegaConstant<T extends Numeric> {
    protected static final char TIMES = '\u2062'; // invisible times

    protected transient T value;
    protected Class<T> masqueradesAs;
    protected RationalType rationalCoefficient;
    protected final List<Numeric> constants = new ArrayList<>();
    protected final List<Long> exponents = new ArrayList<>();

    public MegaConstant(Class<T> evaluatesAs, RationalType coefficient) {
        this.masqueradesAs = evaluatesAs;
        this.rationalCoefficient = coefficient;
    }

    public MegaConstant(Class<T> evaluatesAs, RationalType coefficient, Numeric[] constants, Long[] exponents) {
        this(evaluatesAs, coefficient);
        if (constants.length != exponents.length) throw new IllegalArgumentException("Arrays of constants and exponents must be of equal length");
        for (int i = 0; i < constants.length; i++) {
            append(constants[i], exponents[i]);
        }
    }

    /**
     * Varargs constructor that assumes all component constants
     * have an exponent of unity.
     * @param evaluateAs  the type we wish to evaluate as
     * @param coefficient the rational coefficient of this aggregate constant
     * @param constants   zero or more Numeric values
     */
    public MegaConstant(Class<T> evaluateAs, RationalType coefficient, Numeric... constants) {
        this(evaluateAs, coefficient);
        for (Numeric constant : constants) {
            append(constant, 1L);
        }
    }

    protected boolean anyIrrational() {
        return constants.stream().filter(RealType.class::isInstance)
                .map(RealType.class::cast)
                .anyMatch(RealType::isIrrational);
    }

    protected void append(Numeric constant, long exponent) {
        if (exponent == 0L) return;  // zero exponent gives us unity
        if (constant.getClass().isAnnotationPresent(Constant.class)) {
            if (constants.contains(constant)) {
                // constant already exists, so add exponents
                int index = constants.indexOf(constant);
                exponents.set(index, exponents.get(index) + exponent);
                if (exponents.get(index) == 0L) {
                    // 0 exponent means these values canceled out
                    constants.remove(index);
                    exponents.remove(index);
                }
            } else {
                constants.add(constant);
                exponents.add(exponent);
            }
        } else if (constant.isCoercibleTo(RationalType.class)) {
            try {
                RationalType rationalConst = (RationalType) constant.coerceTo(RationalType.class);
                if (exponent != 1L) {
                    IntegerType innerExponent = new IntegerImpl(BigInteger.valueOf(exponent));
                    // when given an IntegerType argument, RationalImpl.power() returns a RationalType
                    // I'd rather not use power() here since it's intended for Groovy applications,
                    // but I'd also rather not rewrite this code elsewhere...
                    rationalConst = (RationalType) rationalConst.power(innerExponent);
                }
                rationalCoefficient = (RationalType) rationalCoefficient.multiply(rationalConst).coerceTo(RationalType.class);
            } catch (CoercionException e) {
                throw new IllegalStateException("Unable to coerce coefficient to rational", e);
            }
        } else {
            throw new IllegalArgumentException(constant + " is not a valid constant");
        }
    }

    protected void append(ConstantTuple tuple) {
        append(tuple.getConstantValue(), tuple.getExponent());
    }

    protected T calculate() {
        Numeric product = rationalCoefficient;
        for (int i = 0; i < constants.size(); i++) {
            IntegerType exponent = new IntegerImpl(BigInteger.valueOf(exponents.get(i)));
            product = product.multiply(MathUtils.computeIntegerExponent(constants.get(i), exponent));
        }
        try {
            value = (T) product.coerceTo(masqueradesAs);
        } catch (CoercionException e) {
            throw new IllegalStateException("Unable to coerce value to return type", e);
        }
        return value;
    }

    public T getValue() {
        return value != null ? value : calculate();
    }

    public MegaConstant<T> combine(MegaConstant<T> other) {
        if (other.masqueradesAs != this.masqueradesAs) throw new IllegalArgumentException("Cannot combine dissimilar types");
        try {
            return doCombine(other);
        } catch (CoercionException e) {
            throw new IllegalStateException("While combining " + this + " and " + other, e);
        }
    }

    protected abstract MegaConstant<T> doCombine(MegaConstant<T> other) throws CoercionException;

    public RationalType leadingCoefficient() {
        return rationalCoefficient;
    }

    public List<ConstantTuple> innerView() {
        if (constants.size() != exponents.size()) {
            throw new IllegalStateException("Mismatch of constants/exponents sizes");
        }
        List<ConstantTuple> result = new ArrayList<>(constants.size());
        for (int idx = 0; idx < constants.size(); idx++) {
            result.add(new ConstantTuple(constants.get(idx), exponents.get(idx)));
        }
        return result;
    }

    public String toString() {
        List<Long> numExponents = exponents.stream().filter(x -> x > 0L).collect(Collectors.toList());
        List<Long> denomExponents = exponents.stream().filter(x -> x < 0L)
                .map(x -> -x).collect(Collectors.toList());
        StringBuilder buf = new StringBuilder();
        // numerator
        if (!rationalCoefficient.numerator().asBigInteger().equals(BigInteger.ONE)) {
            buf.append(rationalCoefficient.numerator());
            if (!numExponents.isEmpty()) buf.append(TIMES);
        }
        List<Numeric> numConstants = IntStream.range(0, constants.size())
                .filter(k -> exponents.get(k) > 0L)
                .mapToObj(constants::get)
                .collect(Collectors.toList());
        buildConstRepresentation(buf, numConstants, numExponents);

        // denominator
        final boolean denomNotUnity = !rationalCoefficient.denominator().asBigInteger().equals(BigInteger.ONE);
        if (denomNotUnity && !denomExponents.isEmpty()) {
            buf.append('\u2215'); // Division Slash
        }
        if (denomNotUnity) {
            buf.append(rationalCoefficient.denominator());
            if (!denomExponents.isEmpty()) buf.append(TIMES);
        }
        List<Numeric> denomConstants = IntStream.range(0, constants.size())
                .filter(k -> exponents.get(k) < 0L)
                .mapToObj(constants::get)
                .collect(Collectors.toList());
        buildConstRepresentation(buf, denomConstants, denomExponents);

        return buf.toString();
    }

    private void buildConstRepresentation(StringBuilder buf, List<Numeric> constants, List<Long> exponents) {
        if (constants.size() != exponents.size()) throw new IllegalStateException("Mismatch between constants and exponents");
        for (int k = 0; k < constants.size(); k++) {
            Constant constant = constants.get(k).getClass().getAnnotation(Constant.class);
            if (constant == null) throw new IllegalStateException(constants.get(k) + " is not a constant");
            buf.append(constant.representation());
            int n = exponents.get(k).intValue();
            if (n > 1) buf.append(numericSuperscript(n));
            if (k < constants.size() - 1) buf.append(TIMES);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MegaConstant)) return false;
        MegaConstant<?> that = (MegaConstant<?>) o;
        // value is computed, so don't include it in the comparison
        return Objects.equals(masqueradesAs, that.masqueradesAs) &&
                Objects.equals(rationalCoefficient, that.rationalCoefficient) &&
                Objects.equals(constants, that.constants) &&
                Objects.equals(exponents, that.exponents);
    }

    @Override
    public int hashCode() {
        return Objects.hash(masqueradesAs, rationalCoefficient, constants, exponents);
    }

    public static class ConstantTuple {
        private final Numeric constantValue;
        private long exponent;

        public ConstantTuple(Numeric val, long exponent) {
            this.constantValue = val;
            this.exponent = exponent;
        }

        public Numeric getConstantValue() {
            return constantValue;
        }

        public long getExponent() {
            return exponent;
        }

        public void setExponent(long exponent) {
            this.exponent = exponent;
        }
    }
}
