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
import tungsten.types.Set;
import tungsten.types.exceptions.CoercionException;
import tungsten.types.numerics.*;
import tungsten.types.set.impl.NumericSet;
import tungsten.types.util.ClassTools;
import tungsten.types.util.MathUtils;
import tungsten.types.util.UnicodeTextEffects;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * An implementation of {@link RealType}, representing real numbers.
 *
 * @author Robert Poole, <a href="mailto:Tarquin.AZ@gmail.com">Tarquin.AZ@gmail.com</a>
 */
public class RealImpl implements RealType {
    private boolean irrational = false;
    private boolean exact = true;
    private final BigDecimal val;
    private MathContext mctx = MathContext.UNLIMITED;
    
    public RealImpl(BigDecimal init) {
        val = init;
    }
    
    public RealImpl(BigDecimal init, boolean exact) {
        this(init);
        this.exact = exact;
    }

    public RealImpl(BigDecimal init, MathContext mctx) {
        this(init);
        this.mctx = mctx;
    }

    public RealImpl(BigDecimal init, MathContext mctx, boolean exact) {
        this(init, exact);
        this.mctx = mctx;
    }

    public RealImpl(String representation) {
        val = new BigDecimal(UnicodeTextEffects.sanitizeDecimal(representation));
    }
    
    public RealImpl(String representation, boolean exact) {
        this(representation);
        this.exact = exact;
    }

    public RealImpl(String representation, MathContext ctx) {
        this(representation);
        this.mctx = ctx;
    }
    
    /**
     * Convenience constructor to convert a rational to a real.
     * @param init the rational value to convert
     */
    public RealImpl(RationalType init) {
        this(init.asBigDecimal(), init.isExact());
        this.mctx = init.getMathContext();
        irrational = false;
    }
    
    public RealImpl(RationalType init, MathContext mctx) {
        if (mctx.getPrecision() > init.getMathContext().getPrecision()) {
            init = new RationalImpl(init.numerator(), init.denominator(), mctx);
        }
        this.val = init.asBigDecimal();
        this.exact = init.isExact();
        this.irrational = false;
        this.mctx = mctx;
    }
    
    /**
     * Convenience constructor to convert an integer to a real.
     * @param init the integer value to convert
     */
    public RealImpl(IntegerType init) {
        val = new BigDecimal(init.asBigInteger());
        exact = init.isExact();
        irrational = false;
        mctx = init.getMathContext();  // the default for integers is better than MathContext.UNLIMITED
    }

    public RealImpl(IntegerType n, MathContext ctx) {
        val = new BigDecimal(n.asBigInteger());
        exact = n.isExact();
        irrational = false;
        mctx = ctx;
    }

    public void setIrrational(boolean irrational) {
        if (irrational && this.exact) {
            throw new IllegalStateException("There cannot be an exact representation of an irrational number");
        }
        this.irrational = irrational;
    }
    
    public final void setMathContext(MathContext mctx) {
        if (mctx == null) {
            throw new IllegalArgumentException("MathContext must not be null");
        }
        this.mctx = mctx;
    }

    @Override
    public boolean isIrrational() {
        return irrational;
    }

    @Override
    public RealType magnitude() {
        return new RealImpl(val.abs(mctx), mctx);
    }

    @Override
    public RealType negate() {
        return new RealImpl(val.negate(mctx), mctx, exact);
    }

    @Override
    public BigDecimal asBigDecimal() {
        return val.round(mctx);
    }

    @Override
    public Sign sign() {
        return Sign.fromValue(val);
    }

    @Override
    public boolean isExact() {
        return exact;
    }

    @Override
    public boolean isCoercibleTo(Class<? extends Numeric> numtype) {
        NumericHierarchy htype = NumericHierarchy.forNumericType(numtype);
        if (htype == null) return numtype == Numeric.class;
        switch (htype) {
            case COMPLEX:
            case REAL:
                return true;
            case INTEGER:
                return this.isIntegralValue();
            case RATIONAL:
                return !this.isIrrational();
            default:
                // some unknown type, return false by default
                return false;
        }
    }

    @Override
    public Numeric coerceTo(Class<? extends Numeric> numtype) throws CoercionException {
        if (numtype == Numeric.class) return this;
        NumericHierarchy htype = NumericHierarchy.forNumericType(numtype);
        switch (htype) {
            case REAL:
                return this;
            case COMPLEX:
                final RealType zero = (RealType) ExactZero.getInstance(mctx).coerceTo(RealType.class);
                return new ComplexRectImpl(this, zero, exact);
            case RATIONAL:
                if (!this.isIrrational()) {
                    return rationalize();
                }
                break;
            case INTEGER:
                if (this.isIntegralValue()) {
                    return new IntegerImpl(val.toBigIntegerExact(), exact);
                }
                break;
        }
        throw new CoercionException("Failed to coerce real value", this.getClass(), numtype);
    }
    
