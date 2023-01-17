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
import tungsten.types.functions.impl.Cos;
import tungsten.types.functions.impl.Sin;
import tungsten.types.numerics.*;
import tungsten.types.set.impl.NumericSet;
import tungsten.types.util.MathUtils;
import tungsten.types.util.RangeUtils;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.util.Arrays;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

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
    
    public ComplexPolarImpl(RealType modulus, RealType argument) {
        if (modulus.sign() == Sign.NEGATIVE) {
            throw new IllegalArgumentException("Complex modulus must be positive");
        }
        this.modulus = modulus;
        this.argument = argument;
        this.mctx = MathUtils.inferMathContext(Arrays.asList(modulus, argument));
        epsilon = MathUtils.computeIntegerExponent(TEN, 1 - this.mctx.getPrecision(), this.mctx);
        this.cos = new Cos(epsilon);
        this.sin = new Sin(epsilon);
    }
    
    public ComplexPolarImpl(RealType modulus, RealType argument, boolean exact) {
        this(modulus, argument);
        this.exact = exact;
    }

    public static final char SEPARATOR = '@';

    /**
     * A constructor that takes a {@link String} as input and parses it
     * into a polar complex value. The modulus and the argument are
     * separated by the @ symbol.  If the argument is followed by the
     * degree symbol (e.g., 90&deg;, unicode U+00B0), the argument is interpreted as an
     * angle in degrees rather than radians.
     * @param strval the string representation of a complex polar value
     */
    public ComplexPolarImpl(String strval) {
        String strMod = strval.substring(0, strval.indexOf(SEPARATOR)).trim();
        String strAng = strval.substring(strval.indexOf(SEPARATOR) + 1).trim();
        boolean usesDegrees = false;
        if (strAng.endsWith("\u00B0")) {
            usesDegrees = true;
            strAng = strAng.substring(0, strAng.length() - 1).trim();
        }
        RealType angle = new RealImpl(strAng);
        if (usesDegrees) {
            MathContext ctx = angle.getMathContext();
            angle = (RealType) Pi.getInstance(ctx).multiply(angle).divide(new RealImpl(BigDecimal.valueOf(180L)));
        }
        this.modulus = new RealImpl(strMod);
        if (modulus.sign() == Sign.NEGATIVE) throw new IllegalArgumentException("Complex modulus must be positive");
        this.argument = angle;
        this.mctx = MathUtils.inferMathContext(Arrays.asList(this.modulus, this.argument));
        epsilon = MathUtils.computeIntegerExponent(TEN, 1 - this.mctx.getPrecision(), this.mctx);
        this.cos = new Cos(epsilon);
        this.sin = new Sin(epsilon);
    }

    public void setMathContext(MathContext mctx) {
        this.mctx = mctx;
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

    private final Cos cos;

    @Override
    public RealType real() {
        return (RealType) cos.apply(argument).multiply(modulus);
    }

    private final Sin sin;

    @Override
    public RealType imaginary() {
        return (RealType) sin.apply(argument).multiply(modulus);
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
        if (addend instanceof ComplexType) {
            ComplexType cadd = (ComplexType) addend;
            return new ComplexRectImpl((RealType) this.real().add(cadd.real()),
                    (RealType) this.imaginary().add(cadd.imaginary()),
                    this.isExact() && cadd.isExact());
        } else if (addend.isCoercibleTo(RealType.class)) {
            try {
                RealType realval = (RealType) addend.coerceTo(RealType.class);
                return new ComplexRectImpl((RealType) this.real().add(realval), this.imaginary(), exact && realval.isExact());
            } catch (CoercionException ex) {
                // we should never get here
                Logger.getLogger(ComplexPolarImpl.class.getName()).log(Level.SEVERE, "Failed to coerce addend to RealType", ex);
            }
        }
        throw new UnsupportedOperationException("Addend of type " + addend.getClass().getTypeName() + " is not supported");
    }

    @Override
    public Numeric subtract(Numeric subtrahend) {
        if (Zero.isZero(subtrahend)) return this;
        if (subtrahend instanceof ComplexType) {
            ComplexType csub = (ComplexType) subtrahend;
            return new ComplexRectImpl((RealType) this.real().subtract(csub.real()),
                    (RealType) this.imaginary().subtract(csub.imaginary()),
                    this.isExact() && csub.isExact());
        } else if (subtrahend.isCoercibleTo(RealType.class)) {
            try {
                RealType realval = (RealType) subtrahend.coerceTo(RealType.class);
                return new ComplexRectImpl((RealType) this.real().subtract(realval), this.imaginary(), exact && realval.isExact());
            } catch (CoercionException ex) {
                // we should never get here
                Logger.getLogger(ComplexPolarImpl.class.getName()).log(Level.SEVERE, "Failed to coerce subtrahend to RealType", ex);
            }
        }
        throw new UnsupportedOperationException("Subtrahend of type " + subtrahend.getClass().getTypeName() + " is not supported");
    }

    @Override
    public Numeric multiply(Numeric multiplier) {
        if (Zero.isZero(multiplier)) return ExactZero.getInstance(mctx);
        if (One.isUnity(multiplier)) return this;
        if (multiplier instanceof ComplexType) {
            ComplexType cmult = (ComplexType) multiplier;
            RealType modnew = (RealType) modulus.multiply(cmult.magnitude());
            RealType argnew = (RealType) argument.add(cmult.argument());
            ComplexPolarImpl result = new ComplexPolarImpl(modnew, argnew, exact && cmult.isExact());
            result.setMathContext(mctx);
            return result;
        } else if (multiplier.isCoercibleTo(RealType.class)) {
            try {
                RealType scalar = (RealType) multiplier.coerceTo(RealType.class);
                switch (scalar.sign()) {
                    case NEGATIVE:
                        Pi pi = Pi.getInstance(mctx);
                        RealType absval = scalar.magnitude();
                        return new ComplexPolarImpl((RealType) modulus.multiply(absval), (RealType) argument.add(pi), false);
                    default:
                        return new ComplexPolarImpl((RealType) modulus.multiply(scalar), argument, exact && scalar.isExact());
                }
            } catch (CoercionException ex) {
                // we should never get here
                Logger.getLogger(ComplexPolarImpl.class.getName()).log(Level.SEVERE, "Failed to coerce multiplier to RealType.", ex);
            }
        }
        throw new UnsupportedOperationException("Unsupported type of multiplier");
    }

    @Override
    public Numeric divide(Numeric divisor) {
        if (Zero.isZero(divisor)) throw new ArithmeticException("Division by 0");
        if (One.isUnity(divisor)) return this;
        if (divisor instanceof ComplexType) {
            ComplexType cdiv = (ComplexType) divisor;
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
                        throw new IllegalArgumentException("Division by zero not allowed");
                    case NEGATIVE:
                        Pi pi = Pi.getInstance(mctx);
                        RealType absval = scalar.magnitude();
                        return new ComplexPolarImpl((RealType) modulus.divide(absval), (RealType) argument.subtract(pi), false);
                    default:
                        return new ComplexPolarImpl((RealType) modulus.divide(scalar), argument);
                }
            } catch (CoercionException ex) {
                // we should never get here
                Logger.getLogger(ComplexPolarImpl.class.getName()).log(Level.SEVERE, "Failed to coerce divisor to RealType.", ex);
            }
        }
        throw new UnsupportedOperationException("Unsupported type of divisor");
    }

    @Override
    public Numeric inverse() {
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
            return new ComplexPolarImpl(modnew, argnew, exact);
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
                Logger.getLogger(ComplexPolarImpl.class.getName()).log(Level.SEVERE, "Division by IntegerType n yielded non-coercible result.", ex);
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
            Logger.getLogger(ComplexPolarImpl.class.getName()).log(Level.SEVERE, "Error coercing all roots to ComplexType", ex);
            throw new IllegalStateException("Coercing one of n roots failed", ex);
        }
    }
    
    public Set<ComplexType> nthRoots(long n) {
        return nthRoots(new IntegerImpl(BigInteger.valueOf(n), true));
    }

    /**
     * A strict test for equality.  The algorithm makes no attempt to normalize the argument (phase angle)
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
        if (o instanceof ComplexType) {
            ComplexType that = (ComplexType) o;
            if (this.isExact() != that.isExact()) return false;
            return this.modulus.equals(that.magnitude()) && this.normalizeArgument().equals(that.argument());
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
        return modulus.toString() + " \u2220" + argument.toString();
    }

    @Override
    public MathContext getMathContext() {
        return mctx;
    }
}
