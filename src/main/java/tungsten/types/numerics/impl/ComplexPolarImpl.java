/* 
 * The MIT License
 *
 * Copyright © 2018 Robert Poole <Tarquin.AZ@gmail.com>.
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
package tungsten.types.numerics.impl;

import tungsten.types.Numeric;
import tungsten.types.Range;
import tungsten.types.Set;
import tungsten.types.annotations.Polar;
import tungsten.types.exceptions.CoercionException;
import tungsten.types.numerics.*;
import tungsten.types.set.impl.NumericSet;
import tungsten.types.util.*;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

import static tungsten.types.util.MathUtils.cos;
import static tungsten.types.util.MathUtils.sin;

/**
 * An implementation of {@link ComplexType} that uses a polar representation
 * internally.  This representation is typically superior for computing
 * multiplication, division, roots, and powers.
 *
 * @author Robert Poole, <a href="mailto:Tarquin.AZ@gmail.com">Tarquin.AZ@gmail.com</a>
 */
@Polar
public class ComplexPolarImpl implements ComplexType {
    private final RealType modulus;
    private final RealType argument;
    private MathContext mctx;
    private RealType epsilon;
    private boolean exact = true;

    private static final RealType TWO = new RealImpl(BigDecimal.valueOf(2L), MathContext.UNLIMITED);
    private static final RealType TEN = new RealImpl(BigDecimal.TEN, MathContext.UNLIMITED);

    /**
     * Construct a polar complex number value given a modulus and an argument.
     * @param modulus  the magnitude of this complex value
     * @param argument the angle formed between the positive real axis and the line
     *                 segment defined by this point on the complex plane
     */
    public ComplexPolarImpl(RealType modulus, RealType argument) {
        if (modulus.sign() == Sign.NEGATIVE) {
            throw new IllegalArgumentException("Complex modulus must be positive or zero");
        }
        this.modulus = modulus;
        this.argument = argument;
        this.mctx = MathUtils.inferMathContext(List.of(modulus, argument));
        epsilon = MathUtils.computeIntegerExponent(TEN, 1 - this.mctx.getPrecision(), this.mctx);
    }

    /**
     * Construct a polar complex number value given a modulus, an argument, and an
     * indicator of exactness.
     * @param modulus  the magnitude of this value
     * @param argument the angle of this complex value with respect to the positive
     *                 real axis
     * @param exact    whether this value is to be considered exact or not
     */
    public ComplexPolarImpl(RealType modulus, RealType argument, boolean exact) {
        this(modulus, argument);
        this.exact = exact;
    }

    /**
     * A convenience constructor for converting a real into a complex.
     *
     * @param realVal any real value to be converted
     */
    public ComplexPolarImpl(RealType realVal) {
        this.modulus = realVal.magnitude();
        this.mctx = realVal.getMathContext();
        this.argument = realVal.sign() == Sign.NEGATIVE ? Pi.getInstance(mctx) :
                new RealImpl(BigDecimal.ZERO, mctx);
        this.exact = realVal.isExact();
        epsilon = MathUtils.computeIntegerExponent(TEN, 1 - this.mctx.getPrecision(), this.mctx);
    }

    /**
     * The separator character used to mark the division between
     * the modulus and argument of a complex angular string value, used
     * by any constructor that takes a {@link String} argument.
     */
    public static final char SEPARATOR = '@';

    /**
     * A constructor that takes a {@link String} as input and parses it
     * into a polar complex value. The modulus and the argument are
     * separated by the @ symbol.  If the argument contains the
     * degree symbol (e.g., 90&deg;, unicode U+00B0) and matches one of the
     * patterns recognized by {@link AngularDegrees}, then it is interpreted as an
     * angle in degrees rather than radians.
     * @param strval the string representation of a complex polar value
     */
    public ComplexPolarImpl(String strval) {
        String strMod = strval.substring(0, strval.indexOf(SEPARATOR)).strip();
        String strAng = UnicodeTextEffects.sanitizeDecimal(strval.substring(strval.indexOf(SEPARATOR) + 1).strip());
        RealType angle;
        if (AngularDegrees.isDecimalDegrees(strAng) || AngularDegrees.isDMS(strAng)) {
            AngularDegrees degrees = new AngularDegrees(strAng);
            Logger.getLogger(ComplexPolarImpl.class.getName()).log(Level.INFO,
                    "Parsed degree argument {0} from {1}",
                    new Object[] {degrees, strval});
            angle = degrees.asRadians();
        } else {
            // this is slightly inefficient since the RealImpl constructor will also sanitize String inputs
            // and we already sanitized strAng above
            angle = new RealImpl(strAng);
        }
        this.modulus = new RealImpl(strMod);
        if (modulus.sign() == Sign.NEGATIVE) throw new IllegalArgumentException("Complex modulus must be positive or zero");
        this.argument = angle;
        // the following doesn't really work since modulus and argument probably have precision = 0
//        this.mctx = MathUtils.inferMathContext(List.of(this.modulus, this.argument));
//        epsilon = MathUtils.computeIntegerExponent(TEN, 1 - this.mctx.getPrecision(), this.mctx);
        this.mctx = MathContext.UNLIMITED;
        this.epsilon = new RealImpl("0.00001");
    }