    protected RationalType rationalize() {
        if (isIntegralValue()) {
            final RationalImpl result = new RationalImpl(val.toBigIntegerExact(), BigInteger.ONE, exact);
            result.setMathContext(mctx);
            return result;
        }
        final BigDecimal stripped = val.stripTrailingZeros();
        var num = new IntegerImpl(stripped.unscaledValue(), exact);
        var denom = new IntegerImpl(BigInteger.TEN.pow(stripped.scale()));
        RationalType ratl = new RationalImpl(num, denom, mctx);
        return ratl.reduce();
    }

    @Override
    public Numeric add(Numeric addend) {
        final boolean exactness = exact && addend.isExact();
        if (addend instanceof RealType) {
            final RealType that = (RealType) addend;
            final RealImpl result = new RealImpl(val.add(that.asBigDecimal(), mctx), mctx, exactness);
            result.setIrrational(irrational || that.isIrrational());
            return result;
        } else if (addend instanceof RationalType) {
            final RationalType that = (RationalType) addend;
            final RealImpl result = new RealImpl(val.add(that.asBigDecimal(), mctx), mctx, exactness);
            result.setIrrational(irrational);
            return result;
        } else if (addend instanceof IntegerType) {
            final IntegerType that = (IntegerType) addend;
            BigDecimal sum = val.add(new BigDecimal(that.asBigInteger(), mctx));
            final RealImpl result = new RealImpl(sum, mctx, exactness);
            result.setIrrational(irrational);
            return result;
        } else {
            Class<?> iface = ClassTools.getInterfaceTypeFor(addend.getClass());
            if (iface == Numeric.class) {
                // to avoid infinite recursion
                return addend.add(this);
            }
            // if we got here, addend is probably something like ComplexType
            try {
                return this.coerceTo((Class<? extends Numeric>) iface).add(addend);
            } catch (CoercionException ex) {
                Logger.getLogger(RealImpl.class.getName()).log(Level.SEVERE,
                        "Failed to coerce type during real add.", ex);
            }
        }
        throw new UnsupportedOperationException("Addition operation unsupported");
    }

    @Override
    public Numeric subtract(Numeric subtrahend) {
        if (subtrahend instanceof RealType) {
            // corner case where both operands are real, to avoid intermediate object creation
            RealType that = (RealType) subtrahend;
            RealImpl result = new RealImpl(val.subtract(that.asBigDecimal(), mctx), mctx, this.exact && that.isExact());
            result.setIrrational(irrational || that.isIrrational());
            return result;
        }
        return add(subtrahend.negate());
    }

    @Override
    public Numeric multiply(Numeric multiplier) {
        final boolean exactness = exact && multiplier.isExact();
        if (multiplier instanceof RealType) {
            RealType remult = (RealType) multiplier;
            final RealImpl result = new RealImpl(val.multiply(remult.asBigDecimal(), mctx), mctx, exactness);
            result.setIrrational(irrational || remult.isIrrational());
            return result;
        } else if (multiplier instanceof RationalType) {
            RationalType ratmult = (RationalType) multiplier;
            BigDecimal num = new BigDecimal(ratmult.numerator().asBigInteger());
            BigDecimal denom = new BigDecimal(ratmult.denominator().asBigInteger());
            final RealImpl result = new RealImpl(val.multiply(num).divide(denom, mctx), mctx, exactness);
            result.setIrrational(irrational);
            return result;
        } else if (multiplier instanceof IntegerType) {
            IntegerType intmult = (IntegerType) multiplier;
            if (isIntegralValue()) {
                return new IntegerImpl(val.toBigIntegerExact().multiply(intmult.asBigInteger()), exactness) {
                    @Override
                    public MathContext getMathContext() {
                        return mctx;
                    }
                };
            } else {
                BigDecimal decmult = new BigDecimal(intmult.asBigInteger());
                final RealImpl result = new RealImpl(val.multiply(decmult, mctx), mctx, exactness);
                result.setIrrational(irrational);
                return result;
            }
        } else {
            Class<?> iface = ClassTools.getInterfaceTypeFor(multiplier.getClass());
            if (iface == Numeric.class) {
                // to avoid infinite recursion
                return multiplier.multiply(this);
            }
            // if we got here, multiplier is probably something like ComplexType
            try {
                return this.coerceTo((Class<? extends Numeric>) iface).multiply(multiplier);
            } catch (CoercionException ex) {
                Logger.getLogger(RealImpl.class.getName()).log(Level.SEVERE, "Failed to coerce type during real multiply.", ex);
            }
        }
        throw new UnsupportedOperationException("Multiplication operation unsupported");
    }

