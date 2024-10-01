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
import tungsten.types.exceptions.CoercionException;
import tungsten.types.numerics.*;
import tungsten.types.util.ClassTools;
import tungsten.types.util.MathUtils;
import tungsten.types.util.OptionalOperations;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Abstract implementation of the number 1 (one, or unity).
 * Note that this is not exactly a singleton implementation &mdash;
 * one instance exists for each MathContext in use.
 * Note that {@link #equals(Object) } may be inconsistent
 * with {@link #hashCode() }.
 *
 * @author Robert Poole <a href="mailto:Tarquin.AZ@gmail.com">Tarquin.AZ@gmail.com</a>
 */
@Constant(name = "one", representation = "1")
public class One implements Numeric, Comparable<Numeric> {
    private final MathContext mctx;
    
    private One(MathContext mctx) {
        this.mctx = mctx;
    }
    
    private static final Map<MathContext, One> instanceMap = new HashMap<>();
    private static final Lock instanceLock = new ReentrantLock();

    public static Numeric getInstance(MathContext mctx) {
        instanceLock.lock();
        try {
            One instance = instanceMap.get(mctx);
            if (instance == null) {
                instance = new One(mctx);
                instanceMap.put(mctx, instance);
            }
            return instance;
        } finally {
            instanceLock.unlock();
        }
    }

    @Override
    public boolean isExact() {
        return true;
    }

    @Override
    public boolean isCoercibleTo(Class<? extends Numeric> numtype) {
        return true;
    }

    private final IntegerType INT_UNITY = new IntegerImpl(BigInteger.ONE) {
        @Override
        public MathContext getMathContext() {
            return One.this.mctx;
        }
    };

    @Override
    public Numeric coerceTo(Class<? extends Numeric> numtype) throws CoercionException {
        if (numtype == Numeric.class) return this;
        NumericHierarchy htype = NumericHierarchy.forNumericType(numtype);
        Numeric retval;
        switch (htype) {
            case INTEGER:
                retval = INT_UNITY;
                break;
            case RATIONAL:
                retval = new RationalImpl(INT_UNITY);
                OptionalOperations.setMathContext(retval, mctx); // just to be sure
                break;
            case REAL:
                retval = obtainRealUnity();
                break;
            case COMPLEX:
                retval = new ComplexRectImpl(obtainRealUnity(), obtainRealZero());
                break;
            default:
                throw new CoercionException("Cannot coerce unity to expected type", One.class, numtype);
        }
        return retval;
    }
    
    private RealType obtainRealUnity() {
        return new RealImpl(BigDecimal.ONE, mctx);
    }
    
    private RealType obtainRealZero() {
        return new RealImpl(BigDecimal.ZERO, mctx);
    }

    @SuppressWarnings("unchecked")
    @Override
    public One magnitude() {
        return this;
    }

    @Override
    public Numeric negate() {
        return new IntegerImpl(BigInteger.ONE.negate()) {
            @Override
            public IntegerType negate() {
                return INT_UNITY;
            }

            @Override
            public MathContext getMathContext() {
                return One.this.mctx;
            }
        }; // was: INT_UNITY.negate();
    }
    
    private boolean isArgumentRealOrCplx(Numeric argument) {
        if (ClassTools.isAbstractType(argument)) return false;
        return NumericHierarchy.forNumericType(argument.getClass()).compareTo(NumericHierarchy.RATIONAL) > 0;
    }

    @Override
    public Numeric add(Numeric addend) {
        if (Zero.isZero(addend)) return this;
        if (isArgumentRealOrCplx(addend)) {
            if (addend instanceof RealType) {
                // small optimization
                RealType that = (RealType) addend;
                return new RealImpl(BigDecimal.ONE.add(that.asBigDecimal(), mctx), mctx, that.isExact());
            }
            RealType unity = obtainRealUnity();
            return unity.add(addend);
        }
        return addend.add(INT_UNITY);
    }

    @Override
    public Numeric subtract(Numeric subtrahend) {
        if (isUnity(subtrahend)) return ExactZero.getInstance(mctx);
        if (Zero.isZero(subtrahend)) return this;
        if (isArgumentRealOrCplx(subtrahend)) {
            if (subtrahend instanceof RealType) {
                // small optimization
                RealType that = (RealType) subtrahend;
                return new RealImpl(BigDecimal.ONE.subtract(that.asBigDecimal(), mctx), mctx, that.isExact());
            }
            RealType unity = obtainRealUnity();
            return unity.subtract(subtrahend);
        }
        return subtrahend.negate().add(INT_UNITY);
    }

    @Override
    public Numeric multiply(Numeric multiplier) {
        return multiplier;
    }

    @Override
    public Numeric divide(Numeric divisor) {
        if (ComplexType.isExtendedEnabled()) {
            if (divisor instanceof PointAtInfinity) {
                return ExactZero.getInstance(mctx);
            }
            if (Zero.isZero(divisor)) {
                return PointAtInfinity.getInstance();
            }
        }
        if (isUnity(divisor)) return this;
        return divisor.inverse();
    }

    @Override
    public Numeric inverse() {
        return this;
    }

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
     * <li>is exact, and</li>
     * <li>has a numeric value equivalent to unity (1),</li></ul>
     * then it is considered equal.
     * @param o the value to compare
     * @return true if equal, false otherwise
     */
    @Override
    public boolean equals(Object o) {
        if (o instanceof One) return true;
        if (o instanceof Numeric) {
            final Numeric that = (Numeric) o;
            
            if (!that.isExact()) return false;
            Class<? extends Numeric> clazz = that.getClass();
            try {
                Numeric temp = this.coerceTo(clazz);
                return temp.equals(o);
            } catch (CoercionException ex) {
                Logger.getLogger(One.class.getName()).log(Level.SEVERE, "Exception during test for equality with " + o, ex);
            }
        }
        return false;
    }

    /**
     * Convenience method to determine if a {{@link Numeric}} has a value of one (unity).
     * Note that this does not check exactness or significant digits, only the value.
     * The intention is to consolidate tests for &ldquo;one-ness&rdquo; here rather than spreading
     * them throughout the code base.  These checks are guaranteed to be faster than
     * coercing an instance of One to the target type and then testing for equality.
     *
     * @param val the value to test
     * @return true if the value is 1, false otherwise
     */
    public static boolean isUnity(Numeric val) {
        if (val instanceof One) return true;
        if (val != null) {
            NumericHierarchy htype = NumericHierarchy.forNumericType(val.getClass());
            if (htype == null) return false;
            switch (htype) {
                case INTEGER:
                    return ((IntegerType) val).asBigInteger().equals(BigInteger.ONE);
                case RATIONAL:
                    final RationalType rational = (RationalType) val;
                    return rational.numerator().equals(rational.denominator());
                case REAL:
                    return ((RealType) val).asBigDecimal().compareTo(BigDecimal.ONE) == 0;
                case COMPLEX:
                    final ComplexType that = (ComplexType) val;
                    return isUnity(that.real()) && Zero.isZero(that.imaginary());
            }
        }
        return false;
    }
    
    @Override
    public int hashCode() { return 1; }
    
    @Override
    public String toString() { return "1"; }

    @Override
    public int compareTo(Numeric o) {
        if (o instanceof Zero) return 1;
        if (o instanceof One) return 0;
        if (MathUtils.isInfinity(o, Sign.POSITIVE)) return -1;
        if (MathUtils.isInfinity(o, Sign.NEGATIVE)) return 1;
        if (o instanceof Comparable) {
            try {
                Comparable<Numeric> that = (Comparable<Numeric>) o;
                return -that.compareTo(this.coerceTo(o.getClass()));
            } catch (CoercionException ex) {
                Logger.getLogger(One.class.getName()).log(Level.SEVERE, "Exception during comparison with " + o, ex);
            }
        }
        throw new IllegalArgumentException("Non-comparable value of type " + o.getClass().getTypeName());
    }

    /*
    Groovy methods below.
     */
    @Override
    public Numeric power(Numeric operand) {
        return this;
    }
}