    /**
     * A constructor which takes a {@code String} representation of a
     * polar complex value and a {@code MathContext} which governs all
     * arithmetic operations.
     * @param strval the string representation of a polar complex value
     * @param mctx   the {@code MathContext} to associate with this value
     */
    public ComplexPolarImpl(String strval, MathContext mctx) {
        this(strval);
        setMathContext(mctx);
    }

    /**
     * Set the {@code MathContext} to associate with this complex value.
     * @param mctx the {@code MathContext} to use
     */
    public void setMathContext(MathContext mctx) {
        this.mctx = mctx;
        if (argument.getMathContext().getPrecision() < mctx.getPrecision())
            OptionalOperations.setMathContext(argument, mctx);
        if (modulus.getMathContext().getPrecision() < mctx.getPrecision())
            OptionalOperations.setMathContext(modulus, mctx);
        this.epsilon = MathUtils.computeIntegerExponent(TEN, 1 - mctx.getPrecision(), mctx);
    }
    
    @Override
    public RealType magnitude() {
        return modulus;
    }

    @Override
    public ComplexType negate() {
        final Pi pi = Pi.getInstance(mctx);
        final ComplexPolarImpl negval = new ComplexPolarImpl(modulus, (RealType) argument.add(pi), false);
        negval.setMathContext(mctx);
        return negval;
    }

    @Override
    public ComplexType conjugate() {
        ComplexPolarImpl conj = new ComplexPolarImpl(modulus, argument.negate(), exact);
        conj.setMathContext(mctx);
        return conj;
    }

    @Override
    public RealType real() {
        return (RealType) cos(argument).multiply(modulus);
    }

    @Override
    public RealType imaginary() {
        return (RealType) sin(argument).multiply(modulus);
    }

    @Override
    public RealType argument() {
        return argument;
    }

    @Override
    public boolean isExact() {
        return exact;
    }

    @Override
    public boolean isCoercibleTo(Class<? extends Numeric> numtype) {
        if (numtype == Numeric.class) return true;
        NumericHierarchy htype = NumericHierarchy.forNumericType(numtype);
        switch (htype) {
            case COMPLEX:
                return true;
            case REAL:
                final Pi pi = Pi.getInstance(mctx);
                final RealType zero = new RealImpl(BigDecimal.ZERO, mctx);
                final RealType normalizedArg = normalizeArgument();
                return MathUtils.areEqualToWithin(normalizedArg, zero, epsilon) ||
                        MathUtils.areEqualToWithin(normalizedArg, pi, epsilon);
            default:
                return false;
        }
    }

    /**
     * Normalize the argument of this polar complex value
     * to the range (&minus;&pi;, &pi;].
     * @return the normalized polar argument
     */
    protected RealType normalizeArgument() {
        if (argument instanceof Pi) {
            // special case where argument is exactly 𝞹
            return argument;
        }
        final RealType twopi = (RealType) Pi.getInstance(mctx).multiply(TWO);
        RealType realVal = argument;
        Range<RealType> atan2range = RangeUtils.getAngularInstance(mctx);
        
        if (atan2range.contains(realVal)) {
            // already in the range (-𝞹, 𝞹]
            return argument;
        } else {
            // reduce values > 𝞹
            while (atan2range.isAbove(realVal)) {
                realVal = (RealType) realVal.subtract(twopi);
            }
            // increase values < -𝞹
            while (atan2range.isBelow(realVal)) {
                realVal = (RealType) realVal.add(twopi);
            }
            return realVal;
        }
    }

