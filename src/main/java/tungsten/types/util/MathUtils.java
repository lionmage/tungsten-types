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
package tungsten.types.util;

import tungsten.types.Set;
import tungsten.types.Vector;
import tungsten.types.*;
import tungsten.types.annotations.Columnar;
import tungsten.types.annotations.Polar;
import tungsten.types.exceptions.CoercionException;
import tungsten.types.functions.UnaryFunction;
import tungsten.types.functions.impl.Exp;
import tungsten.types.functions.impl.NaturalLog;
import tungsten.types.functions.impl.Negate;
import tungsten.types.matrix.impl.*;
import tungsten.types.numerics.*;
import tungsten.types.numerics.impl.*;
import tungsten.types.set.impl.NumericSet;
import tungsten.types.vector.ColumnVector;
import tungsten.types.vector.RowVector;
import tungsten.types.vector.impl.*;

import java.lang.reflect.ParameterizedType;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
import java.util.stream.StreamSupport;

import static tungsten.types.Range.BoundType;

/**
 * A utility class to hold commonly used functions and algorithms.
 *
 * @author Robert Poole, <a href="mailto:tarquin@alum.mit.edu">MIT alumni e-mail</a> or <a href="mailto:Tarquin.AZ@gmail.com">Gmail</a>
 */
public class MathUtils {
    public static final String THETA = "\u03B8";
    /**
     * The {@link String} representing the System property that
     * governs whether internally-provided Java math operations
     * are to be preferred during calculation.
     */
    public static final String PREFER_INBUILT = "tungsten.types.numerics.MathUtils.prefer.native";
    private static final BigInteger TWO = BigInteger.valueOf(2L);
    
    private static final Map<Long, BigInteger> factorialCache = new HashMap<>();

    /**
     * Checks configuration and returns {@code true} if we should
     * prefer built-in operations (i.e., those supplied by Java) over
     * implementations supplied by Tungsten.  Typically, built-in
     * operations execute faster at the expense of lower accuracy.
     * For example, {@link BigDecimal#pow(int, MathContext)} returns
     * a result that is accurate to within 2 ULPs, whereas
     * {@link #computeIntegerExponent(RealType, long, MathContext)}
     * may give more accurate results at the cost of slower performance.
     * @return true if the system is configured to prefer Java-supplied
     *  operations, false otherwise
     * @see #PREFER_INBUILT
     */
    public static boolean useBuiltInOperations() {
        String value = System.getProperty(PREFER_INBUILT, "true");
        return Boolean.parseBoolean(value);
    }

    /**
     * Compute n! &mdash; the factorial of integer value n.
     * Note that this implementation uses caching of previously
     * computed values both for short-circuit evaluation
     * and for computing new values.  Caching for values
     * of n&nbsp;&gt;&nbsp;{@link Long#MAX_VALUE} is not
     * guaranteed.
     * @param n a non-negative integer value
     * @return the value of n!
     */
    public static IntegerType factorial(IntegerType n) {
        if (n.sign() == Sign.NEGATIVE) throw new IllegalArgumentException("Factorial undefined for " + n);
        if (n.asBigInteger().equals(BigInteger.ZERO) || n.asBigInteger().equals(BigInteger.ONE)) {
            return new IntegerImpl(BigInteger.ONE);
        } else if (getCacheFor(n) != null) {
            return new IntegerImpl(getCacheFor(n));
        }
        
        Long m = findMaxKeyUnder(n);
        
        BigInteger accum = m != null ? factorialCache.get(m) : BigInteger.ONE;
        BigInteger intermediate = n.asBigInteger();
        BigInteger bailout = m != null ? BigInteger.valueOf(m + 1L) : TWO;
        while (intermediate.compareTo(bailout) >= 0) {
            accum = accum.multiply(intermediate);
            intermediate = intermediate.subtract(BigInteger.ONE);
        }
        cacheFact(n, accum);
        return new IntegerImpl(accum);
    }

    /**
     * If there's a cached factorial value, find the highest key that is less
     * than n.
     * @param n the upper bound of our search
     * @return the highest cache key given the search parameter, or null
     *  if no key is found
     */
    private static Long findMaxKeyUnder(IntegerType n) {
        try {
            final long ncmp = n.asBigInteger().longValueExact();
            return factorialCache.keySet().parallelStream().filter(x -> x < ncmp).max(Long::compareTo).orElse(null);
        } catch (ArithmeticException e) {
            Logger.getLogger(MathUtils.class.getName()).log(Level.FINER, "Attempt to find a max key < n outside Long range.", e);
            // return the biggest key we can find since the given upper bound is too large for the cache
            return factorialCache.keySet().parallelStream().max(Long::compareTo).orElse(null);
        }
    }
    
    private static final BigInteger MAX_LONG = BigInteger.valueOf(Long.MAX_VALUE);
    
    private static void cacheFact(BigInteger n, BigInteger value) {
        // these bounds should prevent an ArithmeticException from being thrown
        // if not, we want to fail fast to catch the problem
        if (n.compareTo(TWO) >= 0 && n.compareTo(MAX_LONG) < 0) {
            Long key = n.longValueExact();
            
            if (!factorialCache.containsKey(key)) factorialCache.put(key, value);
        }
    }
    
    private static void cacheFact(IntegerType n, BigInteger value) {
        cacheFact(n.asBigInteger(), value);
    }
    
    private static BigInteger getCacheFor(BigInteger n) {
        try {
            return factorialCache.get(n.longValueExact());
        } catch (ArithmeticException e) {
            Logger.getLogger(MathUtils.class.getName()).log(Level.FINER, "Attempt to access cache of factorial value for n outside Long range.", e);
            return null; // this is the same as if we had a regular cache miss
        }
    }
    
    private static BigInteger getCacheFor(IntegerType n) {
        return getCacheFor(n.asBigInteger());
    }

    /**
     * Compute the binomial coefficient.
     * @param n the size of the set from which we are choosing
     * @param k the number of elements we are choosing from the set at a time
     * @return the binomial coefficient
     */
    public static IntegerType nChooseK(IntegerType n, IntegerType k) {
        // factorial() already checks for negative values, so we just need to check that k <= n
        if (k.compareTo(n) > 0) throw new IllegalArgumentException("k must be \u2265 n");
        try {
            return (IntegerType) factorial(n).divide(factorial(k).multiply(factorial((IntegerType) n.subtract(k)))).coerceTo(IntegerType.class);
        } catch (CoercionException e) {
            // the result should always be an integer, so this should never happen
            Logger.getLogger(MathUtils.class.getName()).log(Level.WARNING,
                    "A non-integer result was generated by nChooseK", e);
            throw new ArithmeticException("Received a non-integer result for n-choose-k");
        }
    }

    /**
     * A convenience method for computing n-choose-k when only {@code long}
     * values are available.
     * @param n the size of the set from which we are choosing
     * @param k the number of elements we are choosing from the set at a time
     * @return the binomial coefficient
     */
    public static IntegerType nChooseK(long n, long k) {
        IntegerType N = new IntegerImpl(BigInteger.valueOf(n));
        IntegerType K = new IntegerImpl(BigInteger.valueOf(k));
        return nChooseK(N, K);
    }

    /**
     * Round a value x to the given {@link MathContext}.
     * @param x   the real value to be rounded
     * @param ctx the {@link MathContext} to apply
     * @return the value x rounded
     */
    public static RealType round(RealType x, MathContext ctx) {
        BigDecimal value = x.asBigDecimal().round(ctx);
        return new RealImpl(value, ctx, false);
    }

    /**
     * Round the values of a {@link Vector<RealType>} to the
     * precision of the given {@link MathContext}.
     * @param x   a {@link Vector} with real element values
     * @param ctx the {@link MathContext} which provides the precision and {@link RoundingMode}
     * @return a new vector of rounded real values
     */
    public static RealVector round(Vector<RealType> x, MathContext ctx) {
        RealType[] elements = new RealType[(int) x.length()];
        for (long k = 0L; k < x.length(); k++) elements[(int) k] = round(x.elementAt(k), ctx);
        return new RealVector(elements, ctx);
    }

    /**
     * Generates a random real value that fits within the provided range.
     * @param range the {@link Range<RealType>} that specifies the lower and upper
     *              bounds of the value to be generated
     * @return a random number in the specified {@code range}
     */
    public static RealType random(Range<RealType> range) {
        MathContext ctx = inferMathContext(List.of(range.getLowerBound(), range.getUpperBound()));
        RealType span = (RealType) range.getUpperBound().subtract(range.getLowerBound());
        RealType randVal;
        do {
            randVal = new RealImpl(BigDecimal.valueOf(Math.random()), ctx, false);
        } while (!range.isLowerClosed() && Zero.isZero(randVal));  // exclude values from lower limit if needed
        return (RealType) range.getLowerBound().add(span.multiply(randVal));
    }

    /**
     * Round a value z to the given {@link MathContext}. This operation
     * is equivalent to performing a rounding operation on each of the
     * components of the complex value z.
     * @param z   the complex value to be rounded
     * @param ctx the {@link MathContext} to apply
     * @return the complex value z rounded
     */
    public static ComplexType round(ComplexType z, MathContext ctx) {
        if (z.getClass().isAnnotationPresent(Polar.class)) {
            return new ComplexPolarImpl(round(z.magnitude(), ctx), round(z.argument(), ctx), false);
        }
        return new ComplexRectImpl(round(z.real(), ctx), round(z.imaginary(), ctx), false);
    }
    
    public static RealType computeIntegerExponent(Numeric x, IntegerType n) {
        final RealType result;
        final BigInteger exponent = n.asBigInteger();
        final boolean exactness = x.isExact() && n.isExact();

        if (exponent.equals(BigInteger.ZERO)) {
            result = new RealImpl(BigDecimal.ONE, x.getMathContext(), exactness);
        } else {
            try {
                result = computeIntegerExponent((RealType) x.coerceTo(RealType.class), exponent.longValueExact());
                if (result.isExact() != exactness) {
                    Logger.getLogger(MathUtils.class.getName()).log(Level.INFO,
                            "Expected exactness of {0} but got {1} for calculating {2}{3}.",
                            new Object[] {exactness, result.isExact(), x, UnicodeTextEffects.numericSuperscript(n.asBigInteger().intValue())});
                }
            } catch (CoercionException ex) {
                Logger.getLogger(MathUtils.class.getName()).log(Level.SEVERE, "Failed to coerce argument to RealType.", ex);
                throw new ArithmeticException("While coercing argument to computeIntegerExponent: " + ex.getMessage());
            }
        }
        
        return result;
    }
    
    public static ComplexType computeIntegerExponent(ComplexType z, IntegerType n) {
        return computeIntegerExponent(z, n.asBigInteger().longValueExact(), z.getMathContext());
    }

    // This is the value of the maximum allowed integer exponent for
    // BigDecimal.pow(), as documented in the JDK.  This could be adjusted
    // lower in the future, but never higher.  (The absolute upper
    // limit would be the maximum value of int.)
    private static final long MAX_INT_FOR_EXPONENT = 999_999_999L;

    /**
     * Compute x<sup>n</sup>.
     * @param x the value to take the exponent of
     * @param n the integer exponent
     * @param mctx the {@link MathContext} for computing the exponent
     * @return x raised to the n<sup>th</sup> power
     */
    public static RealType computeIntegerExponent(RealType x, long n, MathContext mctx) {
        if (n == 0L) return new RealImpl(BigDecimal.ONE, mctx, x.isExact());
        if (n == 1L) return x;
        // if n falls within a certain integer range, delegate to BigDecimal.pow()
        if (useBuiltInOperations() && Math.abs(n) < MAX_INT_FOR_EXPONENT) {
            RealImpl real = new RealImpl(x.asBigDecimal().pow((int) n, mctx), mctx, x.isExact());
            real.setIrrational(x.isIrrational());
            return real;
        }
        try {
            if (n == -1L) {
                return (RealType) x.inverse().coerceTo(RealType.class);
            }

            // TODO figure out if we can get away with only 2 extra digits of precision
            MathContext compctx = new MathContext(mctx.getPrecision() + 4, mctx.getRoundingMode());
            Numeric intermediate = One.getInstance(compctx);
            Numeric factor = new RealImpl(x.magnitude().asBigDecimal(), compctx, x.isExact());
            long m = Math.abs(n);
            if (m % 2L == 1L) {  // handle the corner case of odd exponents
                intermediate = factor;
                m--;
            }
            while (m % 2L == 0L) {
                factor = factor.multiply(factor);
                m >>= 1;
            }
            for (long k = 0; k < m; k++) intermediate = intermediate.multiply(factor);
            if (n < 0L) intermediate = intermediate.inverse();
            // if |n| is odd, preserve original sign
            if (x.sign() == Sign.NEGATIVE && Math.abs(n) % 2L != 0L) intermediate = intermediate.negate();
            return round((RealType) intermediate.coerceTo(RealType.class), mctx);
        } catch (CoercionException ex) {
            Logger.getLogger(MathUtils.class.getName()).log(Level.SEVERE, "Unrecoverable exception thrown while computing integer exponent.", ex);
            throw new ArithmeticException("Failure to coerce result to RealType");
        }
    }

