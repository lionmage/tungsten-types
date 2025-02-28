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
import tungsten.types.numerics.*;
import tungsten.types.exceptions.CoercionException;
import tungsten.types.util.ClassTools;
import tungsten.types.util.MathUtils;
import tungsten.types.util.OptionalOperations;
import tungsten.types.util.UnicodeTextEffects;
//import tungsten.types.util.OptionalOperations;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;

/**
 * An implementation of a rational number data type.
 *
 * @author Robert Poole
 */
public class RationalImpl implements RationalType {
    private boolean exact = true;
    private BigInteger numerator;
    private BigInteger denominator;
    private MathContext mctx = MathContext.UNLIMITED;

    protected RationalImpl() { }

    public RationalImpl(BigInteger numerator, BigInteger denominator) {
        if (numerator == null || denominator == null) {
            throw new IllegalArgumentException("Numerator and denominator must be non-null");
        } else if (denominator.equals(BigInteger.ZERO)) {
            throw new IllegalArgumentException("Denominator must be non-zero");
        }
        // by convention, we want the denominator to be positive
        if (denominator.signum() < 0) {
            denominator = denominator.negate();
            numerator = numerator.negate();
        }
        this.numerator = numerator;
        this.denominator = denominator;
    }
    
    public RationalImpl(String representation) {
        if (UnicodeTextEffects.hasVulgarFraction(representation)) {
            representation = UnicodeTextEffects.expandFractions(representation);
        }
        Matcher m = SOLIDUS_REGEX.matcher(representation);
        if (!m.find()) {
            throw new IllegalArgumentException("Badly formatted rational value: " + representation);
        }
        final int position = m.start();
        if (position < 1) {
            throw new IllegalArgumentException("Missing numerator: " + representation);
        }
        String numStr = UnicodeTextEffects.sanitizeDecimal(representation.substring(0, position));
        String denomStr = UnicodeTextEffects.sanitizeDecimal(representation.substring(position + 1));
        
        numerator = new BigInteger(numStr.strip());
        denominator = new BigInteger(denomStr.strip());
    }
    
    public RationalImpl(BigInteger numerator, BigInteger denominator, boolean exact) {
        this(numerator, denominator);
        this.exact = exact;
    }

    public RationalImpl(BigInteger numerator, BigInteger denominator, MathContext mctx) {
        this(numerator, denominator);
        this.mctx = mctx;
    }
    
    public RationalImpl(String representation, boolean exact) {
        this(representation);
        this.exact = exact;
    }

    public RationalImpl(String representation, MathContext mctx) {
        this(representation);
        this.mctx = mctx;
    }
    
    /**
     * Convenience constructor which takes {@link IntegerType} arguments.
     * @param numerator the numerator of this fraction
     * @param denominator the denominator of this fraction
     */
    public RationalImpl(IntegerType numerator, IntegerType denominator) {
        this.numerator = numerator.asBigInteger();
        this.denominator = denominator.asBigInteger();
        this.exact = numerator.isExact() && denominator.isExact();
        mctx = pickBestMC(numerator.getMathContext(), denominator.getMathContext());
    }

    public RationalImpl(IntegerType numerator, IntegerType denominator, MathContext mctx) {
        this.numerator = numerator.asBigInteger();
        this.denominator = denominator.asBigInteger();
        this.exact = numerator.isExact() && denominator.isExact();
        this.mctx = mctx;
    }
    
    private MathContext pickBestMC(MathContext first, MathContext second) {
        int precision = Math.min(first.getPrecision(), second.getPrecision());
        return new MathContext(precision, first.getRoundingMode());
    }

    public RationalImpl(long numerator, long denominator, MathContext mctx) {
        this(BigInteger.valueOf(numerator), BigInteger.valueOf(denominator), mctx);
    }

    /**
     * Convenience constructor to convert {@link IntegerType}
     * to a rational.
     * @param val an integer value
     */
    public RationalImpl(IntegerType val) {
        numerator = val.asBigInteger();
        denominator = BigInteger.ONE;
        exact = val.isExact();
        mctx = val.getMathContext();
    }
    
    public void setMathContext(MathContext nuCtx) {
        if (nuCtx != null) this.mctx = nuCtx;
    }

    @Override
    public RationalType magnitude() {
        final RationalImpl magnitude = new RationalImpl(numerator.abs(), denominator, mctx);
        return magnitude.reduce();
    }

    @Override
    public RationalType negate() {
        final RationalImpl result = new RationalImpl(numerator.negate(), denominator, exact);
        result.setMathContext(mctx);
        return result;
    }