    @Override
    public Numeric coerceTo(Class<? extends Numeric> numtype) throws CoercionException {
        if (numtype == Numeric.class) return this;
        NumericHierarchy htype = NumericHierarchy.forNumericType(numtype);
        switch (htype) {
            case COMPLEX:
                return this;
            case REAL:
                final RealType zero = new RealImpl(BigDecimal.ZERO, mctx);
                final RealType pi = Pi.getInstance(mctx);
                final RealType normalizedArg = normalizeArgument();
                if (MathUtils.areEqualToWithin(normalizedArg, zero, epsilon)) {
                    return modulus;
                } else if (MathUtils.areEqualToWithin(normalizedArg, pi, epsilon)) {
                    return modulus.negate();
                } else {
                    throw new CoercionException("Argument must be 0 or \uD835\uDF0B", this.getClass(), numtype);
                }
            default:
                throw new CoercionException("Unsupported coercion", this.getClass(), numtype);
        }
    }

    @Override
    public Numeric add(Numeric addend) {
        if (Zero.isZero(addend)) return this;
        if (addend instanceof ComplexType cadd) {
            if (addend instanceof PointAtInfinity && ComplexType.isExtendedEnabled()) {
                return PointAtInfinity.getInstance();
            }
            return new ComplexRectImpl((RealType) this.real().add(cadd.real()),
                    (RealType) this.imaginary().add(cadd.imaginary()),
                    this.isExact() && cadd.isExact());
        } else if (addend.isCoercibleTo(RealType.class)) {
            try {
                RealType realval = (RealType) addend.coerceTo(RealType.class);
                return new ComplexRectImpl((RealType) this.real().add(realval), this.imaginary(), exact && realval.isExact());
            } catch (CoercionException ex) {
                // we should never get here
                Logger.getLogger(ComplexPolarImpl.class.getName()).log(Level.SEVERE,
                        "Failed coercion after check for coercibility.", ex);
            }
        }
        throw new UnsupportedOperationException("Addend of type " + addend.getClass().getTypeName() + " is not supported");
    }

    @Override
    public Numeric subtract(Numeric subtrahend) {
        if (Zero.isZero(subtrahend)) return this;
        if (subtrahend instanceof ComplexType csub) {
            if (subtrahend instanceof PointAtInfinity && ComplexType.isExtendedEnabled()) {
                // there is only one infinity
                return PointAtInfinity.getInstance();
            }
            return new ComplexRectImpl((RealType) this.real().subtract(csub.real()),
                    (RealType) this.imaginary().subtract(csub.imaginary()),
                    this.isExact() && csub.isExact());
        } else if (subtrahend.isCoercibleTo(RealType.class)) {
            try {
                RealType realval = (RealType) subtrahend.coerceTo(RealType.class);
                return new ComplexRectImpl((RealType) this.real().subtract(realval), this.imaginary(), exact && realval.isExact());
            } catch (CoercionException ex) {
                // we should never get here
                Logger.getLogger(ComplexPolarImpl.class.getName()).log(Level.SEVERE,
                        "Failed coercion after check for coercibility.", ex);
            }
        }
        throw new UnsupportedOperationException("Subtrahend of type " + subtrahend.getClass().getTypeName() + " is not supported");
    }

    @Override
    public Numeric multiply(Numeric multiplier) {
        if (Zero.isZero(multiplier)) return ExactZero.getInstance(mctx);
        if (One.isUnity(multiplier)) return this;
        if (multiplier instanceof ComplexType cmult) {
            if (multiplier instanceof PointAtInfinity && ComplexType.isExtendedEnabled()) {
                if (Zero.isZero(this.modulus)) throw new ArithmeticException("0 \u22C5 ∞ is undefined");
                return PointAtInfinity.getInstance();
            }
            RealType modnew = (RealType) modulus.multiply(cmult.magnitude());
            RealType argnew = (RealType) argument.add(cmult.argument());
            ComplexPolarImpl result = new ComplexPolarImpl(modnew, argnew, exact && cmult.isExact());
            result.setMathContext(mctx);
            return result;
        } else if (multiplier.isCoercibleTo(RealType.class)) {
            try {
                RealType scalar = (RealType) multiplier.coerceTo(RealType.class);
                if (scalar.sign() == Sign.NEGATIVE) {
                    Pi pi = Pi.getInstance(mctx);
                    RealType absval = scalar.magnitude();
                    return new ComplexPolarImpl((RealType) modulus.multiply(absval), (RealType) argument.add(pi), false);
                }
                return new ComplexPolarImpl((RealType) modulus.multiply(scalar), argument, exact && scalar.isExact());
            } catch (CoercionException ex) {
                // we should never get here
                Logger.getLogger(ComplexPolarImpl.class.getName()).log(Level.SEVERE,
                        "Failed coercion after check for coercibility.", ex);
            }
        }
        throw new UnsupportedOperationException("Unsupported type of multiplier");
    }