    /**
     * Compute z<sup>n</sup> for complex z.
     * @param z    the complex value to be raised to the given exponent
     * @param n    the exponent
     * @param mctx the {@link MathContext} to be used for any intermediate real values, which may not be
     *             reflected in the result
     * @return z raised to the n<sup>th</sup> power
     */
    public static ComplexType computeIntegerExponent(ComplexType z, long n, MathContext mctx) {
        if (n == 0L) return new ComplexRectImpl(new RealImpl(BigDecimal.ONE, mctx, z.isExact()));
        if (n == 1L) return z;
        if (n == -1L) {
            return z.inverse();
        }
        if (z.getClass().isAnnotationPresent(Polar.class)) {
            // computing powers of complex numbers in polar form is much faster and easier
            final IntegerImpl exponent = new IntegerImpl(BigInteger.valueOf(n));
            RealType modulus = computeIntegerExponent(z.magnitude(), exponent);
            RealType argument = (RealType) z.argument().multiply(exponent);
            return new ComplexPolarImpl(modulus, argument, z.isExact());
        }

        try {
            Numeric intermediate = One.getInstance(mctx);
            Numeric factor = z;
            long m = Math.abs(n);
            if (m % 2L == 1L) {  // odd exponents are a simple corner case
                intermediate = factor;
                m--;
            }
            while (m % 2L == 0L) {
                factor = factor.multiply(factor);
                m >>= 1L;
            }
            for (long k = 0L; k < m; k++) intermediate = intermediate.multiply(factor);
            if (n < 0L) intermediate = intermediate.inverse();
            return (ComplexType) intermediate.coerceTo(ComplexType.class);
        } catch (CoercionException ex) {
            Logger.getLogger(MathUtils.class.getName()).log(Level.SEVERE, "Unrecoverable exception thrown while computing integer exponent.", ex);
            throw new ArithmeticException("Failure to coerce result to ComplexType");
        }
    }
    
    /**
     * Compute x<sup>n</sup>. The {@link MathContext} is inferred from {@code x}.
     * @param x the value to take the exponent of
     * @param n the integer exponent
     * @return x raised to the n<sup>th</sup> power
     */
    public static RealType computeIntegerExponent(RealType x, long n) {
        return computeIntegerExponent(x, n, x.getMathContext());
    }
    
    private static final BigDecimal decTWO = BigDecimal.valueOf(2L);
    private static final Range<RealType> newtonRange = new Range<>(new RealImpl(BigDecimal.ZERO), new RealImpl(decTWO), BoundType.EXCLUSIVE);
    
    /**
     * Compute the natural logarithm, ln(x)
     * @param x the value for which to obtain the natural logarithm
     * @param mctx the {@link MathContext} to use for this operation
     * @return the natural logarithm of {@code x}
     */
    public static RealType ln(RealType x, MathContext mctx) {
        if (x.asBigDecimal().compareTo(BigDecimal.ONE) == 0) {
            try {
                return (RealType) ExactZero.getInstance(mctx).coerceTo(RealType.class);
            } catch (CoercionException ex) {
                // We should never get here!
                throw new IllegalStateException(ex);
            }
        }
        if (x.asBigDecimal().compareTo(BigDecimal.ZERO) <= 0) {
            if (x.asBigDecimal().compareTo(BigDecimal.ZERO) == 0) return RealInfinity.getInstance(Sign.NEGATIVE, mctx);
            throw new ArithmeticException("ln() is undefined for values < 0");
        }
        if (newtonRange.contains(x)) return lnNewton(x, mctx);
        
        if (x.asBigDecimal().compareTo(BigDecimal.TEN) > 0) {
            RealType mantissa = mantissa(x);
            IntegerType exponent = exponent(x);
            // use the identity ln(a*10^n) = ln(a) + n*ln(10)
            RealType ln10 = lnSeries(new RealImpl(BigDecimal.TEN), mctx);
            try {
                // We are mainly coercing here out of an abundance of caution, so if something does break, we log it well.
                return (RealType) ln(mantissa, mctx).add(ln10.multiply(exponent)).coerceTo(RealType.class);
            } catch (CoercionException ex) {
                Logger logger = Logger.getLogger(MathUtils.class.getName());
                logger.log(Level.SEVERE, "While computing ln(a\u2009×\u200910\u207F) = ln(a) + n\u2009×\u2009ln(10).", ex);
                logger.log(Level.INFO, "Attempted to decompose ln(x) for x\u2009=\u2009{0} with mantissa\u2009=\u2009{1} and exponent\u2009=\u2009{2}.",
                        new Object[] {x, mantissa, exponent});
                throw new IllegalStateException("Calculation of ln(a × 10\u207F) = ln(a) + n × ln(10) failed to generate a real value", ex);
            }
        }
        
        return lnSeries(x, mctx);
    }
    
    /**
     * Compute the natural logarithm, ln(x)
     * @param x the value for which to obtain the natural logarithm
     * @return the natural logarithm of {@code x}
     */
    public static RealType ln(RealType x) {
        return ln(x, x.getMathContext());
    }
    
    private static RealType lnNewton(RealType x, MathContext mctx) {
        Euler e = Euler.getInstance(mctx);
        BigDecimal xval = x.asBigDecimal();
        BigDecimal y0 = BigDecimal.ONE;
        BigDecimal y1;
        while (true) {
            final BigDecimal expval = e.exp(new RealImpl(y0, false)).asBigDecimal();
            
            BigDecimal num = xval.subtract(expval, mctx);
            BigDecimal denom = xval.add(expval, mctx);
            y1 = y0.add(decTWO.multiply(num.divide(denom, mctx), mctx), mctx);
            if (y0.compareTo(y1) == 0) break;
            
            y0 = y1;
        }
        final RealImpl result = new RealImpl(y0, false);
        result.setIrrational(true);
        result.setMathContext(mctx);
        return result;
    }
    
    private static RealType lnSeries(RealType x, MathContext mctx) {
        MathContext compctx = new MathContext(mctx.getPrecision() + 4, mctx.getRoundingMode());
        BigDecimal xfrac = x.asBigDecimal().subtract(BigDecimal.ONE, compctx).divide(x.asBigDecimal(), compctx);
        BigDecimal sum = BigDecimal.ZERO;
        for (int n = 1; n < mctx.getPrecision() * 17; n++) {
            sum = sum.add(computeNthTerm_ln(xfrac, n, compctx), compctx);
        }
        final RealImpl result = new RealImpl(sum.round(mctx), false);
        result.setIrrational(true);
        result.setMathContext(mctx);
        return result;
    }
    
    private static BigDecimal computeNthTerm_ln(BigDecimal frac, int n, MathContext mctx) {
        BigDecimal ninv = BigDecimal.ONE.divide(BigDecimal.valueOf(n), mctx);
        return ninv.multiply(computeIntegerExponent(new RealImpl(frac), n, mctx).asBigDecimal(), mctx);
    }
    
    /**
     * Compute the general logarithm, log<sub>b</sub>(x).
     * @param x the number for which we wish to take a logarithm
     * @param base the base of the logarithm
     * @param mctx the MathContext to use for the 
     * @return the logarithm of {@code x} in {@code base}
     */
    public static RealType log(RealType x, RealType base, MathContext mctx) {
        return (RealType) ln(x, mctx).divide(ln(base, mctx));
    }
    
    /**
     * Compute the general logarithm, log<sub>b</sub>(x).
     * The {@link MathContext} is inferred from the argument {@code x}.
     * @param x the number for which we wish to take a logarithm
     * @param base the base of the logarithm
     * @return the logarithm of {@code x} in {@code base}
     */
    public static RealType log(RealType x, RealType base) {
        return log(x, base, x.getMathContext());
    }
    
    /**
     * Computes the mantissa of a real value as expressed in scientific
     * notation, mantissa&nbsp;&times;&nbsp;10<sup>exponent</sup>.
     * @param x the real value
     * @return the mantissa of {@code x}
     */
    public static RealType mantissa(RealType x) {
        BigDecimal mantissa = x.asBigDecimal().scaleByPowerOfTen(x.asBigDecimal().scale() + 1 - x.asBigDecimal().precision());
        RealImpl result = new RealImpl(mantissa, x.isExact());
        result.setMathContext(x.getMathContext());
        return result;
    }
    
    /**
     * Computes the exponent of a real value as expressed in scientific
     * notation, mantissa&nbsp;&times;&nbsp;10<sup>exponent</sup>.
     * @param x the real value
     * @return the exponent of {@code x}
     */
    public static IntegerType exponent(RealType x) {
        int exponent = x.asBigDecimal().precision() - x.asBigDecimal().scale() - 1;
        return new IntegerImpl(BigInteger.valueOf(exponent));  // the exponent should always be exact
    }
    
    /**
     * Compute the general case of x<sup>y</sup>, where x is a real number
     * and y is anything generally coercible to a real (i.e., integer,
     * rational, or real values).
     * @param base the value to raise to a given power
     * @param exponent the power to which we want to raise {@code base}
     * @param mctx the {@link MathContext} to use for this calculation
     * @return the value of base<sup>exponent</sup>
     */
    public static RealType generalizedExponent(RealType base, Numeric exponent, MathContext mctx) {
        if (Zero.isZero(exponent)) {
            try {
                return (RealType) One.getInstance(mctx).coerceTo(RealType.class);
            } catch (CoercionException ex) {
                Logger.getLogger(MathUtils.class.getName()).log(Level.SEVERE,
                        "Could not obtain a real instance of One", ex);
                throw new IllegalStateException(ex);
            }
        } else if (One.isUnity(exponent)) {
            return base;
        }
        if (exponent instanceof RealInfinity) {
            if (base.sign() == Sign.POSITIVE) {
                switch (((RealInfinity) exponent).sign()) {
                    case NEGATIVE:
                        return new RealImpl(BigDecimal.ZERO, mctx);
                    case POSITIVE:
                        return RealInfinity.getInstance(Sign.POSITIVE, mctx);
                    default:
                        throw new IllegalStateException("Unknown state for " + exponent);
                }
            } else {
                if (((RealInfinity) exponent).sign() == Sign.NEGATIVE) {
                    return new RealImpl(BigDecimal.ZERO, mctx);
                }
                throw new ArithmeticException(base + UnicodeTextEffects.convertToSuperscript(exponent.toString()) + " does not converge");
            }
        } else if (exponent instanceof PosInfinity) {
            if (base.sign() == Sign.POSITIVE) return RealInfinity.getInstance(Sign.POSITIVE, mctx);
            throw new ArithmeticException(base + UnicodeTextEffects.convertToSuperscript(exponent.toString()) + " does not converge");
        } else if (exponent instanceof NegInfinity) {
            return new RealImpl(BigDecimal.ZERO, mctx);
        }
        NumericHierarchy htype = NumericHierarchy.forNumericType(exponent.getClass());
        switch (htype) {
            case INTEGER:
                long n = ((IntegerType) exponent).asBigInteger().longValueExact();
                return computeIntegerExponent(base, n, mctx);
            case REAL:
                if (exponent.isCoercibleTo(IntegerType.class)) {
                    try {
                        IntegerType integer = (IntegerType) exponent.coerceTo(IntegerType.class);
                        return generalizedExponent(base, integer, mctx);
                    } catch (CoercionException ex) {
                        Logger.getLogger(MathUtils.class.getName()).log(Level.SEVERE, "Failed to coerce real to integer.", ex);
                        throw new IllegalStateException("Failed type coercion after test for coercibility", ex);
                    }
                }
                // approximate with a rational
                try {
                    RationalType ratexponent = (RationalType) exponent.coerceTo(RationalType.class);
                    return generalizedExponent(base, ratexponent, mctx);
                } catch (CoercionException ex) {
                    // recover by using exponential identity, which is more costly than rational exponentiation
                    final Euler e = Euler.getInstance(mctx);
                    // use the identity x^y = e^(y * ln(x))
                    RealType arg = (RealType) ln(base).multiply(exponent);
                    return e.exp(arg);
                }
            case RATIONAL:
                // use the identity b^(u/v) = vth root of b^u
                RationalType ratexponent = (RationalType) exponent;
                final long n_num = ratexponent.numerator().asBigInteger().longValueExact();
                RealType intermediate = computeIntegerExponent(base, n_num, mctx);
                return nthRoot(intermediate, ratexponent.denominator(), mctx);
            default:
                throw new ArithmeticException("Currently generalizedExponent() has no support for exponents of type " + exponent.getClass().getTypeName());
        }
    }

    public static ComplexType generalizedExponent(RealType base, ComplexType exponent, MathContext mctx) {
        // this logic could not be folded into the generalizedExponent() method above without changing that method's return type
        // this method should be the equivalent of converting base to a ComplexType and calling the generalizedExponent()
        // method below, but this method should be faster (uses real-valued ln(), no exp()) and involves fewer temporary objects
        return new ComplexPolarImpl(generalizedExponent(base, exponent.real(), mctx), (RealType) ln(base, mctx).multiply(exponent.imaginary()));
    }

    public static ComplexType generalizedExponent(ComplexType base, Numeric exponent, MathContext mctx) {
        if (Zero.isZero(exponent)) {
            try {
                return (ComplexType) One.getInstance(mctx).coerceTo(ComplexType.class);
            } catch (CoercionException ex) {
                Logger.getLogger(MathUtils.class.getName()).log(Level.SEVERE,
                        "Could not obtain a complex instance of One", ex);
                throw new IllegalStateException(ex);
            }
        } else if (One.isUnity(exponent)) {
            return base;
        }
        NumericHierarchy htype = NumericHierarchy.forNumericType(exponent.getClass());
        switch (htype) {
            case INTEGER:
                long n = ((IntegerType) exponent).asBigInteger().longValueExact();
                return computeIntegerExponent(base, n, mctx);
            case REAL:
                if (exponent.isCoercibleTo(IntegerType.class)) {
                    try {
                        IntegerType integer = (IntegerType) exponent.coerceTo(IntegerType.class);
                        return generalizedExponent(base, integer, mctx);
                    } catch (CoercionException ex) {
                        Logger.getLogger(MathUtils.class.getName()).log(Level.SEVERE, "Failed to coerce real to integer.", ex);
                        throw new IllegalStateException("Failed type coercion after test for coercibility", ex);
                    }
                }
                // approximate with a rational
                try {
                    RationalType ratexponent = (RationalType) exponent.coerceTo(RationalType.class);
                    return generalizedExponent(base, ratexponent, mctx);
                } catch (CoercionException ex) {
                    // recover by using the exponential identity
                    final Euler e = Euler.getInstance(mctx);
                    ComplexType arg = (ComplexType) ln(base).multiply(exponent);
                    return e.exp(arg);
                }
            case RATIONAL:
                // use the identity b^(u/v) = vth root of b^u
                RationalType ratexponent = (RationalType) exponent;
                final long n_num = ratexponent.numerator().asBigInteger().longValueExact();
                ComplexType intermediate = computeIntegerExponent(base, n_num, mctx);
                RealType modulus = nthRoot(intermediate.magnitude(), ratexponent.denominator());
                RealType argument = (RealType) intermediate.argument().divide(ratexponent.denominator());
                return new ComplexPolarImpl(modulus, argument, false);
            case COMPLEX:
                final Euler e = Euler.getInstance(mctx);
                // use the identity z^w = e^(w⋅ln(z))
                ComplexType argForE = (ComplexType) ln(base).multiply(exponent);
                return e.exp(argForE);
            default:
                throw new ArithmeticException("Currently generalizedExponent() has no support for exponents of type " + exponent.getClass().getTypeName());
        }
    }


