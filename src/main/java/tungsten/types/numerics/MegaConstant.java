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
import tungsten.types.annotations.Experimental;
import tungsten.types.exceptions.CoercionException;
import tungsten.types.numerics.impl.IntegerImpl;
import tungsten.types.numerics.impl.One;
import tungsten.types.numerics.impl.Zero;
import tungsten.types.util.MathUtils;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.locks.StampedLock;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static tungsten.types.util.UnicodeTextEffects.numericSuperscript;

/**
 * Abstract base class for a &ldquo;mega constant,&rdquo; a
 * constant value that is itself composed of fundamental
 * constants and has a rational coefficient.
 * @param <T> the type that this constant's value should present itself as
 * @since 0.4
 */
@Experimental
public abstract class MegaConstant<T extends Numeric> {
    protected static final char TIMES = '\u2062'; // invisible times
    protected static final char DIVISION_SLASH = '\u2215'; // Division Slash

    protected transient T value;
    private final StampedLock valueGuard = new StampedLock();
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

    /**
     * Determines if any component of this constant is an irrational value.
     * @return true if this constant contains an irrational value
     */
    protected boolean anyIrrational() {
        return constants.stream().filter(RealType.class::isInstance)
                .map(RealType.class::cast)
                .anyMatch(RealType::isIrrational);
    }

    /**
     * Append a constant value to this aggregate constant. Rational
     * values (or values coercible to rational) or any {@link Numeric} instance that has the
     * {@code @Constant} annotation is acceptable.
     * @param constant the constant value to append
     * @param exponent the power to which this constant must be raised
     */
    protected void append(Numeric constant, long exponent) {
        if (exponent == 0L) return;  // zero exponent gives us unity
        if (!constant.isCoercibleTo(masqueradesAs)) {
            throw new IllegalArgumentException(constant + " cannot masquerade as " + masqueradesAs.getName());
        }
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
        // reset the value cache
        long stamp = valueGuard.tryWriteLock();
        if (stamp != 0L) {
            value = null;
            valueGuard.unlockWrite(stamp);
        }
    }

    protected void append(ConstantTuple tuple) {
        append(tuple.getConstantValue(), tuple.getExponent());
    }

    /**
     * The main method to calculate the aggregate value of this mega constant.
     * It is protected so subclasses can modify or replace its behavior.
     * If a read lock has been acquired before calling this method, the caller
     * must supply the stamp value as the sole argument; otherwise, {@code 0L}
     * may be supplied.
     *
     * @param readStamp the stamp associated with a read lock, or zero if no read lock was acquired
     * @return the calculated value of this mega constant
     * @see StampedLock#tryConvertToWriteLock(long)
     */
    protected T calculate(long readStamp) {
        Numeric product = rationalCoefficient;
        for (int i = 0; i < constants.size(); i++) {
            IntegerType exponent = new IntegerImpl(BigInteger.valueOf(exponents.get(i)));
            product = product.multiply(MathUtils.computeIntegerExponent(constants.get(i), exponent));
        }
        long stamp = readStamp != 0L ? valueGuard.tryConvertToWriteLock(readStamp) : valueGuard.writeLock();
        try {
            if (stamp == 0L) {
                Logger.getLogger(MegaConstant.class.getName()).log(Level.INFO,
                        "Failed to convert read lock to a write lock. Unlocking read lock and blocking on obtaining write lock.");
                if (readStamp != 0L) valueGuard.unlockRead(readStamp);
                stamp = valueGuard.writeLock();
            }
            value = (T) product.coerceTo(masqueradesAs);
        } catch (CoercionException e) {
            throw new IllegalStateException("Unable to coerce value to return type", e);
        } finally {
            valueGuard.unlockWrite(stamp);
        }
        return value;
    }

    public T getValue() {
        long stamp = valueGuard.tryOptimisticRead();
        if (value != null && valueGuard.validate(stamp)) return value;
        stamp = valueGuard.readLock();
        try {
            return value != null ? value : calculate(stamp);
        } finally {
            // calculate() might release the read lock, so do a basic check here before unlocking
            if (valueGuard.isReadLocked()) valueGuard.unlockRead(stamp);
        }
    }