    @Override
    public Numeric divide(Numeric divisor) {
        if (Zero.isZero(divisor)) {
            if (ComplexType.isExtendedEnabled()) {
                if (Zero.isZero(modulus)) throw new ArithmeticException("0/0 is undefined");
                return PointAtInfinity.getInstance();
            }
            throw new ArithmeticException("Division by 0");
        }
        if (One.isUnity(divisor)) return this;
        if (divisor instanceof ComplexType cdiv) {
            if (divisor instanceof PointAtInfinity && ComplexType.isExtendedEnabled()) {
                return ExactZero.getInstance(mctx);  // was: new ComplexPolarImpl(new RealImpl(BigDecimal.ZERO, mctx));
            }
            RealType modnew = (RealType) modulus.divide(cdiv.magnitude());
            RealType argnew = (RealType) argument.subtract(cdiv.argument());
            ComplexPolarImpl result = new ComplexPolarImpl(modnew, argnew, exact && cdiv.isExact());
            result.setMathContext(mctx);
            return result;
        } else if (divisor.isCoercibleTo(RealType.class)) {
            try {
                RealType scalar = (RealType) divisor.coerceTo(RealType.class);
                switch (scalar.sign()) {
                    case ZERO:
                        Logger.getLogger(ComplexPolarImpl.class.getName()).log(Level.SEVERE,
                                "Scalar {0} has a sign of ZERO, but should have been handled earlier", scalar);
                        throw new IllegalStateException("Scalar from divisor has sign of ZERO (should not have gotten here)");
                    case NEGATIVE:
                        Pi pi = Pi.getInstance(mctx);
                        RealType absval = scalar.magnitude();
                        return new ComplexPolarImpl((RealType) modulus.divide(absval), (RealType) argument.subtract(pi), false);
                    default:
                        return new ComplexPolarImpl((RealType) modulus.divide(scalar), argument);
                }
            } catch (CoercionException ex) {
                // we should never get here
                Logger.getLogger(ComplexPolarImpl.class.getName()).log(Level.SEVERE,
                        "Failed coercion after check for coercibility.", ex);
            }
        }
        throw new UnsupportedOperationException("Unsupported type of divisor");
    }

    @Override
    public ComplexType inverse() {
        if (Zero.isZero(modulus) && ComplexType.isExtendedEnabled()) {
            return PointAtInfinity.getInstance();
        }
        // special case of division where the numerator has a modulus of 1
        // and an argument of 0
        return new ComplexPolarImpl((RealType) modulus.inverse(), argument.negate(), exact);
    }

    @Override
    public Numeric sqrt() {
        assert modulus.sign() != Sign.NEGATIVE;
        try {
            RealType modnew = (RealType) modulus.sqrt().coerceTo(RealType.class);
            RealType argnew = (RealType) argument.divide(TWO);
            return new ComplexPolarImpl(modnew, argnew, exact && modnew.isExact());
        } catch (CoercionException e) {
            throw new IllegalStateException(e);
        }
    }
    