    /**
     * Compute the n<sup>th</sup> root of a real value a.  The result is the principal
     * root of the equation x<sup>n</sup>&nbsp;=&nbsp;a.  Note that the {@link MathContext}
     * is inferred from the argument {@code a}.
     * @param a the value for which we want to find a root
     * @param n the degree of the root
     * @return the {@code n}th root of {@code a}
     */
    public static RealType nthRoot(RealType a, IntegerType n) {
        return nthRoot(a, n, a.getMathContext());
    }
    
    /**
     * Compute the n<sup>th</sup> root of a real value a.  The result is the principal
     * root of the equation x<sup>n</sup>&nbsp;=&nbsp;a.  The {@link MathContext}
     * is explicitly supplied.
     * @param a the value for which we want to find a root
     * @param n the degree of the root
     * @param mctx the {@link MathContext} to use for this calculation
     * @return the {@code n}th root of {@code a}
     */
    public static RealType nthRoot(RealType a, IntegerType n, MathContext mctx) {
        if (n.sign() != Sign.POSITIVE) throw new IllegalArgumentException("Degree of root must be positive");
        final BigDecimal A = a.asBigDecimal();
        if (A.compareTo(BigDecimal.ZERO) == 0) {
            try {
                return (RealType) ExactZero.getInstance(mctx).coerceTo(RealType.class);
            } catch (CoercionException ex) {
                // we should never get here
                throw new IllegalStateException(ex);
            }
        }
        if (n.isEven() && a.sign() == Sign.NEGATIVE) {
            throw new ArithmeticException("Cannot compute a real-valued " + n + "th root of " + a);
        }
        if (n.asBigInteger().longValue() == 2L && useBuiltInOperations()) {
            return new RealImpl(A.sqrt(mctx), mctx, a.isExact());  // faster but sloppier
        }

        // sadly, we need to use int here because we're relying on
        // BigDecimal.pow() for speed and efficiency
        // on the other hand, if we are taking nth roots of values where n > MAX_INTEGER,
        // we might have other problems...
        final int nint = n.asBigInteger().intValueExact();
        final BigDecimal ncalc = new BigDecimal(n.asBigInteger());
        final BigDecimal nminus1 = ncalc.subtract(BigDecimal.ONE);
        BigDecimal x0;
        BigDecimal x1 = A.divide(new BigDecimal(n.asBigInteger()), mctx); // initial estimate

        do {
            x0 = x1;
            x1 = nminus1.multiply(x0, mctx).add(A.divide(x0.pow(nint - 1, mctx), mctx), mctx).divide(ncalc, mctx);
        } while (x0.compareTo(x1) != 0);
        x1 = x1.stripTrailingZeros();
        boolean irrational = classifyIfIrrational(x1, mctx);
        final RealImpl result = new RealImpl(x1, a.isExact() && !irrational);
        result.setMathContext(mctx);
        result.setIrrational(irrational);
        return result;
    }
    
    private static boolean classifyIfIrrational(BigDecimal realval, MathContext mctx) {
        if (realval.scale() <= 0) return false;  // this is an integer
        IntegerType nonFractionPart = new IntegerImpl(realval.toBigInteger());
        int reducedDigitLength = mctx.getPrecision() - (int) nonFractionPart.numberOfDigits();
        return reducedDigitLength == realval.scale();
    }
    
    /**
     * Compute the n<sup>th</sup> roots of unity, &#x212f;<sup>2&pi;&#x2148;k/n</sup> for
     * {k=0,&thinsp;1,&thinsp;2,&thinsp;&hellip;,&thinsp;n&minus;1}.
     * @param n the degree of the roots
     * @param mctx the {@link MathContext} for computing these values
     * @return a {@link Set} of {@code n} complex roots
     */
    public static Set<ComplexType> rootsOfUnity(long n, MathContext mctx) {
        if (n < 1L) throw new IllegalArgumentException("Degree of roots must be \u2265 1");
        final RealImpl decTwo = new RealImpl(new BigDecimal(TWO), mctx);
        decTwo.setMathContext(mctx);
        final RealImpl decOne = new RealImpl(BigDecimal.ONE, mctx);
        decOne.setMathContext(mctx);
        final RealType twopi = (RealType) Pi.getInstance(mctx).multiply(decTwo);
        NumericSet set = new NumericSet();
        for (long k = 0L; k < n; k++) {
            RationalType expFactor = new RationalImpl(k, n, mctx);
            ComplexPolarImpl val = new ComplexPolarImpl(decOne, (RealType) twopi.multiply(expFactor));
            val.setMathContext(mctx);
            set.append(val);
        }
        try {
            return set.coerceTo(ComplexType.class);
        } catch (CoercionException ex) {
            Logger.getLogger(MathUtils.class.getName()).log(Level.SEVERE, "NumericSet -> Set<ComplexType>", ex);
            throw new IllegalStateException("We should never have gotten here!", ex);
        }
    }

    /**
     * Method intended to determine the lowest precision of a {@link Collection} of {@link Numeric} arguments.
     * @param args a {@link Collection} of {@link Numeric} arguments
     * @return a {@link MathContext} constructed from the given arguments, or {@link MathContext#UNLIMITED} if none can be inferred from arguments
     */
    public static MathContext inferMathContext(Collection<? extends Numeric> args) {
        int precision = args.stream().mapToInt(x -> x.getMathContext().getPrecision()).filter(x -> x > 0).min().orElse(-1);
        if (precision > 0) {
            return new MathContext(precision, findMostCommonRoundingMode(args.stream().map(Numeric::getMathContext).collect(Collectors.toSet())));
        }
        return MathContext.UNLIMITED;
    }

    private static RoundingMode findMostCommonRoundingMode(Collection<MathContext> mathContexts) {
        final Map<RoundingMode, Integer> counts = new HashMap<>();
        mathContexts.forEach(ctx -> counts.put(ctx.getRoundingMode(), counts.getOrDefault(ctx.getRoundingMode(), 0) + 1));
        return counts.entrySet().stream().sorted((e1, e2) -> e2.getValue().compareTo(e1.getValue())).map(Map.Entry::getKey)
                .findFirst().orElse(RoundingMode.HALF_UP);
    }
    
    public static String inScientificNotation(RealType value) {
        return convertToScientificNotation(value.asBigDecimal());
    }
    
    public static String inScientificNotation(RationalType value) {
        return convertToScientificNotation(value.asBigDecimal());
    }
    
    private static String convertToScientificNotation(BigDecimal decValue) {
        if (decValue.scale() <= 0) {
            IntegerImpl temp = new IntegerImpl(decValue.toBigIntegerExact());
            return inScientificNotation(temp);
        }
        StringBuilder buf = new StringBuilder();
        
        int exponent = decValue.scale();
        BigDecimal temp = decValue;
        while (temp.abs().compareTo(BigDecimal.TEN) > 0) {
            temp = temp.movePointLeft(1);
            exponent++;
        }
        buf.append(temp.toPlainString()).append("\u2009\u00D7\u200910");
        buf.append(UnicodeTextEffects.numericSuperscript(exponent));
        
        return buf.toString();
    }
    
    public static String inScientificNotation(IntegerType value) {
        long digits = value.numberOfDigits();
        int exponent = (int) (digits - 1L);
        StringBuilder buf = new StringBuilder();
        buf.append(value.asBigInteger());
        int insertionPoint = 1;
        if (value.sign() == Sign.NEGATIVE) insertionPoint++;
        buf.insert(insertionPoint, '.');
        // U+2009 is thin space, U+00D7 is multiplication symbol
        buf.append("\u2009\u00D7\u200910").append(UnicodeTextEffects.numericSuperscript(exponent));
        
        return buf.toString();
    }

    private static final RealType TEN = new RealImpl(BigDecimal.TEN, MathContext.UNLIMITED);
    
    /**
     * Generate a matrix of rotation in 2 dimensions.
     * 
     * @param theta the angle of rotation in radians around the origin
     * @return a 2&#215;2 matrix of rotation
     */
    public static Matrix<RealType> get2DMatrixOfRotation(RealType theta) {
        RealType[][] temp = new RealType[2][2];

        RealType cos = cos(theta);
        RealType sin = sin(theta);

        temp[0][0] = cos;
        temp[0][1] = sin.negate();
        temp[1][0] = sin;
        temp[1][1] = cos;
        
        return new BasicMatrix<>(temp);
    }
    
    /**
     * Generate a matrix of rotation in 3 dimensions.
     * 
     * @param theta the angle of rotation in radians
     * @param axis the major axis around which the rotation is to occur
     * @return a 3&#215;3 matrix of rotation
     * @see <a href="https://en.wikipedia.org/wiki/Rotation_matrix">the Wikipedia article on matrices of rotation</a>
     */
    public static Matrix<RealType> get3DMatrixOfRotation(RealType theta, Axis axis) {
        final MathContext ctx = theta.getMathContext();
        final RealType one = new RealImpl(BigDecimal.ONE, ctx);
        final RealType zero = new RealImpl(BigDecimal.ZERO, ctx);
        RealType[][] temp = new RealType[3][];

        RealType cos = cos(theta);
        RealType sin = sin(theta);

        switch (axis) {
            case X_AXIS:
                temp[0] = new RealType[] { one, zero, zero };
                temp[1] = new RealType[] { zero, cos, sin.negate() };
                temp[2] = new RealType[] { zero, sin, cos };
                break;
            case Y_AXIS:
                temp[0] = new RealType[] { cos, zero, sin };
                temp[1] = new RealType[] { zero, one, zero };
                temp[2] = new RealType[] { sin.negate(), zero, cos };
                break;
            case Z_AXIS:
                temp[0] = new RealType[] { cos, sin.negate(), zero };
                temp[1] = new RealType[] { sin, cos, zero };
                temp[2] = new RealType[] { zero, zero, one };
                break;
        }
        return new BasicMatrix<>(temp);
    }

    private static final EnumMap<Axis, Long> axisToIndex = new EnumMap<>(Axis.class);
    static {
        axisToIndex.put(Axis.X_AXIS, 0L);
        axisToIndex.put(Axis.Y_AXIS, 1L);
        axisToIndex.put(Axis.Z_AXIS, 2L);
    }

    /**
     * Determine if a vector is aligned with the given axis.
     * This is calculated by checking whether the vector has a non-zero
     * value for only one of its dimensions.
     * @param vector the vector to test
     * @param axis   the {@link Axis} against which we are testing
     * @return true if the given vector aligns with the given axis
     */
    public static boolean isAlignedWith(Vector<RealType> vector, Axis axis) {
        long idx = axisToIndex.get(axis);
        boolean match = false;
        for (int k = 0; k < vector.length(); k++) {
            if (k == idx) {
                // we should have a single non-zero element corresponding with the axis
                match = !Zero.isZero(vector.elementAt(k));
            } else {
                // all elements other than those aligned with the given axis should be zero
                if (!Zero.isZero(vector.elementAt(k))) return false;
            }
        }
        return match;
    }

    /**
     * Determine if a vector aligns with any spatial axis.
     * @param vector the vector to test
     * @return the {@link Axis} that {@code vector} aligns with, or {@code null}
     *  if the vector does not align fully with any axis.
     */
    public static Axis axisAlignedWith(Vector<RealType> vector) {
        return Arrays.stream(Axis.values()).filter(x -> isAlignedWith(vector, x))
                .findFirst().orElse(null);
    }

    /**
     * Compute the Hadamard product for two n&times;n matrices.
     * In standard notation, that is <strong>A&#x2218;B</strong>.
     * Note that, unlike regular matrix multiplication, the Hadamard
     * product is commutative.
     * @param A the first matrix in the product
     * @param B the second matrix in the product
     * @return the Hadamard product of {@code A} and {@code B}
     * @param <T> the element type for the input matrices
     */
    public static <T extends Numeric> Matrix<T> hadamardProduct(Matrix<T> A, Matrix<T> B) {
        if (A.rows() != B.rows() || A.columns() != B.columns()) {
            throw new ArithmeticException("Matrices must be of equal dimension");
        }
        return new ParametricMatrix<>(A.rows(), A.columns(),
                (row, column) -> (T) A.valueAt(row, column).multiply(B.valueAt(row, column)));
    }

    public static <T extends Numeric> Matrix<T> hadamardProduct(Vector<T> a, Vector<T> b) {
        if (a.length() != b.length()) throw new ArithmeticException("Vectors must be of equal dimension");
        Matrix<T> diag = new DiagonalMatrix<>(a);
        ColumnVector<T> col = new ArrayColumnVector<>(b);
        return diag.multiply(col);
    }

    public static long MAX_CLONE_DEPTH = (long) Integer.MAX_VALUE >> 4L;

