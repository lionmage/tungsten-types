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
import tungsten.types.annotations.Constant;
import tungsten.types.annotations.Polar;
import tungsten.types.exceptions.CoercionException;
import tungsten.types.numerics.*;
import tungsten.types.util.MathUtils;
import tungsten.types.util.OptionalOperations;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A universal representation of zero (0).
 * Note that this is not exactly a singleton implementation &mdash;
 * one instance exists for each {@link MathContext} in use.
 * Note that {@link #equals(Object) } may be inconsistent
 * with {@link #hashCode() }.
 *
 * @author Robert Poole, <a href="mailto:Tarquin.AZ@gmail.com">Tarquin.AZ@gmail.com</a>
 */
@Constant(name = "zero", representation = "0")
public abstract class Zero implements Numeric, Comparable<Numeric> {
    protected final MathContext mctx;
    
    protected Zero(MathContext mctx) {
        this.mctx = mctx;
    }
    
    @Override
    public abstract boolean isExact();

    @Override
    public boolean isCoercibleTo(Class<? extends Numeric> numtype) {
        return true;
    }
    
    private final IntegerType INT_ZERO = new IntegerImpl(BigInteger.ZERO) {
        @Override
        public MathContext getMathContext() {
            return Zero.this.mctx;
        }
    };

    @Override
    public Numeric coerceTo(Class<? extends Numeric> numtype) throws CoercionException {
        if (numtype == Numeric.class) return this;
        NumericHierarchy htype = NumericHierarchy.forNumericType(numtype);
        Numeric retval;
        switch (htype) {
            case INTEGER:
                retval = INT_ZERO;
                break;
            case RATIONAL:
                retval = new RationalImpl(BigInteger.ZERO, BigInteger.ONE, isExact());
                OptionalOperations.setMathContext(retval, mctx);
                break;
            case REAL:
                retval = obtainRealZero();
                break;
            case COMPLEX:
                retval = new ComplexRectImpl(obtainRealZero(), obtainRealZero());
                break;
            default:
                throw new CoercionException("Cannot coerce zero to expected type", Zero.class, numtype);
        }
        return retval;
    }
    
    protected RealType obtainRealZero() {
        return new RealImpl(BigDecimal.ZERO, mctx, isExact());
    }

    @SuppressWarnings("unchecked")
    @Override
    public Zero magnitude() {
        return this;
    }

    @Override
    public Numeric negate() {
        return this;
    }

    @Override
    public Numeric add(Numeric addend) {
        return addend;
    }

    @Override
    public Numeric subtract(Numeric subtrahend) {
        if (subtrahend instanceof PointAtInfinity && ComplexType.isExtendedEnabled()) {
            return PointAtInfinity.getInstance();
        }
        return subtrahend.negate();
    }

    @Override
    public Numeric multiply(Numeric multiplier) {
        if (multiplier instanceof PointAtInfinity && ComplexType.isExtendedEnabled()) {
            throw new ArithmeticException("0 \u22C5 ∞ is undefined");
        }
        return this;
    }

    @Override
    public Numeric divide(Numeric divisor) {
        if (Zero.isZero(divisor)) {
            if (ComplexType.isExtendedEnabled()) {
                throw new ArithmeticException("0/0 is undefined");
            }

            // otherwise, make a best guess as to what we should give back
            Sign aggSign = this.sign();  // the aggregate sign of the result
            if (aggSign == Sign.NEGATIVE && OptionalOperations.sign(divisor) == Sign.NEGATIVE) {
                aggSign = Sign.POSITIVE;
            } else if (aggSign == Sign.POSITIVE && OptionalOperations.sign(divisor) == Sign.NEGATIVE) {
                aggSign = Sign.NEGATIVE;
            }
            switch (aggSign) {
                case POSITIVE:
                    return PosInfinity.getInstance(mctx);
                case NEGATIVE:
                    return NegInfinity.getInstance(mctx);
                case ZERO:
                    throw new ArithmeticException("0/0 cannot be interpreted");
            }
        }
        return this;
    }

    @Override
    public Numeric inverse() {
        if (ComplexType.isExtendedEnabled()) return PointAtInfinity.getInstance();
        throw new ArithmeticException("Cannot divide by zero");
    }

    public abstract Sign sign();

    @Override
    public Numeric sqrt() {
        return this;
    }

    @Override
    public MathContext getMathContext() {
        return mctx;
    }
    
    /**
     * Test for equality with a given value.  If the given value is:
     * <ul><li>an implementation of {@link Numeric}</li>
     * <li>matches the exactness of {@code this}, and</li>
     * <li>has a numeric value equivalent to zero (0),</li></ul>
     * then it is considered equal.
     * @param o the value to compare
     * @return true if the argument is equivalent to {@code this}, false otherwise
     */
    @Override
    public boolean equals(Object o) {
        if (o instanceof Zero) {
            final Zero that = (Zero) o;
            return this.sign() == that.sign() && this.isExact() == that.isExact();
        }
        if (o instanceof Numeric) {
            final Numeric that = (Numeric) o;
            
            if (this.isExact() != that.isExact()) return false;
            final Class<? extends Numeric> clazz = that.getClass();
            try {
                Numeric temp = this.coerceTo(clazz);
                return temp.equals(that);
            } catch (CoercionException ex) {
                Logger.getLogger(Zero.class.getName()).log(Level.SEVERE, "Exception during test for equality with " + o, ex);
            }
        }
        return false;
    }

    /**
     * Convenience method to determine if a {@link Numeric} has a value of zero.
     * Note that this does not check exactness or significant digits, only the value.
     * The intention is to consolidate tests for &ldquo;zero-ness&rdquo; here rather than spreading
     * them throughout the code base.  These checks are guaranteed to be faster than
     * coercing an instance of {@code Zero} to the target type and then testing for equality.
     *
     * @param val the value to test
     * @return true if the value is 0, false otherwise
     */
    public static boolean isZero(Numeric val) {
        if (val instanceof Zero) return true;
        if (val != null) {
            NumericHierarchy htype = NumericHierarchy.forNumericType(val.getClass());
            // if htype is null, we are dealing with some other direct subtype of Numeric
            // that is not zero
            if (htype == null) return false;
            switch (htype) {
                case INTEGER:
                    return ((IntegerType) val).asBigInteger().equals(BigInteger.ZERO);
                case RATIONAL:
                    return isZero(((RationalType) val).numerator());
                case REAL:
                    if (val instanceof ContinuedFraction) {
                        ContinuedFraction cf = (ContinuedFraction) val;
                        return cf.terms() == 1L && cf.termAt(0L) == 0L;
                    }
                    return ((RealType) val).asBigDecimal().compareTo(BigDecimal.ZERO) == 0;
                case COMPLEX:
                    final ComplexType that = (ComplexType) val;
                    if (that.getClass().isAnnotationPresent(Polar.class)) {
                        return isZero(that.magnitude());
                    }
                    return isZero(that.real()) && isZero(that.imaginary());
            }
        }
        return false;
    }
    
    @Override
    public int hashCode() { return 0; }
    
    @Override
    public String toString() { return "0"; }

    @Override
    public int compareTo(Numeric o) {
        if (o instanceof One) return -1;
        if (MathUtils.isInfinity(o, Sign.POSITIVE)) return -1;
        if (MathUtils.isInfinity(o, Sign.NEGATIVE)) return 1;
        if (o instanceof Comparable) {
            try {
                Comparable<Numeric> that = (Comparable<Numeric>) o;
                return -that.compareTo(this.coerceTo(o.getClass()));
            } catch (CoercionException ex) {
                Logger.getLogger(Zero.class.getName()).log(Level.SEVERE, "Exception during comparison with " + o, ex);
            }
        }
        throw new IllegalArgumentException("Non-comparable value of type " + o.getClass().getTypeName());
    }

    /*
     Groovy methods follow.
     */
    public Numeric power(Numeric operand) {
        if (isZero(operand)) {
            return One.getInstance(mctx);
        } else if (OptionalOperations.sign(operand) == Sign.NEGATIVE) {
            throw new ArithmeticException("Division by zero");
        }
        return this;
    }
}