    @Override
    public Set<ComplexType> nthRoots(IntegerType n) {
        ComplexType principalRoot;
        final long nLong = n.asBigInteger().longValueExact();
        if (nLong < 1L) throw new IllegalArgumentException("Degree of roots must be \u2265 1");
        if (nLong == 2L) {
            principalRoot = (ComplexType) sqrt();
        } else {
            try {
                principalRoot = new ComplexPolarImpl(MathUtils.nthRoot(modulus, n, mctx),
                        (RealType) argument.divide(n).coerceTo(RealType.class), exact);
            } catch (CoercionException ex) {
                Logger.getLogger(ComplexPolarImpl.class.getName()).log(Level.SEVERE,
                        "Division by IntegerType n yielded non-coercible result.", ex);
                throw new ArithmeticException("Error computing principal root");
            }
        }
        Set<ComplexType> rootsOfUnity = MathUtils.rootsOfUnity(nLong, mctx);
        NumericSet result = new NumericSet();
        for (ComplexType root : rootsOfUnity) {
            result.append(principalRoot.multiply(root));
        }
        try {
            return result.coerceTo(ComplexType.class);
        } catch (CoercionException ex) {
            Logger.getLogger(ComplexPolarImpl.class.getName()).log(Level.SEVERE, "Error coercing all roots to ComplexType.", ex);
            throw new IllegalStateException("Coercing one of n roots failed", ex);
        }
    }

    /**
     * Compute the n<sup>th</sup> roots of this complex value.
     * This is a convenience method which takes a {@code long} instead
     * of an {@code IntegerType}.
     * @param n the degree of the requested roots
     * @return a {@code Set} of {@code n} complex values
     * @see #nthRoots(IntegerType)
     */
    public Set<ComplexType> nthRoots(long n) {
        return nthRoots(new IntegerImpl(BigInteger.valueOf(n), true));
    }

    /**
     * A strict test for equality.  The algorithm may attempt to normalize the argument (phase angle)
     * of any {@link ComplexType} supplied as a parameter. If you need a more forgiving
     * comparison, use {@link #equalToWithin(ComplexPolarImpl, ComplexPolarImpl, RealType)} instead.
     *
     * @param o the object with which to compare this
     * @return true if and only if this complex value is exactly equal to o
     */
    @Override
    public boolean equals(Object o) {
        if (o instanceof Zero) {
            return this.isExact() == ((Zero) o).isExact() && testEquals(this.modulus.asBigDecimal(), BigDecimal.ZERO);
        } else if (o instanceof One) {
            return this.isExact() &&
                    testEquals(this.modulus.asBigDecimal(), BigDecimal.ONE) &&
                    testEquals(this.normalizeArgument().asBigDecimal(), BigDecimal.ZERO);
        }
        if (o instanceof ComplexType that) {
            if (o instanceof PointAtInfinity) return false;
            if (this.isExact() != that.isExact()) return false;
            // both are exact or inexact at this point
            boolean argsMatch = !exact ?
                    MathUtils.areEqualToWithin(this.normalizeArgument(), that.argument(), epsilon) :
                    this.normalizeArgument().equals(that.argument());
            boolean modMatch = !exact ?
                    MathUtils.areEqualToWithin(this.modulus, that.magnitude(), epsilon) :
                    this.modulus.equals(that.magnitude());
            return modMatch && argsMatch;
        }
        return isCoercibleTo(RealType.class) && real().equals(o);
    }

    /**
     * Compare two {@link ComplexPolarImpl} values to within a given precision denoted by {@code epsilon} (symbol: &epsilon;).
     * The normalized arguments of the two complex values are compared, which may not be the case with {@link #equals(Object)},
     * making this method more flexible when dealing with polar complex values, albeit potentially slower.
     *
     * @param A       the first complex polar value to compare
     * @param B       the second complex polar value to compare
     * @param epsilon the precision &epsilon; to within which A and B are compared
     * @return true if and only if the modulus and normalized phase angles of A and B match, false otherwise
     */
    public static boolean equalToWithin(ComplexPolarImpl A, ComplexPolarImpl B, RealType epsilon) {
        if (MathUtils.areEqualToWithin(A.magnitude(), B.magnitude(), epsilon)) {
            return MathUtils.areEqualToWithin(A.normalizeArgument(), B.normalizeArgument(), epsilon);
        }
        return false;
    }
    
    private boolean testEquals(BigDecimal A, BigDecimal B) {
        return A.compareTo(B) == 0;
    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 79 * hash + Objects.hashCode(this.modulus);
        hash = 79 * hash + Objects.hashCode(this.argument);
        hash = 79 * hash + (this.exact ? 1 : 0);
        return hash;
    }
    
    @Override
    public String toString() {
        // returns this complex number in angle notation
        // U+205F = medium mathematical space, U+2220 = angle symbol
        return modulus.toString() + "\u205F\u2220" + argument.toString();
    }

    @Override
    public MathContext getMathContext() {
        return mctx;
    }
}