    @Override
    public IntegerType[] divideWithRemainder() {
        BigInteger[] results = numerator.divideAndRemainder(denominator);
        // the exactness of the whole number part depends on whether there is in fact a fraction
        return new IntegerType[] {new IntegerImpl(results[0], BigInteger.ZERO.equals(results[1])), new IntegerImpl(results[1])};
    }

    @Override
    public IntegerType numerator() {
        return new IntegerImpl(numerator, exact);
    }

    @Override
    public IntegerType denominator() {
        return new IntegerImpl(denominator, exact);
    }

    protected void setNumerator(IntegerType numerator) {
        setNumerator(numerator.asBigInteger());
        exact = numerator.isExact() && this.isExact();
    }

    protected void setNumerator(BigInteger numerator) {
        this.numerator = numerator;
    }

    protected void setDenominator(IntegerType denom) {
        setDenominator(denom.asBigInteger());
        exact = denom.isExact() && this.isExact();
    }

    protected void setDenominator(BigInteger denom) {
        this.denominator = denom;
    }

    @Override
    public BigDecimal asBigDecimal() {
        BigDecimal decNum = new BigDecimal(numerator);
        BigDecimal decDenom = new BigDecimal(denominator);
        return decNum.divide(decDenom, mctx);
    }

    @Override
    public RationalType reduce() {
        final BigInteger gcd = numerator.gcd(denominator);
        if (gcd.equals(BigInteger.ONE)) {
            // this fraction cannot be reduced any further
            return this;
        }
        RationalImpl reduced = new RationalImpl(numerator.divide(gcd), denominator.divide(gcd), exact);
        reduced.setMathContext(mctx);
        return reduced;
    }

    @Override
    public boolean isExact() {
        return exact;
    }

    @Override
    public boolean isCoercibleTo(Class<? extends Numeric> numtype) {
        if (numtype == Numeric.class) return true;
        if (IntegerType.class.isAssignableFrom(numtype)) {
            return denominator.equals(BigInteger.ONE) || numerator.equals(BigInteger.ZERO);
        }
        // for anything other than an integer or an abstract type, always true
        return !ClassTools.isAbstractType(numtype);
    }

    @Override
    public Numeric coerceTo(Class<? extends Numeric> numtype) throws CoercionException {
        if (numtype == Numeric.class) {
            if (exact) {
                // if it's one of two special values, return One or Zero
                if (numerator.equals(BigInteger.ZERO)) return ExactZero.getInstance(mctx);
                if (denominator.equals(numerator)) return One.getInstance(mctx);
            }

            return this;
        }
        NumericHierarchy htype = NumericHierarchy.forNumericType(numtype);
        switch (htype) {
            case INTEGER:
                if (denominator.equals(BigInteger.ONE)) {
                    return new IntegerImpl(numerator, exact);
                } else if (numerator.equals(BigInteger.ZERO)) {
                    return new IntegerImpl(BigInteger.ZERO, exact);
                } else {
                    throw new CoercionException("Cannot convert fraction to integer", this.getClass(), numtype);
                }
            case RATIONAL:
                return this;
            case REAL:
                return new RealImpl(this, mctx);
            case COMPLEX:
                final RealType zero = (RealType) ExactZero.getInstance(mctx).coerceTo(RealType.class);
                final RealType creal = new RealImpl(this, mctx);
                return new ComplexRectImpl(creal, zero, exact);
            default:
                throw new CoercionException("Cannot convert rational to unknown type", this.getClass(), numtype);
        }
    }

    @Override
    public Numeric add(Numeric addend) {
        final int addendPrecision = addend.getMathContext().getPrecision();
        if (addend instanceof RationalType) {
            RationalType that = (RationalType) addend;
            BigInteger denomnew = this.denominator.multiply(that.denominator().asBigInteger());
            BigInteger numleft = this.numerator.multiply(that.denominator().asBigInteger());
            BigInteger numright = that.numerator().asBigInteger().multiply(this.denominator);
            boolean exactness = this.isExact() && that.isExact();
            RationalImpl sum = new RationalImpl(numleft.add(numright), denomnew, exactness);
            sum.setMathContext(addendPrecision > 0 && addendPrecision < mctx.getPrecision() ?
                    addend.getMathContext() : mctx);
            return sum.reduce();
        } else if (addend instanceof IntegerType) {
            IntegerType that = (IntegerType) addend;
            BigInteger scaled = this.denominator.multiply(that.asBigInteger());
            boolean exactness = this.isExact() && that.isExact();
            RationalImpl sum = new RationalImpl(this.numerator.add(scaled), this.denominator, exactness);
            sum.setMathContext(addendPrecision > 0 && addendPrecision < mctx.getPrecision() ?
                    addend.getMathContext() : mctx);
            return sum;
        } else {
            Class<?> iface = ClassTools.getInterfaceTypeFor(addend.getClass());
            if (iface == Numeric.class) {
                // to avoid infinite recursion
                return addend.add(this);
            }
            // if we got here, addend is probably something like Real or ComplexType
            try {
                return this.coerceTo((Class<? extends Numeric>) iface).add(addend);
            } catch (CoercionException ex) {
                Logger.getLogger(RationalImpl.class.getName()).log(Level.SEVERE, "Failed to coerce type during rational add.", ex);
            }
        }
        throw new UnsupportedOperationException("Addition operation unsupported");
    }