    /**
     * Compute the conjugate transpose of a given matrix A, denoted A<sup>*</sup>.
     * This is equivalent to taking the transpose of A and then taking the complex
     * conjugate of each value contained therein.
     * @param original the original matrix for which we want the conjugate transpose
     * @return the conjugate transpose of {@code original}
     */
    public static Matrix<ComplexType> conjugateTranspose(Matrix<? extends Numeric> original) {
        if (original instanceof Vector) {
            Vector<Numeric> asVector = (Vector<Numeric>) original;
            ComplexType[] cplxElements = new ComplexType[(int) asVector.length()];
            try {
                for (int idx = 0; idx < cplxElements.length; idx++)
                    cplxElements[idx] = ((ComplexType) asVector.elementAt(idx).coerceTo(ComplexType.class)).conjugate();
            } catch (CoercionException fail) {
                Logger.getLogger(MathUtils.class.getName()).log(Level.SEVERE, "While computing complex conjugate before transpose.", fail);
                throw new ArithmeticException("Unable to compute conjugate for " + asVector);
            }
            if (asVector instanceof RowVector) {
                return new ArrayColumnVector<>(cplxElements);
            } else if (asVector instanceof ColumnVector) {
                return new ArrayRowVector<>(cplxElements);
            }
            throw new IllegalStateException("Unknown class implementing both Matrix and Vector: " + original.getClass().getTypeName());
        }
        // if the matrix is big enough, return a transformed view
        if (original.rows() > MAX_CLONE_DEPTH || original.columns() > MAX_CLONE_DEPTH) {
            return new ParametricMatrix<>(original.columns(), original.rows(), (row, column) -> {
                try {
                    ComplexType interim = (ComplexType) original.valueAt(column, row).coerceTo(ComplexType.class);
                    return interim.conjugate();
                } catch (CoercionException e) {
                    throw new ArithmeticException("Could not compute conjugate for element at " + row + ", " + column);
                }
            });
        }
        // otherwise use a 2D array as a working copy
        ComplexType[][] working = new ComplexType[(int) original.columns()][(int) original.rows()];
        try {
            for (long j = 0L; j < original.columns(); j++) {
                for (long k = 0L; k < original.rows(); k++) {
                    ComplexType value = (ComplexType) original.valueAt(k, j).coerceTo(ComplexType.class);
                    working[(int) j][(int) k] = value.conjugate();
                }
            }
        } catch (CoercionException e) {
            throw new IllegalStateException("Any type should be coercible to ComplexType", e);
        }
        return new BasicMatrix<>(working);
    }

    /**
     * Compute &#x212f;<sup>X</sup> for a square matrix <strong>X</strong>.
     * Since the calculation is an infinite series, we only compute k terms,
     * where k is derived from the {@link MathContext} of the elements in {@code X}.
     * @param X a n&times;n matrix
     * @return the n&times;n matrix that is an approximation of &#x212f;<sup>X</sup>
     */
    public static Matrix<? extends Numeric> exp(Matrix<? extends Numeric> X) {
        if (X instanceof DiagonalMatrix) return ((DiagonalMatrix<? extends Numeric>) X).exp();
        if (X instanceof SingletonMatrix || (X.columns() == 1L && X.rows() == 1L)) {
            final Numeric value = X.valueAt(0L, 0L);
            final Euler e = Euler.getInstance(value.getMathContext());
            try {
                return new SingletonMatrix<>(value instanceof ComplexType ? e.exp((ComplexType) value) :
                        e.exp((RealType) value.coerceTo(RealType.class)));
            } catch (CoercionException ex) {
                throw new ArithmeticException("Cannot compute \u212Fˣ: " + ex.getMessage());
            }
        }
        if (X.rows() != X.columns()) throw new ArithmeticException("Cannot compute exp for a non-square matrix");
        final MathContext ctx = X.valueAt(0L, 0L).getMathContext();
        Matrix<Numeric> intermediate = new ZeroMatrix(X.rows(), ctx);
        // since this series can converge (very) slowly, this multiplier may need to be increased
        long sumLimit = 2L * ctx.getPrecision() + 4L; // will get at least 4 terms if precision = 0 (Unlimited)
        for (long k = 0L; k < sumLimit; k++) {
            IntegerType kval = new IntegerImpl(BigInteger.valueOf(k));
            intermediate = intermediate.add(((Matrix<Numeric>) X.pow(kval)).scale(factorial(kval).inverse()));
        }
        // return a special anonymous subclass of BasicMatrix which computes the
        // determinant based on the trace of X, which is much cheaper than the default calculation
        return new BasicMatrix<>(intermediate) {
            @Override
            public Numeric determinant() {
                Euler e = Euler.getInstance(ctx);
                Numeric tr = X.trace();
                if (tr instanceof ComplexType) {
                    return e.exp((ComplexType) tr);
                } else {
                    try {
                        return e.exp((RealType) tr.coerceTo(RealType.class));
                    } catch (CoercionException ex) {
                        throw new IllegalStateException("While computing determinant from trace", ex);
                    }
                }
            }
        };
    }

    /**
     * Compute the Moore-Penrose inverse of a matrix.  This is a generalization of the
     * inverse of a square matrix, and can be used to solve a linear system of equations
     * represented by a non-square matrix. Given a matrix A, the Moore-Penrose inverse
     * is written as A<sup>+</sup>.<br/>
     * If the supplied matrix is neither of full column rank nor full row rank, the
     * iterative algorithm by Ben-Israel and Cohen will be used.
     * @param M the {@link Matrix} for which to compute the Moore-Penrose inverse
     * @return the Moore-Penrose inverse of {@code M}, denoted M<sup>+</sup>
     * @see <a href="https://en.wikipedia.org/wiki/Moore%E2%80%93Penrose_inverse">the related article at Wikipedia</a>
     */
    public static Matrix<? extends Numeric> pseudoInverse(Matrix<? extends Numeric> M) {
        // if M is square, it's a degenerate case
        if (M.rows() == M.columns()) {
            return M.inverse();
        }
        // otherwise compute the pseudoinverse
        long rank = rank(M);
        Matrix<ComplexType> Mcxp = conjugateTranspose(M);
        Matrix<ComplexType> Mcplx = new ParametricMatrix<>(M.rows(), M.columns(), (row, column) -> {
            try {
                return (ComplexType) M.valueAt(row, column).coerceTo(ComplexType.class);
            } catch (CoercionException ce) {
                throw new IllegalStateException(String.format("Unable to upconvert element %s at %d,\u2009%d",
                        M.valueAt(row, column), row, column), ce);
            }
        });
        Logger logger = Logger.getLogger(MathUtils.class.getName());
        if (rank == M.columns()) {
            // full column rank
            logger.log(Level.FINE, "Computing A\u20F0A");
            Matrix<ComplexType> prod = Mcxp.multiply(Mcplx);  // A⃰A
            logger.log(Level.FINE, "Computing A\u207A = (A\u20F0A)\u207B\u00B9A\u20F0");
            return ((Matrix<ComplexType>) prod.inverse()).multiply(Mcxp);
        } else if (rank == M.rows()) {
            // full row rank
            logger.log(Level.FINE, "Computing AA\u20F0");
            Matrix<ComplexType> prod = Mcplx.multiply(Mcxp);  // AA⃰
            logger.log(Level.FINE, "Computing A\u207A = A\u20F0(AA\u20F0)\u207B\u00B9");
            return Mcxp.multiply((Matrix<ComplexType>) prod.inverse());
        } else {
            final ComplexType sigma = sigma_1(Mcplx);
            ComplexType sigSquared = (ComplexType) sigma.multiply(sigma.conjugate());  // this should actually be a real (i.e., zero imaginary part)
            ComplexType maxAlpha = (ComplexType) new RealImpl(decTWO).divide(sigSquared);  // should work without coercion
            final RealType zero = new RealImpl(BigDecimal.ZERO, sigma.getMathContext());
            Range<RealType> alphaRange = new Range<>(zero, maxAlpha.real(), BoundType.EXCLUSIVE);
            final ComplexType scale = new ComplexRectImpl(random(alphaRange));
            ComplexType cplxTwo = new ComplexRectImpl(new RealImpl(decTWO, sigma.getMathContext()), zero, true);

            // take the iterative approach
            Matrix<ComplexType> intermediate = Mcxp.scale(scale);
            final int iterationLimit = 3 * sigma.getMathContext().getPrecision() + 2;  // Needs tuning!
            logger.log(Level.INFO, "Computing {3} terms of convergent series A\u2093\u208A\u2081 = 2A\u2093 - A\u2093AA\u2093 for " +
                    "{0}\u00D7{1} matrix A with rank {2}.", new Object[] {M.rows(), M.columns(), rank, iterationLimit});
            int count = 0;
            do {
                intermediate = intermediate.scale(cplxTwo).subtract(intermediate.multiply(Mcplx).multiply(intermediate));
            } while (++count < iterationLimit);  // TODO find a better way to estimate how many iterations we need
            return intermediate;
        }
    }

    /**
     * Compute &sigma;<sub>1</sub>(M) of any {@link Matrix} M, which returns the single largest
     * value of M (i.e., the matrix element with the greatest {@link Numeric#magnitude() magnitude}).
     * @param M any {@link Matrix}
     * @return the element of {@code M} with the greatest magnitude
     * @param <T> the numeric type of the elements of {@code M} as well as the return value
     */
    public static <T extends Numeric> T sigma_1(Matrix<T> M) {
        T maxVal = null;

        if (M.getClass().isAnnotationPresent(Columnar.class)) {
            for (long col = 0L; col < M.columns(); col++) {
                ColumnVector<T> column = M.getColumn(col);
                T colMax = column.stream().max((x, y) -> x.magnitude().compareTo(y.magnitude())).orElseThrow();
                if (maxVal == null || colMax.magnitude().compareTo(maxVal.magnitude()) > 0) maxVal = colMax;
            }
        } else {
            for (long rowidx = 0L; rowidx < M.rows(); rowidx++) {
                RowVector<T> row = M.getRow(rowidx);
                T rowMax = row.stream().max((x, y) -> x.magnitude().compareTo(y.magnitude())).orElseThrow();
                if (maxVal == null || rowMax.magnitude().compareTo(maxVal.magnitude()) > 0) maxVal = rowMax;
            }
        }

        return maxVal;
    }

    public static Matrix<RealType> reify(Matrix<ComplexType> C) {
        BasicMatrix<RealType> result = new BasicMatrix<>();
        for (long row = 0L; row < C.rows(); row++) {
            RowVector<ComplexType> orig = C.getRow(row);
            if (orig.stream().parallel().anyMatch(c -> !c.isCoercibleTo(RealType.class))) {
                Logger.getLogger(MathUtils.class.getName()).log(Level.SEVERE,
                        "Row {0} of source matrix contains elements that cannot be converted to RealType: {1}",
                        new Object[] {row, orig});
                throw new ArithmeticException("Source matrix cannot be converted to real, row = " + row);
            }
            RowVector<RealType> converted = new ListRowVector<>();
            orig.stream().map(ComplexType::real).forEachOrdered(converted::append);
            result.append(converted);
        }
        return result;
    }

    public static long rankUpperLimit(Matrix<? extends Numeric> M) {
        return Math.min(M.rows(), M.columns());
    }

    public static long rank(Matrix<? extends Numeric> M) {
        Matrix<? extends Numeric> R = toReducedRowEchelonForm(M);
        long rank = 0L;
        for (long rowIdx = 0L; rowIdx < R.rows(); rowIdx++) {
            if (!ZeroVector.isZeroVector(R.getRow(rowIdx))) rank++;
        }
        return rank;
    }

    /**
     * Convert a given matrix to reduced row echelon form.
     * The original {@link Matrix} is not changed, even if it is
     * a mutable subclass.
     * @param M the matrix to be converted
     * @return the converted matrix in reduced row echelon form
     * @see <a href="https://en.wikipedia.org/wiki/Row_echelon_form">the Wikipedia article</a>, which
     *  outlines the basic algorithm
     */
    public static Matrix<? extends Numeric> toReducedRowEchelonForm(Matrix<? extends Numeric> M) {
        long lead = 0L;
        BasicMatrix<Numeric> MM = new BasicMatrix<>((Matrix<Numeric>) M);

        for (long r = 0L; r < M.rows(); r++) {
            if (M.columns() < lead) break;

            long i = r;
            while (Zero.isZero(MM.valueAt(i, lead))) {
                if (M.rows() == ++i) {
                    i = r;
                    if (M.columns() == ++lead) return MM; // exit completely
                }
            }
            if (i != r) MM.exchangeRows(i, r);
            MM.updateRow(r, MM.getRow(r).scale(MM.valueAt(r, lead).inverse()));
            for (long j = 0L; j < M.rows(); j++) {
                if (j == r) continue;
                RowVector<Numeric> jthRow = MM.getRow(j);
                Vector<Numeric> subtrahend = MM.getRow(r).scale(MM.valueAt(j, lead));
                MM.updateRow(j, jthRow.subtract(subtrahend));
            }
            lead++;
        }
        return MM;
    }

    public static boolean areLinearlyIndependent(List<Vector<? extends Numeric>> vectors) {
        final long veclen = vectors.get(0).length();
        final long numVec = vectors.parallelStream().count();
        if (vectors.parallelStream().map(Vector::length).anyMatch(val -> val != veclen)) {
            throw new IllegalArgumentException("Vectors must be of the same length");
        }
        if (numVec > veclen) return false; // more vectors than dimensions
        if (vectors.parallelStream().anyMatch(ZeroVector::isZeroVector)) return false;

        ColumnarMatrix<Numeric> M = new ColumnarMatrix<>();
        vectors.stream().map(MathUtils::columnVectorFrom).forEach(colVec -> M.append((ColumnVector<Numeric>) colVec));
        if (veclen == numVec) {
            return !Zero.isZero(M.determinant());
        }
        // otherwise, handle the case where veclen > numVec, i.e., more dimensions than vectors
        Logger logger = Logger.getLogger(MathUtils.class.getName());
        logger.log(Level.INFO, "Computing {0} permutations of {1} vectors for a {1}\00D7{2} matrix.",
                new Object[] {nChooseK(veclen, numVec), numVec, veclen});
        // first, we will transpose M so that the vectors we're checking are on the rows, not the columns
        Matrix<Numeric> V = M.transpose();
        assert V.columns() == veclen;
        assert V.rows() == numVec;
        List<List<Long>> indexSets = permuteIndices(veclen, numVec);
        for (List<Long> indices : indexSets) {
            if (logger.isLoggable(Level.FINE)) logger.log(Level.FINE, "Checking vectors at rows {0}.", indices);
            BasicMatrix<Numeric> constructed = new BasicMatrix<>();
            indices.stream().map(V::getRow).forEachOrdered(constructed::append);
            if (Zero.isZero(constructed.determinant())) return false;
        }
        return true;
    }