    @Override
    public Numeric divide(Numeric divisor) {
        if (Zero.isZero(divisor)) throw new ArithmeticException("Division by 0");
        if (One.isUnity(divisor)) return this;
        final boolean exactness = exact && divisor.isExact();
        if (divisor instanceof RealType) {
            RealType redivisor = (RealType) divisor;
            final RealImpl result = new RealImpl(val.divide(redivisor.asBigDecimal(), mctx), mctx, exactness);
            result.setIrrational(this.irrational || redivisor.isIrrational());
            return result;
        } else if (divisor instanceof RationalType) {
            RationalType ratdivisor = (RationalType) divisor;
            if (isIntegralValue()) {
                IntegerType me = new IntegerImpl(val.toBigIntegerExact(), exactness) {
                    @Override
                    public MathContext getMathContext() {
                        return mctx;
                    }
                };
                return ratdivisor.inverse().multiply(me);
            }
            final RealImpl result = new RealImpl(val.divide(ratdivisor.asBigDecimal(), mctx), mctx, exactness);
            result.setIrrational(irrational);
            return result;
        } else if (divisor instanceof IntegerType) {
            IntegerType intdivisor = (IntegerType) divisor;
            if (isIntegralValue()) {
                final RationalImpl rationalValue = new RationalImpl(val.toBigIntegerExact(), intdivisor.asBigInteger(),
                    exactness);
                rationalValue.setMathContext(mctx);
                return rationalValue;
            } else {
                BigDecimal decdivisor = new BigDecimal(intdivisor.asBigInteger());
                final RealImpl result = new RealImpl(val.divide(decdivisor, mctx), mctx, exactness);
                result.setIrrational(irrational);
                return result;
            }
        } else {
            Class<?> iface = ClassTools.getInterfaceTypeFor(divisor.getClass());
            if (iface == Numeric.class) {
                // to avoid infinite recursion
                return divisor.inverse().multiply(this);
            }
            // if we got here, divisor is probably something like ComplexType
            try {
                return this.coerceTo((Class<? extends Numeric>) iface).divide(divisor);
            } catch (CoercionException ex) {
                Logger.getLogger(RealImpl.class.getName()).log(Level.SEVERE, "Failed to coerce type during real divide.", ex);
            }
        }
        throw new UnsupportedOperationException("Division operation unsupported");
    }

    @Override
    public Numeric inverse() {
        boolean exactness = isExact();
        BigDecimal bdinverse = BigDecimal.ONE.divide(val, mctx);
        if (exactness) {
            if (isIntegralValue()) {
                return new RationalImpl(BigInteger.ONE, val.toBigIntegerExact(), mctx);
            }
            // not an integer, so check to see if division results in an
            // infinite repeating sequence
            bdinverse = bdinverse.stripTrailingZeros();
            exactness = fractionalLengthDifference(bdinverse) != 0;
        }
        final RealImpl inv = new RealImpl(bdinverse, mctx, exactness);
        if (!exactness) inv.setIrrational(this.isIrrational());
        return inv;
    }

    @Override
    public Numeric sqrt() {
        if (isIntegralValue() && sign().compareTo(Sign.ZERO) >= 0) {
            IntegerImpl intval = new IntegerImpl(val.toBigIntegerExact(), exact) {
                @Override
                public MathContext getMathContext() {
                    return mctx; // use the MathContext of the source real
                }
            };
            if (intval.isPerfectSquare()) {
                return intval.sqrt();
            }
        }
        if (sign() == Sign.NEGATIVE) {
            try {
                final RealType zero = (RealType) ExactZero.getInstance(mctx).coerceTo(RealType.class);
                return new ComplexRectImpl(zero, (RealType) this.negate().sqrt().coerceTo(RealType.class));
            } catch (CoercionException e) {
                // we should NEVER get here
                throw new IllegalStateException(e);
            }
        }

        // otherwise, use the built-in square root
        BigDecimal principalRoot = val.sqrt(mctx);
        principalRoot = principalRoot.stripTrailingZeros(); // ensure this representation is as compact as possible
        final boolean atLimit = fractionalLengthDifference(principalRoot) == 0;
        RealImpl result = new RealImpl(principalRoot, mctx, exact && !atLimit);
        result.setIrrational(atLimit);
        return result;
    }