    @Override
    public Numeric subtract(Numeric subtrahend) {
        final int subtrahendPrecision = subtrahend.getMathContext().getPrecision();
        if (subtrahend instanceof RationalType) {
            RationalType that = (RationalType) subtrahend;
            BigInteger denomnew = this.denominator.multiply(that.denominator().asBigInteger());
            BigInteger numleft = this.numerator.multiply(that.denominator().asBigInteger());
            BigInteger numright = that.numerator().asBigInteger().multiply(this.denominator);
            boolean exactness = this.isExact() && that.isExact();
            RationalImpl diff = new RationalImpl(numleft.subtract(numright), denomnew, exactness);
            diff.setMathContext(subtrahendPrecision > 0 && subtrahendPrecision < mctx.getPrecision() ?
                    subtrahend.getMathContext() : mctx);
            return diff.reduce();
        } else if (subtrahend instanceof IntegerType) {
            IntegerType that = (IntegerType) subtrahend;
            BigInteger scaled = this.denominator.multiply(that.asBigInteger());
            boolean exactness = this.isExact() && that.isExact();
            RationalImpl diff = new RationalImpl(this.numerator.subtract(scaled), this.denominator, exactness);
            diff.setMathContext(subtrahendPrecision > 0 && subtrahendPrecision < mctx.getPrecision() ?
                    subtrahend.getMathContext() : mctx);
            return diff;
        } else {
            Class<?> iface = ClassTools.getInterfaceTypeFor(subtrahend.getClass());
            if (iface == Numeric.class) {
                // to avoid infinite recursion
                return subtrahend.negate().add(this);
            }
            // if we got here, subtrahend is probably something like Real or ComplexType
            try {
                return this.coerceTo((Class<? extends Numeric>) iface).subtract(subtrahend);
            } catch (CoercionException ex) {
                Logger.getLogger(RationalImpl.class.getName()).log(Level.SEVERE,
                        "Failed to coerce type during rational subtract.", ex);
            }
        }
        throw new UnsupportedOperationException("Subtraction operation unsupported");
    }

    @Override
    public Numeric multiply(Numeric multiplier) {
        if (multiplier instanceof RationalType) {
            final RationalType that = (RationalType) multiplier;
            RationalImpl result = new RationalImpl(numerator.multiply(that.numerator().asBigInteger()),
                    denominator.multiply(that.denominator().asBigInteger()),
                    exact && that.isExact());
            result.setMathContext(mctx);
            return result.reduce();
        } else if (multiplier instanceof IntegerType) {
            final IntegerType that = (IntegerType) multiplier;
            if (that.equals(denominator())) return numerator();  // small optimization
            final RationalType intermediate = new RationalImpl(numerator.multiply(that.asBigInteger()),
                    denominator, exact && that.isExact()).reduce();
            if (intermediate.isCoercibleTo(IntegerType.class)) {
                if (mctx.getPrecision() == 0) return intermediate.numerator();
                // otherwise, generate an anonymous subclass which preserves the MathContext
                return new IntegerImpl(intermediate.numerator().asBigInteger(), intermediate.isExact()) {
                    @Override
                    public MathContext getMathContext() {
                        return mctx;
                    }
                };
            }
            OptionalOperations.setMathContext(intermediate, mctx);
            return intermediate;
        } else {
            Class<?> iface = ClassTools.getInterfaceTypeFor(multiplier.getClass());
            if (iface == Numeric.class) {
                // to avoid infinite recursion
                return multiplier.multiply(this);
            }
            // if we got here, multiplier is probably something like Real or ComplexType
            try {
                return this.coerceTo((Class<? extends Numeric>) iface).multiply(multiplier);
            } catch (CoercionException ex) {
                Logger.getLogger(RationalImpl.class.getName()).log(Level.SEVERE,
                        "Failed to coerce type during rational multiply.", ex);
            }
        }
        throw new UnsupportedOperationException("Multiplication operation unsupported");
    }