    private static List<List<Long>> permuteIndices(long n, long k) {
        if (k < 0 || k > n) throw new IllegalArgumentException("Bad parameters");
        final long limit = 1L << n;  // 2^n
        return LongStream.range(1L, limit).mapToObj(BigInteger::valueOf).filter(b -> b.bitCount() == k)
                .map(MathUtils::expandIndices).collect(Collectors.toList());
    }

    private static List<Long> expandIndices(BigInteger bits) {
        List<Long> result = new LinkedList<>();
        for (int k = 0; k < bits.bitLength(); k++) {
            if (bits.testBit(k)) result.add((long) k);
        }
        return result;
    }

    /**
     * Determine if a given matrix is normal.
     * A matrix is considered normal if it commutes with its conjugate transpose.
     * In other words, A<sup>*</sup>A = AA<sup>*</sup>.
     * @param matrix the {@link Matrix} to test whether it is normal
     * @return true if the supplied {@link Matrix} is normal, false otherwise
     */
    public static boolean isNormal(Matrix<? extends Numeric> matrix) {
        if (matrix.rows() != matrix.columns()) return false;  // non-square matrices are non-normal
        Matrix<ComplexType> conjXpose = conjugateTranspose(matrix);
        Matrix<ComplexType> inputAsCplx = new ParametricMatrix<>(matrix.rows(), matrix.columns(), (row, column) -> {
            try {
                return (ComplexType) matrix.valueAt(row, column).coerceTo(ComplexType.class);
            } catch (CoercionException e) {
                throw new ArithmeticException("Unable to coerce element at " + row + ", " + column);
            }
        });
        // IntelliJ (and probably other IDEs) will complain that equals() is between objects of
        // inconvertible types, but this is some high-grade horseshit owing to the way Java
        // implements generic type support.  Matrix<ComplexType> is not considered a subtype
        // of Matrix<Numeric> (the supertype of ZeroMatrix).  Yet the equals() method of ZeroMatrix
        // delegates to ParametricMatrix.equals(), which tests two matrices for equality based upon
        // element-wise comparison using Numeric.equals()...
        // Ultimately, it's just easier to create a method ZeroMatrix.isZeroMatrix() which tests
        // the cell values for equality to zero.
        return ZeroMatrix.isZeroMatrix(inputAsCplx.multiply(conjXpose).subtract(conjXpose.multiply(inputAsCplx)));
    }

    /**
     * Determine if a complex matrix is Hermitian (that is, equal to its own conjugate transpose).
     * @param cplxMatrix the complex matrix to test
     * @return true if the given matrix is Hermitian, false otherwise
     */
    public static boolean isHermitian(Matrix<ComplexType> cplxMatrix) {
        return cplxMatrix.equals(conjugateTranspose(cplxMatrix));
    }

    /**
     * Determine if a matrix is orthogonal. That is, the columns and rows
     * of the matrix are orthonormal vectors.
     * @param M the matrix to test for orthogonality
     * @return true if the matrix is orthogonal, false otherwise
     */
    public static boolean isOrthogonal(Matrix<RealType> M) {
        if (M.rows() != M.columns()) return false;  // must be a square matrix
        try {
            return M.transpose().equals(M.inverse());
        } catch (ArithmeticException e) {
            // it makes more sense to log the event and return false
            // this is less costly than pre-checking if the matrix is singular
            Logger.getLogger(MathUtils.class.getName()).log(Level.FINE,
                    "While computing matrix inverse for comparison to transpose.", e);
            return false;
        }
    }

    /**
     * Determine if a matrix is unitary.  This is the complex equivalent
     * to orthogonality.
     * @param C the matrix to test
     * @return true if the matrix is unitary, false otherwise
     * @see #isOrthogonal(Matrix)
     */
    public static boolean isUnitary(Matrix<ComplexType> C) {
        if (C.rows() != C.columns()) return false;  // must be square
        try {
            return conjugateTranspose(C).equals(C.inverse());
        } catch (ArithmeticException e) {
            // it makes more sense to log the event and return false
            // this is less costly than pre-checking if the matrix is singular
            Logger.getLogger(MathUtils.class.getName()).log(Level.FINE,
                    "While computing matrix inverse for comparison to conjugate transpose.", e);
            return false;
        }
    }

    public static boolean isOfType(Matrix<? extends Numeric> matrix, Class<? extends Numeric> clazz) {
        if (matrix instanceof SingletonMatrix || (matrix.rows() == 1L && matrix.columns() == 1L)) {
            return clazz.isAssignableFrom(matrix.valueAt(0L, 0L).getClass());
        }
        if (matrix instanceof DiagonalMatrix) {
            // To guard against a heterogeneous matrix causing problems, we must check
            // one off-diagonal cell.
            return LongStream.range(0L, matrix.rows()).mapToObj(idx -> matrix.valueAt(idx, idx))
                    .map(Object::getClass).allMatch(clazz::isAssignableFrom) &&
                    clazz.isAssignableFrom(matrix.valueAt(0L, matrix.columns() - 1L).getClass());
        }
        if (matrix.getClass().isAnnotationPresent(Columnar.class)) {
            return LongStream.range(0L, matrix.columns()).mapToObj(matrix::getColumn).flatMap(ColumnVector::stream)
                    .map(Object::getClass).allMatch(clazz::isAssignableFrom);
        }
        return LongStream.range(0L, matrix.rows()).mapToObj(matrix::getRow).flatMap(RowVector::stream)
                .map(Object::getClass).allMatch(clazz::isAssignableFrom);
    }

    /**
     * Compute the eigenvalues for the given matrix. The returned {@link Set}
     * may be heterogeneous (i.e., {@link Set<Numeric>} which can contain
     * any subclass of {@link Numeric}).<br/>
     * <strong>Note:</strong> This method is currently only guaranteed to
     * produce results for triangular matrices and 2&times;2 matrices.
     * Symmetric 3&times;3 matrices are also supported. There is very
     * limited support for block-diagonal matrices &mdash;
     * those implemented using {@link AggregateMatrix}, specifically.
     * If all else fails, this method will attempt to determine eigenvalues
     * using the QR algorithm, which repeatedly applies {@link #computeQRdecomposition(Matrix) QR decomposition}
     * and thus is costly.
     * @param M the {@link Matrix} for which we wish to obtain the eigenvalues
     * @return a {@link Set} of eigenvalues
     * @see <a href="https://en.wikipedia.org/wiki/QR_algorithm">the Wikipedia article on the QR algorithm</a>
     */
    public static Set<? extends Numeric> eigenvaluesOf(Matrix<? extends Numeric> M) {
        if (M.rows() != M.columns()) throw new IllegalArgumentException("Cannot compute eigenvalues for a non-square matrix");
        if (M.isTriangular()) {
            // the values on the diagonal are the eigenvalues
            NumericSet diagonalElements = new NumericSet();
            LongStream.range(0L, M.rows()).mapToObj(idx -> M.valueAt(idx, idx)).forEach(diagonalElements::append);
            return diagonalElements;
        }
        // if M is block-diagonal, then the eigenvalues of M are the eigenvalues of all submatrices on the diagonal
        if (M instanceof AggregateMatrix) {
            AggregateMatrix<? extends Numeric> blockMatrix = (AggregateMatrix<? extends Numeric>) M;
            if (blockMatrix.subMatrixRows() != blockMatrix.subMatrixColumns()) {
                Logger.getLogger(MathUtils.class.getName()).log(Level.WARNING,
                        "Block matrix is {0}×{1} (detected as square) but the tiles are laid out {2}×{3} (non-square).",
                        new Object[] { blockMatrix.rows(), blockMatrix.columns(),
                                blockMatrix.subMatrixRows(), blockMatrix.subMatrixColumns() });
            }
            if (isBlockDiagonal(blockMatrix)) {
                NumericSet allEigenvalues = new NumericSet();
                for (int idx = 0; idx < blockMatrix.subMatrixRows(); idx++) {
                    Set<? extends Numeric> subMatrixEigenvalues = eigenvaluesOf(blockMatrix.getSubMatrix(idx, idx));
                    StreamSupport.stream(subMatrixEigenvalues.spliterator(), false).forEach(allEigenvalues::append);
                }
                return allEigenvalues;
            }
        }
        if (M.rows() == 2L) {
            // 2×2 matrices are trivial to compute the eigenvalues of
            return computeEigenvaluesFor2x2(M);
        }
        if (M.rows() == 3L && isSymmetric(M)) {
            return computeEigenvaluesFor3x3Symmetric(M);
        }
        // let's try QR decomposition
        List<Matrix<Numeric>> decomp = computeQRdecomposition((Matrix<Numeric>) M);
        Matrix<Numeric> Q = decomp.get(0);
        Matrix<Numeric> R = decomp.get(1);
        // pull the MathContext from the last column, which should serve as a proxy for the whole matrix
        MathContext ctx = M.getColumn(M.columns() - 1L).getMathContext();
        RealType epsilon = computeIntegerExponent(TEN, 1 - ctx.getPrecision(), ctx);
        if (isUpperTriangularWithin(R, epsilon)) {
            Matrix<Numeric> A;
            do {
                A = R.multiply(Q);
                // the lower (left) triangle might not be filled with exactly zeroes, so check within some tolerance epsilon
                if (isUpperTriangularWithin(A, epsilon)) break;
                decomp = computeQRdecomposition(A);
                Q = decomp.get(0);
                R = decomp.get(1);
            } while (!isUpperTriangularWithin(A, epsilon));
            // pick off the eigenvalues from the diagonal
            NumericSet results = new NumericSet();
            for (long idx = 0L; idx < A.rows(); idx++) {
                results.append(A.valueAt(idx, idx));
            }
            return results;
        } else {
            Logger.getLogger(MathUtils.class.getName()).log(Level.INFO,
                    "QR decomposition failed. R is not upper-triangular to within {1}:\n{0}",
                    new Object[] {R, epsilon});
        }

        // we can't handle this type of matrix yet
        throw new UnsupportedOperationException("Cannot compute eigenvalues for square matrix of size " + M.rows());
    }