    /**
     * Obtain the exponent of a factor in this mega constant.
     * If the given factor is not present, a value of 0 is returned.
     * Currently, this method only understands positive integer factors
     * and {@code Numeric} subtypes that have the {@code @Constant} annotation.
     * @param factor any {@code Numeric} instance
     * @return the exponent representing the factor's presence in this constant, or 0 if not present
     * @since 0.5
     */
    public long factorsOf(Numeric factor) {
        if (factor.isCoercibleTo(IntegerType.class)) {
            if (One.isUnity(factor)) throw new ArithmeticException("Unity is a universal factor");
            try {
                IntegerType intfactor = (IntegerType) factor.coerceTo(IntegerType.class);
                if (intfactor.sign() != Sign.POSITIVE) throw new ArithmeticException("Only positive integers are supported");
                if (Zero.isZero(leadingCoefficient().numerator().modulus(intfactor))) {
                    long count = 1L;
                    IntegerType reduced = (IntegerType) leadingCoefficient().numerator().divide(intfactor);
                    while (Zero.isZero(reduced.modulus(intfactor))) {
                        reduced = (IntegerType) reduced.divide(intfactor);
                        count++;
                    }

                    return count;
                } else if (Zero.isZero(leadingCoefficient().denominator().modulus(intfactor))) {
                    // if we get factors in the denominator of the leading coefficient, return a negative value
                    long count = -1L;
                    IntegerType reduced = (IntegerType) leadingCoefficient().denominator().divide(intfactor);
                    while (Zero.isZero(reduced.modulus(intfactor))) {
                        reduced = (IntegerType) reduced.divide(intfactor);
                        count--;
                    }

                    return count;
                }
            } catch (CoercionException e) {
                Logger.getLogger(MegaConstant.class.getName()).log(Level.SEVERE,
                        "Cannot coerce " + factor + " to IntegerType after coercibility check", e);
                throw new ArithmeticException(factor + " is only pretending to be coercible to an integer");
            }
            return 0L; // we know we won't match an integer value anywhere else
        }
        // otherwise. we check the array of constants
        for (int idx = 0; idx < constants.size(); idx++) {
            // we just need to check if the constant class matches, so we're not comparing math contexts
            if (constants.get(idx).getClass() == factor.getClass()) {
                return exponents.get(idx);
            }
        }

        // default value
        return 0L;
    }

    /**
     * Combine two mega constants into a single mega constant.
     * @param other the other mega constant to ingest
     * @return the combined mega constant
     */
    public MegaConstant<T> combine(MegaConstant<T> other) {
        if (other.masqueradesAs != this.masqueradesAs) throw new IllegalArgumentException("Cannot combine dissimilar types");
        try {
            return doCombine(other);
        } catch (CoercionException e) {
            throw new IllegalStateException("While combining " + this + " and " + other, e);
        }
    }

    /**
     * Combine the contents of the given {@code MegaConstant} with this {@code MegaConstant}.
     * The result is returned in a brand new {@code MegaConstant}, while {@code this} remains
     * unaltered.<br>
     * This method must be implemented by any concrete subclass.  The result should
     * be equivalent to taking the product of {@code this} and {@code other}.
     * @param other the mega constant to combine with this one
     * @return the combined mega constant
     * @throws CoercionException if a type coercion error occurs during this operation
     */
    protected abstract MegaConstant<T> doCombine(MegaConstant<T> other) throws CoercionException;

    public RationalType leadingCoefficient() {
        return rationalCoefficient;
    }

    /**
     * Obtain a list view of the inner contents of this mega constant,
     * with each pair of constant and exponent presented as a tuple.
     * @return a {@code List<ConstantTuple>} containing all constants and their exponents
     */
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
        if (!rationalCoefficient.numerator().asBigInteger().equals(BigInteger.ONE) ||
                numExponents.isEmpty()) {
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
        if (denomNotUnity || !denomExponents.isEmpty()) {
            buf.append(DIVISION_SLASH);
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
            Numeric cval = constants.get(k);
            Constant constant = cval.getClass().getAnnotation(Constant.class);
            if (constant == null) throw new IllegalStateException(cval + " is not a constant");
            buf.append(constant.representation().equals(Constant.USE_TOSTRING) ? cval.toString() : constant.representation());
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

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ConstantTuple tuple = (ConstantTuple) o;
            return exponent == tuple.exponent && Objects.equals(constantValue, tuple.constantValue);
        }

        @Override
        public int hashCode() {
            return Objects.hash(constantValue, exponent);
        }
    }
}