    @Override
    public Numeric divide(Numeric divisor) {
        if (Zero.isZero(divisor)) throw new ArithmeticException("Division by zero");
        if (One.isUnity(divisor)) return this;
        if (divisor instanceof IntegerType) {
            IntegerType num = numerator();
            BigInteger gcd = numerator.abs().gcd(((IntegerType) divisor).asBigInteger().abs());
            if (!BigInteger.ONE.equals(gcd)) {
                IntegerType common = new IntegerImpl(gcd, num.isExact() && divisor.isExact());
                num = (IntegerType) num.divide(common);
                divisor = divisor.divide(common);
            }
            return new RationalImpl(num, (IntegerType) denominator().multiply(divisor), mctx);
        }
        return this.multiply(divisor.inverse());
    }

    @Override
    public IntegerType modulus() {
        return new IntegerImpl(numerator.mod(denominator), this.isExact());
    }

    @Override
    public Numeric inverse() {
        if (numerator.equals(BigInteger.ZERO)) throw new ArithmeticException("Cannot take inverse of 0");
        if (numerator.equals(BigInteger.ONE)) {
            if (mctx.getPrecision() == 0) return denominator();
            // otherwise construct an IntegerType which preserves the MathContext of this
            return new IntegerImpl(denominator, exact) {
                @Override
                public MathContext getMathContext() {
                    return mctx;
                }
            };
        } else if (numerator.equals(BigInteger.ONE.negate())) {
            if (mctx.getPrecision() == 0) return denominator().negate();
            return new IntegerImpl(denominator.negate(), exact) {
                @Override
                public MathContext getMathContext() {
                    return mctx;
                }
            };
        }
        final RationalImpl inverse = new RationalImpl(denominator, numerator, exact);
        inverse.setMathContext(mctx);
        return inverse;
    }

    /**
     * This implementation of square root relies on the identity
     * sqrt(a/b) = sqrt(a)/sqrt(b). The result will only be exact
     * if the numerator and denominator are both perfect squares.
     *
     * @return the square root of this fraction
     * @throws ArithmeticException if type coercion fails during calculation
     * @see IntegerImpl#sqrt()
     */
    @Override
    public Numeric sqrt() {
        final RationalType reduced = this.reduce();
        if (reduced.numerator().isPerfectSquare() && reduced.denominator().isPerfectSquare()) {
            IntegerType numroot = reduced.numerator().sqrt();
            IntegerType denomroot = reduced.denominator().sqrt();
            return new RationalImpl(numroot, denomroot, mctx);
        } else if (MathUtils.useBuiltInOperations()) {
            final BigDecimal intermediate = this.asBigDecimal().abs().sqrt(mctx);
            if (sign() == Sign.NEGATIVE) {
                final RealType zero = new RealImpl(BigDecimal.ZERO, mctx);
                return new ComplexRectImpl(zero, new RealImpl(intermediate, mctx, false));
            }
            return new RealImpl(intermediate, mctx, false);
        }
        // fall through to the general algorithm
        try {
            RealType realNum = (RealType) reduced.numerator().coerceTo(RealType.class);
            RealType realDenom = (RealType) reduced.denominator().coerceTo(RealType.class);
            OptionalOperations.setMathContext(realNum, mctx);
            OptionalOperations.setMathContext(realDenom, mctx);
            return realNum.sqrt().divide(realDenom.sqrt());
        } catch (CoercionException e) {
            throw new ArithmeticException("Exception thrown while taking sqrt of " + this);
        }
    }
    
    @Override
    public boolean equals(Object other) {
        if (other instanceof Zero) {
            return this.isExact() == ((Zero) other).isExact() && this.numerator.equals(BigInteger.ZERO);
        } else if (other instanceof One) {
            return this.isExact() && this.numerator.equals(this.denominator);
        }
        // if it isn't one of the above special constants, round up the usual suspects
        if (other instanceof RealType) {
            final RealType that = (RealType) other;
            if (this.isExact() != that.isExact()) return false;

            return this.asBigDecimal().compareTo(that.asBigDecimal()) == 0;
        } else if (other instanceof RationalType) {
            final RationalType that = (RationalType) other;
            if (this.isExact() != that.isExact()) return false;
            // the following test is more "correct" but is slower
            if (RationalType.reduceForEqualityTest()) {
                if (this.isReducible()) {
                    return this.reduce().equals(that);
                }
                if (that.isReducible()) {
                    return this.equals(that.reduce());
                }
            }
            // ... and this is the faster "good enough" test
            return numerator.equals(that.numerator().asBigInteger()) &&
                    denominator.equals(that.denominator().asBigInteger());
        } else if (other instanceof Numeric) {
            final Numeric that = (Numeric) other;
            if (that.isCoercibleTo(RationalType.class)) {
                if (this.isExact() != that.isExact()) return false;

                try {
                    final RationalType val = (RationalType) that.coerceTo(RationalType.class);
                    return this.equals(val);
                } catch (CoercionException e) {
                    throw new IllegalStateException("Failed to coerce to RationalType after test for coercibility", e);
                }
            }
        }
        return false;
    }

