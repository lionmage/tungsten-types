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

//import ch.obermuhlner.math.big.BigDecimalMath;
import tungsten.types.*;
import tungsten.types.annotations.Columnar;
import tungsten.types.exceptions.CoercionException;
import tungsten.types.matrix.impl.BasicMatrix;
import tungsten.types.numerics.*;
import tungsten.types.numerics.impl.*;
import tungsten.types.set.impl.NumericSet;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import static tungsten.types.Range.BoundType;

/**
 * A utility class to hold commonly used functions and algorithms.
 *
 * @author tarquin
 */
public class MathUtils {
    public static final String THETA = "\u03B8";
    private static final BigInteger TWO = BigInteger.valueOf(2L);
    
    private static final Map<Long, BigInteger> factorialCache = new HashMap<>();
    
    public static IntegerType factorial(IntegerType n) {
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
     * @return the highest cache key given the search parameter
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
    
    public static RealType computeIntegerExponent(Numeric x, IntegerType n) {
        final RealType result;
        final BigInteger exponent = n.asBigInteger();
        
        if (exponent.equals(BigInteger.ZERO)) {
            result = new RealImpl(BigDecimal.ONE, x.getMathContext());
        } else {
            try {
                result = computeIntegerExponent((RealType) x.coerceTo(RealType.class), exponent.intValueExact());
            } catch (CoercionException ex) {
                Logger.getLogger(MathUtils.class.getName()).log(Level.SEVERE, "Failed to coerce argument to RealType.", ex);
                throw new ArithmeticException("Coercion failed: " + ex.getMessage());
            }
        }
        
        return result;
    }
    
    public static ComplexType computeIntegerExponent(ComplexType x, IntegerType n) {
        final BigInteger exponent = n.asBigInteger();
        
        long count = exponent.longValueExact() - 1L;
        ComplexType accum = (ComplexType) x;
        try {
            while (count > 0) {
                accum = (ComplexType) accum.multiply(accum).coerceTo(ComplexType.class);
                count--;
            }
        } catch (CoercionException ce) {
            Logger.getLogger(MathUtils.class.getName()).log(Level.SEVERE, "Failed to coerce accumulator to a complex value.", ce);
            throw new ArithmeticException("Coercion failed: " + ce.getMessage());
        }
        return accum;
    }
    
    /**
     * Compute x<sup>n</sup>.
     * @param x the value to take the exponent of
     * @param n the integer exponent
     * @param mctx the {@link MathContext} for computing the exponent
     * @return x raised to the n<sup>th</sup> power
     */
    public static RealType computeIntegerExponent(RealType x, int n, MathContext mctx) {
        if (n == 0) return new RealImpl(BigDecimal.ONE, mctx);
        if (n == 1) return x;
        try {
            if (n == -1) {
                return (RealType) x.inverse().coerceTo(RealType.class);
            }
            
            Numeric intermediate = new RealImpl(x.magnitude().asBigDecimal(), mctx);
            Numeric factor = intermediate;
            for (int idx = 2; idx <= Math.abs(n); idx++) {
                intermediate = intermediate.multiply(factor);
            }
            if (n < 0) intermediate = intermediate.inverse();
            // if n is odd, preserve original sign
            if (x.sign() == Sign.NEGATIVE && n % 2 != 0) intermediate = intermediate.negate();
            return (RealType) intermediate.coerceTo(RealType.class);
        } catch (CoercionException ex) {
            Logger.getLogger(MathUtils.class.getName()).log(Level.SEVERE, "Unrecoverable exception thrown while computing integer exponent.", ex);
            throw new ArithmeticException("Failure to coerce to RealType.");
        }
    }

    public static ComplexType computeIntegerExponent(ComplexType x, long n, MathContext mctx) {
        if (n == 0L) return new ComplexRectImpl(new RealImpl(BigDecimal.ONE, mctx));
        if (n == 1L) return x;
        try {
            if (n == -1L) {
                return (ComplexType) x.inverse().coerceTo(ComplexType.class);
            }
            if (x instanceof ComplexPolarImpl) {
                // computing powers of Complex numbers in polar form is much faster and easier
                IntegerImpl exponent = new IntegerImpl(BigInteger.valueOf(n));
                RealType modulus = computeIntegerExponent(x.magnitude(), exponent);
                RealType argument = (RealType) x.argument().multiply(exponent);
                return new ComplexPolarImpl(modulus, argument, x.isExact());
            }

            Numeric intermediate = x;
            Numeric factor = intermediate;
            for (long idx = 2L; idx <= Math.abs(n); idx++) {
                intermediate = intermediate.multiply(factor);
            }
            if (n < 0L) intermediate = intermediate.inverse();
            return (ComplexType) intermediate.coerceTo(ComplexType.class);
        } catch (CoercionException ex) {
            Logger.getLogger(MathUtils.class.getName()).log(Level.SEVERE, "Unrecoverable exception thrown while computing integer exponent.", ex);
            throw new ArithmeticException("Failure to coerce to ComplexType.");
        }
    }
    
    /**
     * Compute x<sup>n</sup>. The {@link MathContext} is inferred from {@code x}.
     * @param x the value to take the exponent of
     * @param n the integer exponent
     * @return x raised to the n<sup>th</sup> power
     */
    public static RealType computeIntegerExponent(RealType x, int n) {
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
            throw new ArithmeticException("ln is undefined for values < 0");
        }
        if (newtonRange.contains(x)) return lnNewton(x, mctx);
        
        if (x.asBigDecimal().compareTo(BigDecimal.TEN) > 0) {
            RealType mantissa = mantissa(x);
            IntegerType exponent = exponent(x);
            // use the identity ln(a*10^n) = ln(a) + n*ln(10)
            RealType ln10 = lnSeries(new RealImpl(BigDecimal.TEN), mctx);
            try {
                return (RealType) ln(mantissa, mctx).add((RealType) ln10.multiply(exponent).coerceTo(RealType.class));
            } catch (CoercionException ex) {
                Logger.getLogger(MathUtils.class.getName()).log(Level.SEVERE, "Failed to coerce ln(10)*n to RealType.", ex);
                throw new IllegalStateException("Multiplication of real and integer should give us a real back.");
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
        BigDecimal ninv = BigDecimal.ONE.divide(BigDecimal.valueOf((long) n), mctx);
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
     * notation, mantissa * 10<sup>exponent</sup>.
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
     * notation, mantissa * 10<sup>exponent</sup>.
     * @param x the real value
     * @return the exponent of {@code x}
     */
    public static IntegerType exponent(RealType x) {
        int exponent = x.asBigDecimal().precision() - x.asBigDecimal().scale() - 1;
        return new IntegerImpl(BigInteger.valueOf((long) exponent));  // the exponent should always be exact
    }
    
    /**
     * Compute the general case of x<sup>y</sup>, where x and y are both real numbers.
     * @param base the value to raise to a given power
     * @param exponent the power to which we want to raise {@code base}
     * @param mctx the {@link MathContext} to use for this calculation
     * @return the value of base<sup>exponent</sup>
     */
    public static RealType generalizedExponent(RealType base, Numeric exponent, MathContext mctx) {
        if (exponent instanceof Zero) {
            try {
                return (RealType) One.getInstance(mctx).coerceTo(RealType.class);
            } catch (CoercionException ex) {
                Logger.getLogger(MathUtils.class.getName()).log(Level.SEVERE,
                        "Could not obtain a Real instance of one.", ex);
                throw new IllegalStateException(ex);
            }
        }
        NumericHierarchy htype = NumericHierarchy.forNumericType(exponent.getClass());
        switch (htype) {
            case INTEGER:
                int n = ((IntegerType) exponent).asBigInteger().intValueExact();
                return computeIntegerExponent(base, n, mctx);
            case REAL:
                if (exponent.isCoercibleTo(IntegerType.class)) {
                    try {
                        IntegerType integer = (IntegerType) exponent.coerceTo(IntegerType.class);
                        return generalizedExponent(base, integer, mctx);
                    } catch (CoercionException ex) {
                        Logger.getLogger(MathUtils.class.getName()).log(Level.SEVERE, "Failed to coerce real to integer.", ex);
                        throw new IllegalStateException("Should have been able to coerce RealType to IntegerType.", ex);
                    }
                }
                // approximate with a rational
                try {
                    RationalType ratexponent = (RationalType) exponent.coerceTo(RationalType.class);
                    return generalizedExponent(base, ratexponent, mctx);
                } catch (CoercionException ex) {
                    Logger.getLogger(MathUtils.class.getName()).log(Level.SEVERE, "Failed to coerce real to rational.", ex);
                    throw new IllegalStateException("Should have been able to coerce RealType to RationalType.", ex);
                }
            case RATIONAL:
                // use the identity b^(u/v) = vth root of b^u
                RationalType ratexponent = (RationalType) exponent;
                final int n_num = ratexponent.numerator().asBigInteger().intValueExact();
                RealType intermediate = computeIntegerExponent(base, n_num, mctx);
                return nthRoot(intermediate, ratexponent.denominator(), mctx);
            default:
                throw new ArithmeticException("Currently generalizedExponent() has no support for exponents of type " + exponent.getClass().getTypeName());
        }
    }

    public static ComplexType generalizedExponent(ComplexType base, Numeric exponent, MathContext mctx) {
        if (exponent instanceof Zero) {
            try {
                return (ComplexType) One.getInstance(mctx).coerceTo(ComplexType.class);
            } catch (CoercionException ex) {
                Logger.getLogger(MathUtils.class.getName()).log(Level.SEVERE,
                        "Could not obtain a Complex instance of one.", ex);
                throw new IllegalStateException(ex);
            }
        }
        NumericHierarchy htype = NumericHierarchy.forNumericType(exponent.getClass());
        switch (htype) {
            case INTEGER:
                int n = ((IntegerType) exponent).asBigInteger().intValueExact();
                return computeIntegerExponent(base, n, mctx);
            case REAL:
                if (exponent.isCoercibleTo(IntegerType.class)) {
                    try {
                        IntegerType integer = (IntegerType) exponent.coerceTo(IntegerType.class);
                        return generalizedExponent(base, integer, mctx);
                    } catch (CoercionException ex) {
                        Logger.getLogger(MathUtils.class.getName()).log(Level.SEVERE, "Failed to coerce real to integer.", ex);
                        throw new IllegalStateException("Should have been able to coerce RealType to IntegerType.", ex);
                    }
                }
                // approximate with a rational
                try {
                    RationalType ratexponent = (RationalType) exponent.coerceTo(RationalType.class);
                    return generalizedExponent(base, ratexponent, mctx);
                } catch (CoercionException ex) {
                    Logger.getLogger(MathUtils.class.getName()).log(Level.SEVERE, "Failed to coerce real to rational.", ex);
                    throw new IllegalStateException("Should have been able to coerce RealType to RationalType.", ex);
                }
            case RATIONAL:
                // use the identity b^(u/v) = vth root of b^u
                RationalType ratexponent = (RationalType) exponent;
                final long n_num = ratexponent.numerator().asBigInteger().longValueExact();
                ComplexType intermediate = computeIntegerExponent(base, n_num, mctx);
                RealType modulus = nthRoot(intermediate.magnitude(), ratexponent.denominator());
                RealType argument = (RealType) intermediate.argument().divide(ratexponent.denominator());
                return new ComplexPolarImpl(modulus, argument, false);
            default:
                throw new ArithmeticException("Currently generalizedExponent() has no support for exponents of type " + exponent.getClass().getTypeName());
        }
    }


    /**
     * Compute the nth root of a real value a.  The result is the principal
     * root of the equation x<sup>n</sup> = a.  Note that the {@link MathContext}
     * is inferred from the argument {@code a}.
     * @param a the value for which we want to find a root
     * @param n the degree of the root
     * @return the {@code n}th root of {@code a}
     */
    public static RealType nthRoot(RealType a, IntegerType n) {
        return nthRoot(a, n, a.getMathContext());
    }
    
    /**
     * Compute the nth root of a real value a.  The result is the principal
     * root of the equation x<sup>n</sup> = a.  The {@link MathContext}
     * is explicitly supplied.
     * @param a the value for which we want to find a root
     * @param n the degree of the root
     * @param mctx the {@link MathContext} to use for this calculation
     * @return the {@code n}th root of {@code a}
     */
    public static RealType nthRoot(RealType a, IntegerType n, MathContext mctx) {
        BigDecimal A = a.asBigDecimal();
        if (A.compareTo(BigDecimal.ZERO) == 0) {
            try {
                return (RealType) ExactZero.getInstance(mctx).coerceTo(RealType.class);
            } catch (CoercionException ex) {
                // we should never get here
                throw new IllegalStateException(ex);
            }
        }
        
        int nint = n.asBigInteger().intValueExact();
        BigDecimal ncalc = new BigDecimal(n.asBigInteger());
        BigDecimal nminus1 = ncalc.subtract(BigDecimal.ONE);
        BigDecimal x0;
        BigDecimal x1 = A.divide(new BigDecimal(n.asBigInteger()), mctx); // initial estimate
        
        while (true) {
            x0 = x1;
            x1 = nminus1.multiply(x0, mctx).add(A.divide(x0.pow(nint - 1, mctx), mctx), mctx).divide(ncalc, mctx);
            if (x0.compareTo(x1) == 0) break;
        }
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
     * Compute the n<sup>th</sup> roots of unity.
     * @param n the degree of the roots
     * @param mctx the {@link MathContext} for computing these values
     * @return a {@link Set} of {@code n} complex roots
     */
    public static Set<ComplexType> rootsOfUnity(long n, MathContext mctx) {
        RealImpl decTwo = new RealImpl(new BigDecimal(TWO));
        decTwo.setMathContext(mctx);
        RealImpl decOne = new RealImpl(BigDecimal.ONE);
        decOne.setMathContext(mctx);
        RealType twopi = (RealType) Pi.getInstance(mctx).multiply(decTwo);
        NumericSet set = new NumericSet();
        for (long idx = 1; idx <= n; idx++) {
            RealType realN = new RealImpl(BigDecimal.valueOf(idx), mctx);
            ComplexPolarImpl val = new ComplexPolarImpl(decOne, (RealType) twopi.divide(realN));
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
     * Method intended to determine the lowest precision of a {@link List} of {@link Numeric} arguments.
     * @param args a {@link List} of {@link Numeric} arguments
     * @return a {@link MathContext} constructed from the given arguments, or {@link MathContext#UNLIMITED} if none can be inferred from arguments
     */
    public static MathContext inferMathContext(List<? extends Numeric> args) {
        int precision = args.stream().mapToInt(x -> x.getMathContext().getPrecision()).filter(x -> x > 0).min().orElse(-1);
        if (precision > 0) {
            return new MathContext(precision, args.get(0).getMathContext().getRoundingMode());
        }
        return MathContext.UNLIMITED;
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
    
    /**
     * Generate a matrix of rotation in 2 dimensions.
     * 
     * @param theta the angle of rotation in radians around the origin
     * @return a 2&#215;2 matrix of rotation
     */
    public static Matrix<RealType> get2DMatrixOfRotation(RealType theta) {
        RealType[][] temp = new RealType[2][2];

        throw new UnsupportedOperationException("To be implemented");
        
//        RealType cos = new RealImpl(BigDecimalMath.cos(theta.asBigDecimal(), theta.getMathContext()));
//        RealType sin = new RealImpl(BigDecimalMath.sin(theta.asBigDecimal(), theta.getMathContext()));
//
//        temp[0][0] = cos;
//        temp[0][1] = sin.negate();
//        temp[1][0] = sin;
//        temp[1][1] = cos;
        
//        return new BasicMatrix<>(temp);
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
        RealType[][] temp = new RealType[3][];

        throw new UnsupportedOperationException("To be implemented");
        
//        RealType one;
//        RealType zero;
//        try {
//            zero = (RealType) ExactZero.getInstance(theta.getMathContext()).coerceTo(RealType.class);
//            one = (RealType) One.getInstance(theta.getMathContext()).coerceTo(RealType.class);
//        } catch (CoercionException coercionException) {
//            Logger.getLogger(MathUtils.class.getName()).log(Level.SEVERE, "Error obtaining real value for base constant.", coercionException);
//            zero = new RealImpl(BigDecimal.ZERO, theta.getMathContext());  // basic default behavior, because we shouldn't fail horribly like this
//            one = new RealImpl(BigDecimal.ONE, theta.getMathContext());
//        }
//
//        RealType cos = new RealImpl(BigDecimalMath.cos(theta.asBigDecimal(), theta.getMathContext()));
//        RealType sin = new RealImpl(BigDecimalMath.sin(theta.asBigDecimal(), theta.getMathContext()));
//
//        switch (axis) {
//            case X_AXIS:
//                temp[0] = new RealType[] { one, zero, zero };
//                temp[1] = new RealType[] { zero, cos, sin.negate() };
//                temp[2] = new RealType[] { zero, sin, cos };
//                break;
//            case Y_AXIS:
//                temp[0] = new RealType[] { cos, zero, sin };
//                temp[1] = new RealType[] { zero, one, zero };
//                temp[2] = new RealType[] { sin.negate(), zero, cos };
//                break;
//            case Z_AXIS:
//                temp[0] = new RealType[] { cos, sin.negate(), zero };
//                temp[1] = new RealType[] { sin, cos, zero };
//                temp[2] = new RealType[] { zero, zero, one };
//                break;
//        }
//        return new BasicMatrix<>(temp);
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
            throw new IllegalArgumentException("Argument epsilon must be a small positive value.");
        }
        
        final RealType difference = (RealType) A.subtract(B).magnitude();
        return difference.compareTo(epsilon) < 0;
    }
    
    /**
     * Tests if two real vectors are equal according to
     * {@link #areEqualToWithin(RealType, RealType, RealType) }. The two
     * vectors are compared element-wise, and if any pair of elements has
     * a difference &ge; &sigma;, the comparison fails fast and returns false.
     * 
     * @param A the first real-valued vector to test for equality
     * @param B the second real-valued vector to test for equality
     * @param epsilon a value between 0 and 1, exclusive, denoting the maximum
     *  difference allowed between any pair of elements for A and B to be
     *  considered equal.
     * @return true if the supplied vectors are of equal length and all of the
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
                if (!MathUtils.areEqualToWithin((Vector<RealType>) A.getColumn(column),
                        (Vector<RealType>) B.getColumn(column), epsilon)) return false;
            }
            return true;
        } else {
            // default behavior is to compare by rows
            for (long row = 0L; row < A.rows(); row++) {
                if (!MathUtils.areEqualToWithin((Vector<RealType>) A.getRow(row),
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

    public static ComplexType ln(ComplexType z) {
        return new ComplexRectImpl(ln(z.magnitude()), z.argument());
    }

    public static Numeric arctan(Numeric z) {
        final ComplexType i = (ComplexType) ImaginaryUnit.getInstance(z.getMathContext());
        final ComplexType coeff = (ComplexType) i.negate().divide(new RealImpl(decTWO, z.getMathContext()));
        ComplexType frac = (ComplexType) i.subtract(z).divide(i.add(z));
        return coeff.multiply(ln(frac));
    }

    public static Numeric atan2(RealType y, RealType x) {
        final RealType zero = new RealImpl(BigDecimal.ZERO, y.getMathContext());
        final RealType two  = new RealImpl(decTWO, y.getMathContext());
        final Numeric term = x.multiply(x).add(y.multiply(y)).sqrt();
        if (x.compareTo(zero) > 0) {
            return two.multiply(arctan(y.divide(term.add(x))));
        }
        if (x.compareTo(zero) <= 0 && !Zero.isZero(y)) {
            return two.multiply(arctan(term.subtract(x).divide(y)));
        }
        if (x.compareTo(zero) < 0 && Zero.isZero(y)) {
            return Pi.getInstance(y.getMathContext());
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
        final ComplexType i = (ComplexType) ImaginaryUnit.getInstance(z.getMathContext());
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
            throw new ArithmeticException("Type coercion error while computing ln(" + z + ").");
        }
    }
    
    public static Comparator<Numeric> obtainGenericComparator() {
        return new Comparator<Numeric>() {
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
                        Logger.getAnonymousLogger().log(Level.SEVERE, "No common type found for {} and {}.",
                                new Object[] {h1, h2});
                        throw new IllegalArgumentException("Failure to coerce arguments to a common type.", ce);
                    }
                } else {
                    throw new IllegalArgumentException("Numeric subtype must be comparable.");
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
}