    @Override
    public Set<ComplexType> nthRoots(IntegerType n) {
        if (n.asBigInteger().equals(BigInteger.TWO)) {
            final Numeric root = this.sqrt();
            try {
                return new NumericSet(List.of(root, root.negate())).coerceTo(ComplexType.class);
            } catch (CoercionException e) {
                Logger.getLogger(RealImpl.class.getName()).log(Level.WARNING,
                        "While generating the complex set of square roots of " + this, e);
                Logger.getLogger(RealImpl.class.getName()).log(Level.WARNING,
                        "Unable to generate a Set<ComplexType> from {0} and {1}; falling through to polar code.",
                        new Object[] { root, root.negate() });
            }
        }
        final Pi pi = Pi.getInstance(mctx);
        final RealImpl zero = new RealImpl(BigDecimal.ZERO, mctx);
        ComplexPolarImpl polar = new ComplexPolarImpl(this.magnitude(), this.sign() == Sign.NEGATIVE ? pi : zero);
        return polar.nthRoots(n);
    }
    
    private int fractionalLengthDifference(BigDecimal num) {
        IntegerType nonFractionPart = new IntegerImpl(num.toBigInteger());
        int reducedDigitLength = mctx.getPrecision();
        if (!nonFractionPart.asBigInteger().equals(BigInteger.ZERO)) {
            reducedDigitLength -= (int) nonFractionPart.numberOfDigits();
        }
        return reducedDigitLength - num.scale();
    }
    
    protected boolean isIntegralValue() {
        if (val.scale() > 0) {
            return val.stripTrailingZeros().scale() <= 0;
        }
        return true;
    }
    
    @Override
    public boolean equals(Object o) {
        if (o instanceof Zero) {
            return ((Zero) o).isExact() == exact && this.asBigDecimal().compareTo(BigDecimal.ZERO) == 0;
        } else if (o instanceof One) {
            return exact && this.asBigDecimal().compareTo(BigDecimal.ONE) == 0;
        }
        if (o instanceof RealType) {
            RealType that = (RealType) o;
            if (this.isExact() != that.isExact()) return false;
            return val.compareTo(that.asBigDecimal()) == 0;
        } else if (this.isIntegralValue() && o instanceof IntegerType) {
            IntegerType that = (IntegerType) o;
            return val.toBigIntegerExact().equals(that.asBigInteger());
        } else if (!irrational && o instanceof RationalType) {
            return rationalize().equals(o);
        } else if (o instanceof ComplexType) {
            ComplexType that = (ComplexType) o;
            // it's cheaper to extract the real portion of a complex value
            // than to upconvert oneself for a comparison
            if (that.isCoercibleTo(RealType.class)) {
                return this.equals(that.real());
            }
        }
        return false;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 79 * hash + (this.irrational ? 1 : 0);
        hash = 79 * hash + (this.exact ? 1 : 0);
        hash = 79 * hash + Objects.hashCode(this.val);
        return hash;
    }
    
    @Override
    public String toString() {
        return val.toPlainString();
    }

    @Override
    public int compareTo(RealType o) {
        if (MathUtils.isInfinity(o, Sign.POSITIVE)) return -1;
        if (MathUtils.isInfinity(o, Sign.NEGATIVE)) return 1;
        return val.compareTo(o.asBigDecimal());
    }

    @Override
    public MathContext getMathContext() {
        return mctx;
    }

    @Override
    public IntegerType floor() {
        final BigInteger trunc = this.asBigDecimal().toBigInteger();
        switch (this.sign()) {
            case POSITIVE:
                return new IntegerImpl(trunc, exact);
            case NEGATIVE:
                return new IntegerImpl(this.isIntegralValue() ? trunc : trunc.subtract(BigInteger.ONE), exact);
            default:
                return new IntegerImpl(BigInteger.ZERO, exact);
        }
    }

    @Override
    public IntegerType ceil() {
        final BigInteger trunc = this.asBigDecimal().toBigInteger();
        switch (this.sign()) {
            case NEGATIVE:
                return new IntegerImpl(trunc, exact);
            case POSITIVE:
                return new IntegerImpl(this.isIntegralValue() ? trunc : trunc.add(BigInteger.ONE), exact);
            default:
                return new IntegerImpl(BigInteger.ZERO, exact);
        }
    }
}