    @Override
    public boolean isReducible() {
        BigInteger gcd = numerator.gcd(denominator);
        return gcd.compareTo(BigInteger.ONE) > 0;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 47 * hash + (this.exact ? 1 : 0);
        hash = 47 * hash + Objects.hashCode(this.numerator);
        hash = 47 * hash + Objects.hashCode(this.denominator);
        return hash;
    }

    @Override
    public int compareTo(RationalType o) {
        BigInteger lhs = numerator.multiply(o.denominator().asBigInteger());
        BigInteger rhs = o.numerator().asBigInteger().multiply(denominator);
        return lhs.compareTo(rhs);
    }

    @Override
    public Sign sign() {
        // the denominator is always positive; numerator contains sign information
        return Sign.fromValue(numerator);
    }

    @Override
    public MathContext getMathContext() {
        return mctx;
    }
    
    @Override public String toString() {
        StringBuilder buf = new StringBuilder();
        // U+2044 FRACTION SLASH will render better than /
        buf.append(numerator).append('\u2044').append(denominator);
        return buf.toString();
    }

    @Override
    public IntegerType floor() {
        switch (this.sign()) {
            case POSITIVE:
                return new IntegerImpl(numerator.divide(denominator));
            case NEGATIVE:
                final BigInteger[] result = numerator.divideAndRemainder(denominator);
                if (result[1].equals(BigInteger.ZERO)) {
                    return new IntegerImpl(result[0]);
                } else {
                    return new IntegerImpl(result[0].subtract(BigInteger.ONE));
                }
            default:
                return new IntegerImpl(BigInteger.ZERO);
        }
    }

    @Override
    public IntegerType ceil() {
        switch (this.sign()) {
            case POSITIVE:
                final BigInteger[] result = numerator.divideAndRemainder(denominator);
                if (result[1].equals(BigInteger.ZERO)) {
                    return new IntegerImpl(result[0]);
                } else {
                    return new IntegerImpl(result[0].add(BigInteger.ONE));
                }
            case NEGATIVE:
                return new IntegerImpl(numerator.divide(denominator));
            default:
                return new IntegerImpl(BigInteger.ZERO);
        }
    }

    /*
    Groovy methods implemented below.
     */

    @Override
    public Numeric power(Numeric operand) {
        if (Zero.isZero(operand)) return One.getInstance(getMathContext());
        if (One.isUnity(operand)) return this;
        if (numerator.equals(BigInteger.ZERO)) return ExactZero.getInstance(getMathContext());
        if (MathUtils.isInfinity(operand, Sign.NEGATIVE)) {
            return this.sign() == Sign.POSITIVE ? PosZero.getInstance(getMathContext()) : NegZero.getInstance(getMathContext());
        } else if (MathUtils.isInfinity(operand, Sign.POSITIVE)) {
            if (denominator.compareTo(numerator.abs()) > 0) {
                if (this.sign() == Sign.POSITIVE) return PosZero.getInstance(getMathContext());
                else throw new ArithmeticException("Negative rational raised to infinity does not converge");
            }
            return RealInfinity.getInstance(this.sign(), getMathContext());
        }
        if (operand.isCoercibleTo(IntegerType.class)) {
            try {
                IntegerType exponent = (IntegerType) operand.coerceTo(IntegerType.class);
                final int n = exponent.asBigInteger().intValueExact();
                final RationalImpl result;
                if (n < 0) {
                    result = new RationalImpl(denominator.pow(-n), numerator.pow(-n), exact);
                } else {
                    result = new RationalImpl(numerator.pow(n), denominator.pow(n), exact);
                }
                result.setMathContext(mctx);
                return result;
            } catch (CoercionException e) {
                throw new ArithmeticException("Unable to convert " + operand);
            }
        }
        final RealType converted = new RealImpl(asBigDecimal(), getMathContext(), isExact());
        if (operand instanceof ComplexType) return MathUtils.generalizedExponent(converted, (ComplexType) operand, getMathContext());
        return MathUtils.generalizedExponent(converted, operand, getMathContext());
    }
}