    /**
     * Determine if a given matrix is upper-triangular within some tolerance.
     * More formally, determines if all the matrix elements in the lower (left) triangle
     * are &lt;&thinsp;&epsilon; for some value &#x1D700; that satisfies the
     * inequality 0 &lt; &#x1D700; &#x226A; 1.
     * @param M       the matrix to test for upper-triangularity
     * @param epsilon the tolerance value &epsilon; denoting the maximum acceptable error
     * @return true if the given matrix satisfies the error-tolerance criteria for upper-triangularity,
     *  false otherwise
     */
    public static boolean isUpperTriangularWithin(Matrix<? extends Numeric> M, RealType epsilon) {
        if (!epsilonRange.contains(epsilon)) throw new IllegalArgumentException("Tolerance should be in range 0 < \uD835\uDF00 \u226A 1");
        if (M.columns() != M.rows()) return false;
        if (M.rows() == 1L) return false;  // singleton matrix can't really be triangular

        for (long row = 1L; row < M.rows(); row++) {
            for (long column = 0L; column < M.columns() - (M.rows() - row); column++) {
                try {
                    if (((RealType) M.valueAt(row, column).magnitude().coerceTo(RealType.class)).compareTo(epsilon) >= 0) {
                        return false;
                    }
                } catch (CoercionException e) {
                    Logger.getLogger(MathUtils.class.getName()).log(Level.WARNING,
                            "Failed to coerce the magnitude of the matrix element at {0},{1} to a real value.",
                            new Object[] {row, column});
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Given a matrix <strong>M</strong> and a set of eigenvalues, computes the eigenvectors.
     * @param M           a square matrix
     * @param eigenvalues a set of one or more eigenvalues of <strong>M</strong>, though
     *                    this set need not be comprehensive
     * @return a {@link Map} of eigenvalues and their corresponding eigenvectors
     * @param <T> the type of the elements of {@code M} and its eigenvalues
     */
    public static <T extends Numeric> Map<T, Vector<T>> eigenvectorsOf(Matrix<T> M, Set<T> eigenvalues) {
        if (eigenvalues.cardinality() <= 0L) throw new IllegalArgumentException("No eigenvalues to solve for");
        Map<T, Vector<T>> results = new HashMap<>((int) eigenvalues.cardinality());

        try {
            for (T value : eigenvalues) {
                final Class<T> clazz = (Class<T>) value.getClass();
                T zero = (T) ExactZero.getInstance(value.getMathContext()).coerceTo(clazz);
                Vector<T> zeroVector = columnVectorFrom(ZeroVector.getInstance(M.rows(), value.getMathContext()), clazz);
                Matrix<T> lambdaMatrix = new ParametricMatrix<>(M.rows(), M.columns(), (row, column) -> {
                    if (row.longValue() == column.longValue()) return value;
                    return zero;
                });
                results.put(value, triangularizeAndSolve(M.subtract(lambdaMatrix), zeroVector));
            }
        } catch (CoercionException e) {
            throw new IllegalStateException("While obtaining a zero instance and converting it", e);
        }
        return results;
    }

    public static boolean isBlockDiagonal(AggregateMatrix<? extends Numeric> blockMatrix) {
        for (int blockRow = 0; blockRow < blockMatrix.subMatrixRows(); blockRow++) {
            for (int blockCol = 0; blockCol < blockMatrix.subMatrixColumns(); blockCol++) {
                if (blockRow == blockCol) continue;
                if (!ZeroMatrix.isZeroMatrix(blockMatrix.getSubMatrix(blockRow, blockCol))) return false;
            }
        }
        return true;
    }

    public static boolean isSymmetric(Matrix<? extends Numeric> matrix) {
        if (matrix instanceof DiagonalMatrix) return true;
        if (matrix.rows() != matrix.columns()) return false;
        if (matrix.rows() == 1L) return true; // a singleton matrix is always symmetric
        for (long row = 0L; row < matrix.rows() - 1L; row++) {
            for (long column = row + 1L; column < matrix.columns(); column++) {
                if (!matrix.valueAt(row, column).equals(matrix.valueAt(column, row))) return false;
            }
        }
        return true;
    }

    /**
     * Decompose an augmented matrix [<strong>A</strong>|b&#x20d7;] into
     * <strong>A</strong> and column vector b&#x20d7;.
     * @param augmented the augmented matrix to decompose
     * @return a {@link List} containing a matrix and a column vector, in that order
     * @param <T> the type of the elements of {@code augmented}
     */
    public static <T extends Numeric> List<Matrix<T>> splitAugmentedMatrix(Matrix<T> augmented) {
        final long lastColumn = augmented.columns() - 1L;
        ColumnVector<T> rhs = augmented.getColumn(lastColumn);
        Matrix<T> lhs;
        if (augmented instanceof BasicMatrix) {
            lhs = ((BasicMatrix<T>) augmented).removeColumn(lastColumn);
        } else if (augmented instanceof ColumnarMatrix) {
            lhs = ((ColumnarMatrix<T>) augmented).removeColumn(lastColumn);
        } else {
            lhs = new SubMatrix<>(augmented, 0L, 0L, augmented.rows() - 1L, lastColumn - 1L);
        }
        assert lhs.rows() == lhs.columns();  // lhs should be a square matrix
        return List.of(lhs, rhs);
    }

    /**
     * Given matrix <strong>A</strong> and vector b&#x20d7;, perform Gaussian elimination
     * and then solve for x&#x20d7; in the equation <strong>A</strong>x&#x20d7;&nbsp;=&nbsp;b&#x20d7;.
     * @param A a square matrix
     * @param b a vector of values with a length corresponding to {@code A.rows()}
     * @return the solution to the equation <strong>A</strong>x&#x20d7;&nbsp;=&nbsp;b&#x20d7;
     * @param <T> the type of the elements of {@code A} and {@code b}
     */
    public static <T extends Numeric> Vector<T> triangularizeAndSolve(Matrix<T> A, Vector<T> b) {
        if (A.rows() != A.columns()) throw new IllegalArgumentException("A must be square");
        if (b.length() != A.rows()) throw new IllegalArgumentException("Length of b must equal rows of A");
        final long n = A.rows();

        Matrix<T> U = A;
        ColumnVector<T> c = b instanceof ColumnVector ? (ColumnVector<T>) b : new ArrayColumnVector<>(b);
        for (long j = 0L; j < n - 1L; j++) {
            Matrix<T> intermediate = gaussianElimination(U, c, j);
            List<Matrix<T>> parts = splitAugmentedMatrix(intermediate);
            U = parts.get(0);
            c = (ColumnVector<T>) parts.get(1);
            // we could put a bailout condition here if the matrix U is already upper triangular
            // if (U.isUpperTriangular()) break;
            // but that might cost us more than just continuing the iteration... needs more thought
        }
        return backSubstitution(U, c);
    }

    /**
     * Perform Gaussian elimination on a matrix <strong>A</strong> and vector b&#x20d7;.
     * The result is a reduced matrix <strong>U</strong> and associated vector c&#x20d7;
     * which, after the final pivot, is in upper-triangular form, suitable for backsolving in the form
     * <strong>U</strong>x&#x20d7;&nbsp;=&nbsp;c&#x20d7;.
     *
     * @param A the n&times;n {@link Matrix} we wish to reduce
     * @param b the associated {@link Vector}
     * @param j the row on which to pivot
     * @return an augmented n&thinsp;&times;&thinsp;n+1 matrix containing a reduced matrix and its
     *  associated vector in the form [<strong>U</strong>|c&#x20d7;]
     * @param <T> the numeric type of the elements of {@code A} and {@code b}
     */
    public static <T extends Numeric> Matrix<T> gaussianElimination(Matrix<T> A, Vector<T> b, long j) {
        if (j < 0L || j >= A.rows()) throw new IndexOutOfBoundsException("Index j must be between 0 and " + A.rows());
        if (A.columns() != A.rows()) throw new IllegalArgumentException("Matrix is non-square");
        if (A.rows() != b.length()) throw new IllegalArgumentException("Vector length must match rows of matrix");
        BasicMatrix<T> U = new BasicMatrix<>(A);
        // for convenience, we attach b to A and create an augmented matrix
        U.append(new ArrayColumnVector<>(b));
        final long n = A.rows();

        if (Zero.isZero(U.valueAt(j, j))) {
            Comparator<Numeric> comp = obtainGenericComparator();
            Numeric biggestValue = ExactZero.getInstance(b.getMathContext());
            long kRow = j;

            for (long k = j + 1L; k < n; k++) {
                if (comp.compare(U.valueAt(k, j).magnitude(), biggestValue) > 0) {
                    biggestValue = U.valueAt(k, j).magnitude();
                    kRow = k;
                }
            }
            // swap rows j and kRow
            // Note: original algorithm only bothers exchanging elements starting at column j
            U.exchangeRows(j, kRow);  // this also effectively swaps elements of the b vector
        }
        T pivot = U.valueAt(j, j);
        if (Zero.isZero(pivot)) {
            throw new ArithmeticException("Matrix A is singular");
        }
        // reduce the rows
        for (long i = j + 1L; i < n; i++) {
            T multiplier = (T) U.valueAt(i, j).divide(pivot);
            for (long l = j; l < n + 1L; l++) { // ensure we include the column corresponding to c
                T updValue = (T) U.valueAt(i, l).subtract(U.valueAt(j, l).multiply(multiplier));
                U.setValueAt(updValue, i, l);
            }
        }

        return U;
    }

    /**
     * Perform back substitution to solve <strong>U</strong>x&#x20d7;&nbsp;=&nbsp;c&#x20d7; for x&#x20d7;,
     * where <strong>U</strong> is an upper-triangular matrix and c&#x20d7; is a vector.
     *
     * @param U a {@link Matrix} in upper-triangular form, that is, {@code U.isUpperTriangular()} returns {@code true}
     * @param c a {@link Vector} of values which must have the same number of elements as U has rows
     * @return the solution vector
     * @param <T> the type of U and the result
     */
    public static <T extends Numeric> Vector<T> backSubstitution(Matrix<T> U, Vector<? super T> c) {
        if (U.rows() != c.length()) throw new IllegalArgumentException("Matrix U must have the same number of rows as elements in Vector c");
        if (U.rows() != U.columns() || !U.isUpperTriangular()) throw new IllegalArgumentException("Matrix U must be upper-triangular and square");
        Class<T> clazz = (Class<T>) ((Class) ((ParameterizedType) U.getClass()
                .getGenericSuperclass()).getActualTypeArguments()[0]);
        if (clazz == null) clazz = (Class<T>) U.valueAt(0L, 0L).getClass();
        NumericHierarchy h = NumericHierarchy.forNumericType(clazz);
        Vector<T> x;
        switch (h) {
            case COMPLEX:
                x = (Vector<T>) new ComplexVector(c.length());
                break;
            case REAL:
                x = (Vector<T>) new RealVector(c.length());
                break;
            default:
                throw new UnsupportedOperationException("No vector type defined for " + clazz.getTypeName());
        }
        final long n = U.rows();  // also U.columns() would work since U is an upper-triangular square matrix

        for (long i = n - 1L; i >= 0L; i--) {
            Numeric sum = ExactZero.getInstance(c.getMathContext());
            for (long j = i + 1L; j < n; j++) {
                sum = sum.add(x.elementAt(j).multiply(U.valueAt(i, j)));
            }
            try {
                T value = (T) c.elementAt(i).subtract(sum).multiply(U.valueAt(i, i).inverse()).coerceTo(clazz);
                x.setElementAt(value, i);
            } catch (CoercionException fatal) {
                Logger.getLogger(MathUtils.class.getName()).log(Level.SEVERE,
                        "While computing the " + i + "th element of x for Ux = c, c = " + c, fatal);
                throw new ArithmeticException("Coercion error while computing " + i + "th element of solution vector");
            }
        }

        return x;
    }

    private static <T extends Numeric> ColumnVector<T> columnVectorFrom(Vector<? super T> source, Class<T> clazz) {
        final long THRESHOLD = 1_000L;
        List<T> converted = new LinkedList<>();
        try {
            for (long index = 0L; index < source.length(); index++) {
                T value = (T) source.elementAt(index).coerceTo(clazz);
                converted.add(value);
            }
        } catch (CoercionException e) {
            throw new IllegalStateException("While converting " + source + " to a column vector", e);
        }
        if (source.length() > THRESHOLD) return new ListColumnVector<>(converted);
        return new ArrayColumnVector<>(converted);
    }

    private static ColumnVector<? extends Numeric> columnVectorFrom(Vector<? extends Numeric> source) {
        final long THRESHOLD = 1_000L;
        List<Numeric> temp = new LinkedList<>();
        LongStream.range(0L, source.length()).mapToObj(source::elementAt).forEach(temp::add);
        if (source.length() > THRESHOLD) return new ListColumnVector<>(temp);
        return new ArrayColumnVector<>(temp);
    }

    private static Set<Numeric> computeEigenvaluesFor2x2(Matrix<? extends Numeric> matrix) {
        Numeric diagSum = matrix.valueAt(0L, 0L).add(matrix.valueAt(1L, 1L));
        Numeric diagDiff = matrix.valueAt(0L, 0L).subtract(matrix.valueAt(1L, 1L));
        final IntegerType four = new IntegerImpl(BigInteger.valueOf(4L));
        Numeric term2 = diagDiff.multiply(diagDiff)
                .add(four.multiply(matrix.valueAt(0L, 1L)).multiply(matrix.valueAt(1L, 0L)))
                .sqrt();
        final IntegerType two = new IntegerImpl(TWO);
        return Set.of(diagSum.add(term2).divide(two), diagSum.subtract(term2).divide(two));
    }

    private static Set<ComplexType> computeEigenvaluesFor3x3Symmetric(Matrix<? extends Numeric> matrix) {
        final MathContext ctx = matrix.valueAt(0L, 0L).getMathContext();
        final RealType two = new RealImpl(decTWO, ctx);
        final RealType three = new RealImpl(BigDecimal.valueOf(3L), ctx);
        final RealType six = new RealImpl(BigDecimal.valueOf(6L), ctx);
        final RealType one = new RealImpl(BigDecimal.ONE, ctx);
        final RealType negone = new RealImpl(BigDecimal.valueOf(-1L), ctx);
        final RealType pi = Pi.getInstance(ctx);
        try {
            ComplexType triangleSq = (ComplexType) matrix.valueAt(0L, 1L).multiply(matrix.valueAt(0L, 1L))
                    .add(matrix.valueAt(0L, 2L).multiply(matrix.valueAt(0L, 2L)))
                    .add(matrix.valueAt(1L, 2L).multiply(matrix.valueAt(1L, 2L))).coerceTo(ComplexType.class);
            ComplexType q = (ComplexType) matrix.trace().divide(three).coerceTo(ComplexType.class);
            ComplexType intermediate = (ComplexType) LongStream.range(0L, matrix.rows()).mapToObj(idx -> matrix.valueAt(idx, idx))
                    .map(z -> z.multiply(z)).reduce(triangleSq.multiply(two), Numeric::add).coerceTo(ComplexType.class);
            ComplexType p = (ComplexType) intermediate.divide(six).sqrt();
            Matrix<ComplexType> A = new ParametricMatrix<>(matrix.rows(), matrix.columns(), (row, column) -> {
                try {
                    return (ComplexType) matrix.valueAt(row, column).coerceTo(ComplexType.class);
                } catch (CoercionException e) {
                    throw new ArithmeticException("Unable to coerce element at " + row + ", " + column);
                }
            });
            Matrix<ComplexType> B = A.subtract(lambdaMatrix(3L, q));
            ComplexType r = (ComplexType) B.determinant().divide(two);
            ComplexType phi;
            if (r.isCoercibleTo(RealType.class) && isOfType(matrix, RealType.class)) {
                // if the matrix is real, ensure we keep the value of phi within bounds
                RealType reR = r.real();
                if (reR.compareTo(negone) <= 0) phi = new ComplexPolarImpl((RealType) pi.divide(three));
                else if (reR.compareTo(one) >= 0) phi = new ComplexRectImpl(new RealImpl(BigDecimal.ZERO, ctx));
                else phi = (ComplexType) arccos(reR).divide(three).coerceTo(ComplexType.class);
            } else {
                phi = (ComplexType) arccos(r).divide(three);
            }
            ComplexType eig1 = (ComplexType) q.add(p.multiply(cos(phi)).multiply(two));
            ComplexType nextAngle = (ComplexType) phi.add(two.multiply(pi).divide(three));
            ComplexType eig2 = (ComplexType) q.add(p.multiply(cos(nextAngle)).multiply(two));
            // since A.trace() = eig1 + eig2 + eig3, we can solve for eig3
            ComplexType eig3 = (ComplexType) A.trace().subtract(eig1).subtract(eig2);
            return Set.of(eig1, eig2, eig3);
        } catch (CoercionException e) {
            throw new ArithmeticException("While computing eigenvalues: " + e.getMessage());
        }
    }

    private static Matrix<ComplexType> lambdaMatrix(long dimension, ComplexType lambda) {
        final ComplexType zero = new ComplexRectImpl(new RealImpl(BigDecimal.ZERO, lambda.getMathContext()));
        return new ParametricMatrix<>(dimension, dimension, (row, column) -> {
            if (row.longValue() == column.longValue()) return lambda;
            return zero;
        });
    }

    /**
     * Decompose a matrix A into Q and R such that A&nbsp;=&nbsp;QR,
     * where Q is an orthogonal matrix and R is an upper-triangular matrix.
     * This implementation uses the Gram-Schmidt process.
     * @param A the matrix to be decomposed
     * @return a {@link List} containing Q and R, in that order
     * @param <T> the type of the elements of {@code A}
     * @see <a href="https://en.wikipedia.org/wiki/QR_decomposition">the Wikipedia article on QR decomposition</a>
     */
    public static <T extends Numeric> List<Matrix<T>> computeQRdecomposition(Matrix<T> A) {
        ColumnarMatrix<T> Q = new ColumnarMatrix<>();
        for (long col = 0L; col < A.columns(); col++) {
            ColumnVector<T> cvec = new ArrayColumnVector<>(A.getColumn(col).normalize());
            Q.append(cvec);
        }
        Matrix<T> R = isOfType(A, ComplexType.class) ? (Matrix<T>) conjugateTranspose(Q).multiply((Matrix<ComplexType>) A) : Q.transpose().multiply(A);
        return List.of(Q, R);
    }

    /**
     * Multiply two matrices using the Strassen/Winograd algorithm.
     * This algorithm uses 7 multiplications instead of the usual 8
     * at each stage of recursion.
     *
     * @param lhs the left-hand matrix in the multiplication
     * @param rhs the right-hand matrix in the multiplication
     * @return the product of {@code lhs} and {@code rhs}
     * @see <a href="https://en.wikipedia.org/wiki/Strassen_algorithm">the Wikipedia article on Strassen's algorithm</a>
     */
    public static Matrix<RealType> efficientMatrixMultiply(Matrix<RealType> lhs, Matrix<RealType> rhs) {
        if (lhs.rows() == rhs.rows() && lhs.columns() == rhs.columns() && lhs.rows() == lhs.columns()) {
            // we have two square matrices of equal dimension
            if (BigInteger.valueOf(lhs.rows()).bitCount() == 1) {
                // rows and columns are powers of 2
                if (lhs.rows() == 2L) {
                    // we have 2×2 matrices
                    final RealType a = lhs.valueAt(0L, 0L);
                    final RealType b = lhs.valueAt(0L, 1L);
                    final RealType c = lhs.valueAt(1L, 0L);
                    final RealType d = lhs.valueAt(1L, 1L);
                    final RealType A = rhs.valueAt(0L, 0L);
                    final RealType C = rhs.valueAt(0L, 1L);
                    final RealType B = rhs.valueAt(1L, 0L);
                    final RealType D = rhs.valueAt(1L, 1L);

                    // using the Winograd form
                    final RealType u = (RealType) c.subtract(a).multiply(C.subtract(D));
                    final RealType v = (RealType) c.add(d).multiply(C.subtract(A));
                    final RealType aA_product = (RealType) a.multiply(A);
                    final RealType w = (RealType) aA_product.add(c.add(d).subtract(a).multiply(A.add(D).subtract(C)));

                    RealType[][] result = new RealType[2][2];
                    result[0][0] = (RealType) aA_product.add(b.multiply(B));
                    result[0][1] = (RealType) w.add(v).add(a.add(b).subtract(c).subtract(d).multiply(D));
                    result[1][0] = (RealType) w.add(u).add(B.add(C).subtract(A).subtract(D).multiply(d));
                    result[1][1] = (RealType) w.add(u).add(v);
                    return new BasicMatrix<>(result);
                } else {
                    // recursively drill down using the same relations as shown above for the scalar case
                    final Matrix<RealType> a = new SubMatrix<>(lhs, 0L, 0L, lhs.rows()/2L - 1L, lhs.columns()/2L - 1L); // 0, 0
                    final Matrix<RealType> b = new SubMatrix<>(lhs, 0L, lhs.columns()/2L, lhs.rows()/2L - 1L, lhs.columns() - 1L); // 0, 1
                    final Matrix<RealType> c = new SubMatrix<>(lhs, lhs.rows()/2L, 0L, lhs.rows() - 1L, lhs.columns()/2L - 1L); // 1, 0
                    final Matrix<RealType> d = new SubMatrix<>(lhs, lhs.rows()/2L, lhs.columns()/2L, lhs.rows() - 1L, lhs.columns() - 1L); // 1, 1
                    final Matrix<RealType> A = new SubMatrix<>(rhs, 0L, 0L, rhs.rows()/2L - 1L, rhs.columns()/2L - 1L); // 0, 0
                    final Matrix<RealType> C = new SubMatrix<>(rhs, 0L, rhs.columns()/2L, rhs.rows()/2L - 1L, rhs.columns() - 1L); // 0, 1
                    final Matrix<RealType> B = new SubMatrix<>(rhs, rhs.rows()/2L, 0L, rhs.rows() - 1L, rhs.columns()/2L - 1L); // 1, 0
                    final Matrix<RealType> D = new SubMatrix<>(rhs, rhs.rows()/2L, rhs.columns()/2L, rhs.rows() - 1L, rhs.columns() - 1L); // 1, 1

                    // using the Winograd form
                    final Matrix<RealType> u = efficientMatrixMultiply(c.subtract(a), C.subtract(D));
                    final Matrix<RealType> v = efficientMatrixMultiply(c.add(d), C.subtract(A));
                    final Matrix<RealType> aAprod = efficientMatrixMultiply(a, A);
                    final Matrix<RealType> w = aAprod.add(efficientMatrixMultiply(c.add(d).subtract(a), A.add(D).subtract(C)));

                    Matrix<RealType>[][] result = (Matrix<RealType>[][]) new Matrix[2][2];
                    result[0][0] = aAprod.add(efficientMatrixMultiply(b, B));
                    result[0][1] = w.add(v).add(efficientMatrixMultiply(a.add(b).subtract(c).subtract(d), D));
                    result[1][0] = w.add(u).add(efficientMatrixMultiply(B.add(C).subtract(A).subtract(D), d));
                    result[1][1] = w.add(u).add(v);
                    return new AggregateMatrix<>(result);
                }
            } else {
                // matrices are square, but rows and columns are not a power of 2
                long resize = smallestPowerOf2GTE(lhs.rows());
                final RealType zero = new RealImpl(BigDecimal.ZERO, lhs.valueAt(0L, 0L).getMathContext());
                Matrix<RealType> left = new PaddedMatrix<>(lhs, resize, resize, zero);
                Matrix<RealType> right = new PaddedMatrix<>(rhs, resize, resize, zero);
                Matrix<RealType> result = efficientMatrixMultiply(left, right);
                // pick off the extraneous zero columns/rows
                return new SubMatrix<>(result, 0L, 0L, lhs.rows(), rhs.columns());
            }
        }
        // if the above conditions are not met, do it the old-fashioned way
        return lhs.multiply(rhs);
    }

    /**
     * Compute the smallest power of 2 that is greater than or
     * equal to a given value.
     *
     * @param input the given value
     * @return the smallest power of 2 ≥ {@code input}
     */
    public static long smallestPowerOf2GTE(long input) {
        if (input < 0L) throw new IllegalArgumentException("Negative values not supported");
        double intermediate = Math.ceil(Math.log(input) / Math.log(2d));
        return (long) Math.pow(2d, intermediate);
    }
    
    private static final Range<RealType> epsilonRange = new Range<>(new RealImpl("0"), new RealImpl("1"), BoundType.EXCLUSIVE);
    
    /**
     * Tests if two real values are within &epsilon; of each other.  This is
     * useful in cases where rounding error or truncation can render a test
     * using {@link RealImpl#equals(Object) the default test for equality}
     * entirely useless.
     * 
     * @param A the first real value to test for equality
     * @param B the second real value to test for equality
     * @param epsilon the largest allowable delta between A and B for them to be
     *  considered equal, a fractional value between 0 and 1 (exclusive)
     * @return true if the supplied values have a difference &lt; &epsilon;
     */
    public static boolean areEqualToWithin(RealType A, RealType B, RealType epsilon) {
        if (epsilon.sign() != Sign.POSITIVE || !epsilonRange.contains(epsilon)) {
            throw new IllegalArgumentException("Argument epsilon must satisfy 0 < \uD835\uDF00 \u226A 1"); // U+1D700 MATHEMATICAL ITALIC SMALL EPSILON
        }

        try {
            final RealType difference = (RealType) A.subtract(B).magnitude().coerceTo(RealType.class);
            return difference.compareTo(epsilon) < 0;
        } catch (CoercionException e) {
            throw new IllegalStateException("Cannot coerce delta to a real value", e);
        }
    }
    
    /**
     * Tests if two real vectors are equal according to
     * {@link #areEqualToWithin(RealType, RealType, RealType) }. The two
     * vectors are compared element-wise, and if any pair of elements has
     * a difference &ge; &epsilon;, the comparison fails fast and returns false.
     * 
     * @param A the first real-valued vector to test for equality
     * @param B the second real-valued vector to test for equality
     * @param epsilon a value between 0 and 1, exclusive, denoting the maximum
     *  difference allowed between any pair of elements for A and B to be
     *  considered equal.
     * @return true if the supplied vectors are of equal length and all the
     *  elements of A are within &epsilon; of their counterparts in B 
     */
    public static boolean areEqualToWithin(Vector<RealType> A, Vector<RealType> B, RealType epsilon) {
        if (A.length() != B.length()) return false;
        for (long index = 0L; index < A.length(); index++) {
            if (!MathUtils.areEqualToWithin(A.elementAt(index), B.elementAt(index), epsilon)) return false;
        }
        return true;
    }
    
    /**
     * Tests if two real-valued matrices are equal according to
     * {@link #areEqualToWithin(RealType, RealType, RealType) }.
     * This method will attempt to use the most optimal strategy for comparing
     * two matrices (still a work in progress).  It recognizes any matrices
     * annotated as {@link Columnar} and will attempt to adjust its access
     * pattern accordingly.
     * 
     * @param A the first real-valued matrix to test for equality
     * @param B the second real-valued matrix to test for equality
     * @param epsilon the maximum delta allowed between corresponding elements,
     *  a fractional value between 0 and 1 (exclusive)
     * @return true if all elements of A are within &epsilon; of their counterparts in B
     */
    public static boolean areEqualToWithin(Matrix<RealType> A, Matrix<RealType> B, RealType epsilon) {
        if (A.rows() != B.rows() || A.columns() != B.columns()) return false;
        if (A.getClass().isAnnotationPresent(Columnar.class) && B.getClass().isAnnotationPresent(Columnar.class)) {
            // go by columns instead of by rows (optimal for any columnar store)
            for (long column = 0L; column < A.columns(); column++) {
                if (!MathUtils.areEqualToWithin(A.getColumn(column),
                        (Vector<RealType>) B.getColumn(column), epsilon)) return false;
            }
            return true;
        } else {
            // default behavior is to compare by rows
            for (long row = 0L; row < A.rows(); row++) {
                if (!MathUtils.areEqualToWithin(A.getRow(row),
                        (Vector<RealType>) B.getRow(row), epsilon)) return false;
            }
            return true;
        }
    }

    public static IntegerType trunc(Numeric val) {
        NumericHierarchy hval = NumericHierarchy.forNumericType(val.getClass());
        switch (hval) {
            case RATIONAL:
                IntegerType[] wholeAndFrac = ((RationalType) val).divideWithRemainder();
                return wholeAndFrac[0];
            case REAL:
                RealType rval = (RealType) val;
                return new IntegerImpl(rval.asBigDecimal().toBigInteger(),
                        rval.isExact() && rval.isCoercibleTo(IntegerType.class));
            case INTEGER:
                return (IntegerType) val;
            default:
                throw new ArithmeticException("Cannot truncate " + val);
        }
    }

    /**
     * Compute the natural logarithm of a complex value.
     * @param z the value we want to calculate the natural logarithm of
     * @return the natural logarithm as a {@link ComplexType} value
     */
    public static ComplexType ln(ComplexType z) {
        return new ComplexRectImpl(ln(z.magnitude()), z.argument());
    }

    public static Numeric arctan(Numeric z) {
        if (z.isCoercibleTo(RealType.class)) {
            try {
                RealType coerced = (RealType) z.coerceTo(RealType.class);
                return arctan(coerced);
            } catch (CoercionException e) {
                throw new IllegalStateException("Failure to coerce after check for coercion", e);
            }
        }
        // otherwise, complex values get handled here
        final ComplexType i = ImaginaryUnit.getInstance(z.getMathContext());
        final ComplexType coeff = (ComplexType) i.negate().divide(new RealImpl(decTWO, z.getMathContext()));
        ComplexType frac = (ComplexType) i.subtract(z).divide(i.add(z));
        return coeff.multiply(ln(frac));
    }

    /**
     * The main implementation of {@link #arctan(Numeric)} had one flaw:
     * For non-polar complex numbers, {@link ComplexType#argument()} requires
     * computation using {@link #atan2(RealType, RealType)}, which delegated
     * to {@link #arctan(Numeric)}.  Unfortunately, that method computes the
     * result of ln(z) using {@code z.argument()}, which results in an
     * infinite loop.<br/>
     * One solution is to compute arctan() using some kind of power series.
     * We've already implemented Maclaurin series (special case of Taylor
     * series) for cos() and sin(), but Euler discovered a series for arctan()
     * that converges faster.  That's what's implemented here, though only
     * for real values.
     * @param x a real value
     * @return the computed value of atan(x)
     */
    public static RealType arctan(RealType x) {
        final MathContext compCtx = new MathContext(x.getMathContext().getPrecision() * 2,
                x.getMathContext().getRoundingMode());
        BigDecimal xsq = x.asBigDecimal().multiply(x.asBigDecimal(), compCtx);
        BigDecimal coeff = x.asBigDecimal().divide(xsq.add(BigDecimal.ONE, compCtx), compCtx);
        long nterms = compCtx.getPrecision() * 2L + 3L;
        BigDecimal val = coeff.multiply(atanEulerSum(x, nterms), compCtx);
        RealImpl result = new RealImpl(val.round(x.getMathContext()), x.getMathContext(), false);
        result.setIrrational(x.isIrrational() || classifyIfIrrational(val, compCtx));
        return result;
    }

    private static BigDecimal atanEulerSum(RealType x, long terms) {
        if (terms < 1L) throw new IllegalArgumentException("Requested number of terms is " + terms);
        final MathContext sumCtx = new MathContext(x.getMathContext().getPrecision() * 2 + 2,
                x.getMathContext().getRoundingMode());
        BigDecimal accum = BigDecimal.ZERO;
        for (long n = 0L; n < terms; n++) {
            accum = accum.add(atanEulerProduct(x, n), sumCtx);
        }
        return accum;
    }

    private static BigDecimal atanEulerProduct(RealType x, long n) {
        if (n < 0) throw new IllegalArgumentException("Product undefined for n < 0");
        if (n == 0) return BigDecimal.ONE;  // the empty product
        final MathContext prodCtx = new MathContext(x.getMathContext().getPrecision() * 2 + 4,
                x.getMathContext().getRoundingMode());
        BigDecimal accum = BigDecimal.ONE;
        BigDecimal xsq = x.asBigDecimal().multiply(x.asBigDecimal(), prodCtx);
        for (long k = 1L; k <= n; k++) {
            BigDecimal kval = BigDecimal.valueOf(k);
            BigDecimal twoKplus1 = BigDecimal.valueOf(2L * k + 1L);
            BigDecimal numerator = decTWO.multiply(kval, prodCtx).multiply(xsq, prodCtx);
            BigDecimal denominator = xsq.add(BigDecimal.ONE, prodCtx).multiply(twoKplus1, prodCtx);
            accum = accum.multiply(numerator, prodCtx).divide(denominator, prodCtx);
        }
        return accum;
    }


    public static Numeric atan2(RealType y, RealType x) {
        final RealType two  = new RealImpl(decTWO, y.getMathContext());
        final Numeric term = x.multiply(x).add(y.multiply(y)).sqrt();
        if (x.sign() == Sign.POSITIVE) {
            return two.multiply(arctan(y.divide(term.add(x))));
        }
        if (x.sign() != Sign.POSITIVE && !Zero.isZero(y)) {
            return two.multiply(arctan(term.subtract(x).divide(y)));
        }
        if (x.sign() == Sign.NEGATIVE && Zero.isZero(y)) {
            // use the MathContext of the non-zero argument in this case
            return Pi.getInstance(x.getMathContext());
        }
        // undefined otherwise, i.e., x = 0 and y = 0
        throw new ArithmeticException(String.format("Could not calculate atan2 for y = %1$s, x = %2$s",
                y, x));
    }

    private static final Range<RealType> acosRange = new Range<>(new RealImpl(BigDecimal.valueOf(-1L)),
            new RealImpl(BigDecimal.ONE), BoundType.INCLUSIVE);

    public static Numeric arccos(Numeric z) {
        if (z instanceof RealType && !acosRange.contains((RealType) z)) {
            throw new ArithmeticException("arccos input range is " + acosRange + " for real-valued input");
        }
        // use the logarithmic form, which extends cleanly into the complex plane
        final ComplexType i = ImaginaryUnit.getInstance(z.getMathContext());
        final ComplexType negi = i.negate();
        try {
            final ComplexType one = (ComplexType) One.getInstance(z.getMathContext()).coerceTo(ComplexType.class);
            ComplexType term = (ComplexType) i.multiply(one.subtract(z.multiply(z)).sqrt()).add(z).coerceTo(ComplexType.class);
            Numeric result = negi.multiply(ln(term));
            if (result instanceof ComplexType && result.isCoercibleTo(RealType.class)) {
                // the result is a real, so return it as a RealType
                return result.coerceTo(RealType.class);
            }
            return result;
        } catch (CoercionException e) {
            throw new ArithmeticException("Type coercion error while computing ln(" + z + "): " + e.getMessage());
        }
    }

    private static RealType computeTrigSum(RealType x, Function<Long, IntegerType> subTerm) {
        final MathContext calcCtx = new MathContext(x.getMathContext().getPrecision() * 2, x.getMathContext().getRoundingMode());
        Numeric accum = ExactZero.getInstance(calcCtx);
        // we must compute at least 4 terms (polynomial order 7) to get an acceptable result within the input range
        final int termLimit = Math.max(4, calculateOptimumNumTerms(x.getMathContext(), subTerm));
        final RealType x_upscaled = new RealImpl(x.asBigDecimal(), calcCtx, x.isExact());
        for (int i = 0; i < termLimit; i++) {
            IntegerType subVal = subTerm.apply((long) i);
            if (i % 2 == 0) {
                accum = accum.add(computeIntegerExponent(x_upscaled, subVal).divide(factorial(subVal)));
            } else {
                accum = accum.subtract(computeIntegerExponent(x_upscaled, subVal).divide(factorial(subVal)));
            }
        }
        try {
            RealType raw = (RealType) accum.coerceTo(RealType.class);
            return round(raw, x.getMathContext());
        } catch (CoercionException e) {
            throw new ArithmeticException("While coercing computed sum " + accum + ": " + e.getMessage());
        }
    }

    private static class TrigTermCountKey {
        private final MathContext mctx;
        private final Function<Long, IntegerType> lambda;

        public TrigTermCountKey(MathContext mctx, Function<Long, IntegerType> lambda) {
            this.mctx = mctx;
            this.lambda = lambda;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            TrigTermCountKey that = (TrigTermCountKey) o;
            return mctx.equals(that.mctx) && lambda.equals(that.lambda);
        }

        @Override
        public int hashCode() {
            return Objects.hash(mctx, lambda);
        }
    }

    private static final Map<TrigTermCountKey, Integer> optimumTermCounts = new ConcurrentHashMap<>();

    /**
     * Given a {@link MathContext} and a mapping function to map from integer indices to subterms
     * within a power series expansion, determine the optimum number of terms to compute so that
     * the error is kept below the resolution threshold determined by the {@link MathContext}.
     * Note that this method computes a uniform estimate of the error, based on a Taylor expansion
     * centered at zero within the interval (&minus;&pi;, &pi;].  In this case,
     * the range r&nbsp;=&nbsp;&pi;, so the estimated error term is &lt;&thinsp;r<sup>N</sup>/N!
     * where N is determined by the mapping function.
     * @param ctx       the math context for computing error terms
     * @param idxMapper the mapping function for summation indices
     * @return the estimated number of terms required to minimize error
     * @see <a href="https://en.wikipedia.org/wiki/Taylor's_theorem#Estimates">Wikipedia article on Taylor's Theorem,
     *  section on estimates for the remainder</a>
     */
    private static int calculateOptimumNumTerms(MathContext ctx, Function<Long, IntegerType> idxMapper) {
        final TrigTermCountKey key = new TrigTermCountKey(ctx, idxMapper);
        if (optimumTermCounts.containsKey(key)) return optimumTermCounts.get(key);
        Logger.getLogger(MathUtils.class.getName()).log(Level.INFO, "Cache miss for MathContext {0}; calculating optimum number of power series terms.", ctx);
        final MathContext calcContext = new MathContext(ctx.getPrecision() * 2, ctx.getRoundingMode());
        final RealType realTwo = new RealImpl(BigDecimal.valueOf(2L), calcContext);
        final RealType maxError = (RealType) computeIntegerExponent(TEN, 1 - ctx.getPrecision(), calcContext).divide(realTwo);
        final Pi pi = Pi.getInstance(calcContext);
        int k = 2;
        do {
            IntegerType kVal = idxMapper.apply((long) k);
            Numeric error = computeIntegerExponent(pi, kVal).divide(factorial(kVal));
            if (error instanceof RationalType) {
                // if we got back a rational, it's because the exponent we calculated has no fractional digits left after rounding
                Logger.getLogger(MathUtils.class.getName()).log(Level.WARNING,
                        "Compute MathContext {0} does not provide sufficient resolution to represent \uD835\uDF0B^{1}.",
                        new Object[] {calcContext, kVal});
                error = new RealImpl((RationalType) error); // to avoid coerceTo() penalties
            }
            Logger.getLogger(MathUtils.class.getName()).log(Level.FINE, "k = {0} error = {1}", new Object[] { k, error });
            if (((RealType) error).compareTo(maxError) < 0) {
                Logger.getLogger(MathUtils.class.getName()).log(Level.INFO, "Recommend computing {0} power series terms for MathContext {1}.",
                        new Object[] { k, ctx });
                optimumTermCounts.put(key, k);
                return k;
            }
        } while (++k < calcContext.getPrecision());
        throw new ArithmeticException("Cannot determine optimum number of power series terms for keeping error < " + maxError);
    }

    private static RealType mapToInnerRange(RealType input, Range<RealType> internalRange) {
        if (internalRange.contains(input)) return input;

        final RealType period = (RealType) internalRange.getUpperBound().subtract(internalRange.getLowerBound());
        RealType temp = input;
        while (internalRange.isBelow(temp)) {
            temp = (RealType) temp.add(period);
        }
        while (internalRange.isAbove(temp)) {
            temp = (RealType) temp.subtract(period);
        }
        return temp;
    }

    public static RealType cos(RealType x) {
        if (x instanceof Pi) return new RealImpl(BigDecimal.valueOf(-1L), x.getMathContext());
        RealType inBounds = mapToInnerRange(x, RangeUtils.getAngularInstance(x.getMathContext()));
        return computeTrigSum(inBounds, n -> new IntegerImpl(BigInteger.valueOf(2L * n)));
    }

    public static RealType sin(RealType x) {
        if (x instanceof Pi) return new RealImpl(BigDecimal.ZERO, x.getMathContext());
        RealType inBounds = mapToInnerRange(x, RangeUtils.getAngularInstance(x.getMathContext()));
        return computeTrigSum(inBounds, n -> new IntegerImpl(BigInteger.valueOf(2L * n + 1L)));
    }

    public static RealType tan(RealType x) {
        final MathContext ctx = x.getMathContext();
        final Pi pi = Pi.getInstance(ctx);
        final RealType epsilon = computeIntegerExponent(TEN, 1 - ctx.getPrecision(), ctx);
        // check for zero crossings before incurring the cost of computing
        // an in-range argument or calculating the sin() and cos() power series
        RealType argOverPi = x.divide(pi).magnitude();
        if (((RealType) argOverPi.subtract(argOverPi.floor())).compareTo(epsilon) < 0) {
            // tan(x) has zero crossings periodically at x=k𝜋 ∀ k ∈ 𝕴
            return new RealImpl(BigDecimal.ZERO, ctx);
        }
        Range<RealType> range = RangeUtils.getTangentInstance(ctx);
        RealType inBounds = mapToInnerRange(x, range);
        // check if we're within epsilon of the limits of our input range
        // if so, tan(x) blows up to infinity
        if (areEqualToWithin(inBounds, range.getLowerBound(), epsilon)) {
            return RealInfinity.getInstance(Sign.NEGATIVE, ctx);
        } else if (areEqualToWithin(inBounds, range.getUpperBound(), epsilon)) {
            return RealInfinity.getInstance(Sign.POSITIVE, ctx);
        }
        return (RealType) sin(inBounds).divide(cos(inBounds));
    }

    public static ComplexType cos(ComplexType z) {
        if (z.isCoercibleTo(RealType.class)) return new ComplexRectImpl(cos(z.real()));
        final ComplexType i = ImaginaryUnit.getInstance(z.getMathContext());
        final ComplexType iz = (ComplexType) i.multiply(z);
        final Euler e = Euler.getInstance(z.getMathContext());
        return (ComplexType) e.exp(iz).add(e.exp(iz.negate())).divide(new RealImpl(decTWO, z.getMathContext()));
    }

    public static ComplexType sin(ComplexType z) {
        if (z.isCoercibleTo(RealType.class)) return new ComplexRectImpl(sin(z.real()));
        final ComplexType i = ImaginaryUnit.getInstance(z.getMathContext());
        final ComplexType iz = (ComplexType) i.multiply(z);
        final Euler e = Euler.getInstance(z.getMathContext());
        return (ComplexType) e.exp(iz).subtract(e.exp(iz.negate())).divide(new RealImpl(decTWO, z.getMathContext()).multiply(i));
    }

    public static Comparator<Numeric> obtainGenericComparator() {
        return new Comparator<>() {
            @Override
            public int compare(Numeric A, Numeric B) {
                if (A instanceof Comparable && B instanceof Comparable) {
                    Class<? extends Numeric> classA = A.getClass();
                    Class<? extends Numeric> classB = B.getClass();
                    NumericHierarchy h1 = NumericHierarchy.forNumericType(classA);
                    NumericHierarchy h2 = NumericHierarchy.forNumericType(classB);
                    // if A or B is a direct subclass of Numeric, delegate to
                    // its compareTo() method
                    if (h1 == null) {
                        Comparable<Numeric> Acomp = (Comparable<Numeric>) A;
                        return Acomp.compareTo(B);
                    } else if (h2 == null) {
                        Comparable<Numeric> Bcomp = (Comparable<Numeric>) B;
                        return -Bcomp.compareTo(A);
                    }
                    // otherwise, we need to do type coercion
                    try {
                        if (h1.compareTo(h2) >= 0) {
                            Comparable<Numeric> Bconv = (Comparable<Numeric>) B.coerceTo(h1.getNumericType());
                            return -Bconv.compareTo(A);
                        } else {
                            Comparable<Numeric> Aconv = (Comparable<Numeric>) A.coerceTo(h2.getNumericType());
                            return Aconv.compareTo(B);
                        }
                    } catch (CoercionException ce) {
                        Logger.getLogger(MathUtils.class.getName()).log(Level.SEVERE, "No common type found for {0} and {1}.",
                                new Object[] { h1, h2 });
                        throw new IllegalArgumentException("Failure to coerce arguments to a common type", ce);
                    }
                } else {
                    throw new IllegalArgumentException("Numeric subtype must be comparable");
                }
            }

            @Override
            public boolean equals(Object obj) {
                if (obj instanceof Comparator) {
                    return obj.getClass() == this.getClass();
                }
                return false;
            }
        };
    }

    private static final Map<Class<? extends UnaryFunction>, Class<? extends UnaryFunction>> inverses =
            new HashMap<>();

    static {
        inverses.put(Negate.class, Negate.class);
        inverses.put(Exp.class, NaturalLog.class);
        inverses.put(NaturalLog.class, Exp.class);
    }

    public static Class<? extends UnaryFunction> inverseFunctionFor(Class<? extends UnaryFunction> fClazz) {
        return inverses.get(fClazz);
    }
}
