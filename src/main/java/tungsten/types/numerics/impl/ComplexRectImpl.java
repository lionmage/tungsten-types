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
import tungsten.types.util.MathUtils;
import tungsten.types.util.OptionalOperations;
import tungsten.types.util.UnicodeTextEffects;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static tungsten.types.util.MathUtils.atan2;

/**
 * An implementation of {@link ComplexType} that uses a rectangular
 * representation internally.
 *
 * @author Robert Poole, <a href="mailto:Tarquin.AZ@gmail.com">Tarquin.AZ@gmail.com</a>
 */
public class ComplexRectImpl implements ComplexType {
    /**
     * The System property which determines whether fast magnitude calculation is used.
     */
    public static final String FAST_MAGNITUDE = "tungsten.types.numerics.ComplexRectImpl.fastMagnitude.enable";
    private final RealType real;
    private final RealType imag;
    private boolean exact = true;
    private MathContext mctx = MathContext.UNLIMITED;
    
    private static final RealType ZERO = new RealImpl(BigDecimal.ZERO);
    private static final RealType TWO = new RealImpl(BigDecimal.valueOf(2L));

    /**
     * Construct a complex number in rectangular form given a real and an imaginary component.
     * @param real      the real component of this complex value
     * @param imaginary the imaginary component of this complex value
     */
    public ComplexRectImpl(RealType real, RealType imaginary) {
        this.real = real;
        this.imag = imaginary;
        this.mctx = MathUtils.inferMathContext(List.of(real, imaginary));
        this.exact = real.isExact() && imaginary.isExact();
    }

    /**
     * Construct a complex number in rectangular form given a real and an imaginary component
     * and an indication of exactness.
     * @param real      the real component of this complex value
     * @param imaginary the imaginary component of this complex value
     * @param exact     determines whether this value is considered exact or not
     */
    public ComplexRectImpl(RealType real, RealType imaginary, boolean exact) {
        this(real, imaginary);
        this.exact = exact;
    }
    
    /*
      This is OK since Pattern is considered thread-safe.  (Matcher, however,
      is not.)  Unlimited whitespace is allowed around the middle + or -
      character, but only 1 optional whitespace character is allowed before
      the terminal i.  Note that U+2212 is no longer included in the pattern
      since the sanitizer converts it to a '-' character.
    */
    private static final Pattern cplxPat = Pattern.compile("([+-]?\\d+\\.?\\d*)\\s*([+-])\\s*(\\d+\\.?\\d*)\\s?[i\u2148]");
    
    /**
     * Convenience constructor that will give us a complex value for any
     * reasonable representation of a complex value.  Because this uses
     * regular expressions under the hood, this constructor should be used
     * judiciously.  This pattern allows generous whitespace between most
     * expected tokens.  Some valid inputs should be:
     * <ul>
     * <li> -5+ 4i</li>
     * <li> 3 - 2 i</li>
     * <li>-4.2+8.43i</li>
     * </ul>
     * Some <em>non-valid</em> inputs would be:
     * <ul>
     * <li>- 7+2.5i</li>
     * <li>+  5  - 2i</li>
     * <li>-2+5    i</li>
     * <li>5 + -3i</li>
     * </ul>
     * @param strval the string representing the complex value we need to parse
     */
    public ComplexRectImpl(String strval) {
        Matcher m = cplxPat.matcher(UnicodeTextEffects.sanitizeDecimal(strval));
        if (m.matches()) {
            assert m.groupCount() == 3;
            // group 0 is the entire matched pattern, so skip that
            real = new RealImpl(m.group(1));
            boolean imNeg = m.group(2).equals("-");  // sanitizer catches U+2212 therefore no need to catch it here
            RealImpl temp = new RealImpl(m.group(3));
            imag = imNeg ? temp.negate() : temp;
            // the following does not really work at all
//            mctx = MathUtils.inferMathContext(List.of(real, imag));
        } else {
            throw new IllegalArgumentException("Illegal init string for complex number value: " + strval);
        }
    }

    /**
     * Convenience constructor that takes a {@code String} representation
     * of a complex value and a {@code MathContext}.
     * @param strval a string representing a complex value in rectangular coordinates
     * @param mctx   the {@code MathContext} to associate with this value
     */
    public ComplexRectImpl(String strval, MathContext mctx) {
        this(strval);
        this.mctx = mctx;
    }

    /**
     * A convenience constructor for converting a real into a complex.
     *
     * @param realValue a real-valued number to be converted
     */
    public ComplexRectImpl(RealType realValue) {
        mctx = realValue.getMathContext();
        real = realValue;
        imag = ZERO;
        exact = realValue.isExact();
    }

    /**
     * Set the {@code MathContext} to associate with this value.
     * @param context the {@code MathContext} to assign
     */
    public void setMathContext(MathContext context) {
        argLock.lock();
        try {
            this.mctx = context;
            if (real.getMathContext().getPrecision() < context.getPrecision())
                OptionalOperations.setMathContext(real, context);
            if (imag.getMathContext().getPrecision() < context.getPrecision())
                OptionalOperations.setMathContext(imag, context);
            argCache = null;  // this must be regenerated
        } finally {
            argLock.unlock();
        }
    }

    /**
     * Returns true if fast magnitude calculation for
     * complex values is enabled.  This method checks
     * the system property denoted by {@link #FAST_MAGNITUDE}.
     * @return true if fast magnitude is enabled, false otherwise
     */
    public static boolean isFastMagnitudeEnabled() {
        return Boolean.getBoolean(FAST_MAGNITUDE);
    }

    @Override
    public RealType magnitude() {
        if (!isFastMagnitudeEnabled()) {
            // do it the slower way
            RealType resq = (RealType) real.multiply(real);
            RealType imsq = (RealType) imag.multiply(imag);
            // this should return an object with correct exact and irrational properties
            try {
                RealType result = (RealType) resq.add(imsq).sqrt().coerceTo(RealType.class);
                // the MathContext of the magnitude should be the same as that of the overall object
                OptionalOperations.setMathContext(result, mctx);
                return result;
            } catch (CoercionException fatal) {
                throw new IllegalStateException("While computing sqrt() for magnitude", fatal);
            }
        }
        // we want the result to reflect the MathContext of this complex number,
        // not its components
        final BigDecimal re = real.asBigDecimal();
        final BigDecimal im = imag.asBigDecimal();
        BigDecimal resq = re.multiply(re, mctx);
        BigDecimal imsq = im.multiply(im, mctx);
        BigDecimal root = resq.add(imsq, mctx).sqrt(mctx);
        // this is a kludge -- anything not an integer is considered inexact
        return new RealImpl(root, mctx, this.isExact() && root.stripTrailingZeros().scale() <= 0);
    }

    @Override
    public ComplexType negate() {
        return new ComplexRectImpl(real.negate(), imag.negate(), exact);
    }

    @Override
    public ComplexType conjugate() {
        return new ComplexRectImpl(real, imag.negate(), exact);
    }

    @Override
    public RealType real() {
        return real;
    }

    @Override
    public RealType imaginary() {
        return imag;
    }

    // computing the argument is costly enough that we should cache it
    private transient RealType argCache;
    private final Lock argLock = new ReentrantLock();

    @Override
    public RealType argument() {
        if (real.sign() == Sign.ZERO) {
            final Pi pi = Pi.getInstance(mctx);
            switch (imag.sign()) {
                case NEGATIVE:
                    return (RealType) pi.divide(TWO).negate();
                case POSITIVE:
                    return (RealType) pi.divide(TWO);
                case ZERO:
                    // this is an indeterminate case, so we pick 0
                    return ComplexRectImpl.ZERO;
                default:
                    throw new IllegalStateException("Invalid sign value for imaginary component");
            }
        }
        if (imag.sign() == Sign.ZERO) {
            switch (real.sign()) {
                case POSITIVE:
                    return ComplexRectImpl.ZERO;
                case NEGATIVE:
                    return Pi.getInstance(mctx);
                default:
                    throw new IllegalStateException("We should never have reached this statement");
            }
        }
        // for the general case, we need to compute the arctangent
        argLock.lock();
        try {
            if (argCache == null) {
                // The return value should be a real, but coerce just in case
                argCache = (RealType) atan2(imaginary(), real()).coerceTo(RealType.class);
            }
            return argCache;
        } catch (CoercionException e) {
            throw new ArithmeticException("Cannot coerce atan2 result to a real value");
        } finally {
            argLock.unlock();
        }
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
                return imag.asBigDecimal().compareTo(BigDecimal.ZERO) == 0;
            default:
                return false;
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
                if (imag.asBigDecimal().compareTo(BigDecimal.ZERO) == 0) {
                    return real;
                } else {
                    throw new CoercionException("Imaginary part must be 0",
                            this.getClass(), numtype);
                }
            default:
                throw new CoercionException("Unsupported coercion", this.getClass(), numtype);
        }
    }

    @Override
    public Numeric add(Numeric addend) {
        if (Zero.isZero(addend)) return this;
        if (addend instanceof ComplexType) {
            if (addend instanceof PointAtInfinity && ComplexType.isExtendedEnabled()) {
                return PointAtInfinity.getInstance();
            }
            ComplexType that = (ComplexType) addend;
            return new ComplexRectImpl((RealType) this.real.add(that.real()),
                    (RealType) this.imag.add(that.imaginary()),
                    this.exact && that.isExact());
        } else if (addend.isCoercibleTo(RealType.class)) {
            try {
                RealType realval = (RealType) addend.coerceTo(RealType.class);
                return new ComplexRectImpl((RealType) this.real().add(realval), this.imaginary(), exact && realval.isExact());
            } catch (CoercionException ex) {
                // we should never get here
                Logger.getLogger(ComplexRectImpl.class.getName()).log(Level.SEVERE, "Failed to coerce addend to RealType.", ex);
            }
        }
        throw new UnsupportedOperationException("Unsupported addend type");
    }

    @Override
    public Numeric subtract(Numeric subtrahend) {
        if (Zero.isZero(subtrahend)) return this;
        if (subtrahend instanceof ComplexType) {
            if (subtrahend instanceof PointAtInfinity && ComplexType.isExtendedEnabled()) {
                // complex infinity has no sign
                return PointAtInfinity.getInstance();
            }
            ComplexType that = (ComplexType) subtrahend;
            return new ComplexRectImpl((RealType) this.real.subtract(that.real()),
                    (RealType) this.imag.subtract(that.imaginary()),
                    this.exact && that.isExact());
        } else if (subtrahend.isCoercibleTo(RealType.class)) {
            try {
                RealType realval = (RealType) subtrahend.coerceTo(RealType.class);
                return new ComplexRectImpl((RealType) this.real().subtract(realval), this.imaginary(), exact && realval.isExact());
            } catch (CoercionException ex) {
                // we should never get here
                Logger.getLogger(ComplexRectImpl.class.getName()).log(Level.SEVERE, "Failed to coerce subtrahend to RealType.", ex);
            }
        }
        throw new UnsupportedOperationException("Unsupported subtrahend type");
    }

    @Override
    public Numeric multiply(Numeric multiplier) {
        if (One.isUnity(multiplier)) return this;
        if (Zero.isZero(multiplier)) return ExactZero.getInstance(mctx);
        if (multiplier instanceof ComplexType) {
            if (multiplier instanceof PointAtInfinity && ComplexType.isExtendedEnabled()) {
                if (Zero.isZero(this)) throw new ArithmeticException("0 \u22C5 ∞ is undefined");
                return PointAtInfinity.getInstance();
            }
            ComplexType cmult = (ComplexType) multiplier;
            RealType realresult = (RealType) real.multiply(cmult.real()).subtract(imag.multiply(cmult.imaginary()));
            RealType imagresult = (RealType) real.multiply(cmult.imaginary()).add(imag.multiply(cmult.real()));
            return new ComplexRectImpl(realresult, imagresult, exact && cmult.isExact());
        } else if (multiplier.isCoercibleTo(RealType.class)) {
            try {
                RealType scalar = (RealType) multiplier.coerceTo(RealType.class);
                return new ComplexRectImpl((RealType) real.multiply(scalar), (RealType) imag.multiply(scalar), exact && scalar.isExact());
            } catch (CoercionException ex) {
                Logger.getLogger(ComplexRectImpl.class.getName()).log(Level.SEVERE, "Failed to coerce multiplier to RealType.", ex);
            }
        }
        throw new UnsupportedOperationException("Unsupported multiplier type");
    }

    @Override
    public Numeric divide(Numeric divisor) {
        if (Zero.isZero(divisor)) {
            if (ComplexType.isExtendedEnabled()) {
                if (Zero.isZero(real) && Zero.isZero(imag)) throw new ArithmeticException("0/0 is undefined");
                return PointAtInfinity.getInstance();
            }
            throw new ArithmeticException("Division by 0");
        }
        if (One.isUnity(divisor)) return this;
        if (divisor instanceof ComplexType) {
            if (divisor instanceof PointAtInfinity && ComplexType.isExtendedEnabled()) {
                return ExactZero.getInstance(mctx); // was: new ComplexRectImpl(ZERO, ZERO)
            }
            ComplexType cdiv = (ComplexType) divisor;
            ComplexType conj = cdiv.conjugate();
            ComplexType num = (ComplexType) this.multiply(conj);
            try {
                RealType denom = (RealType) cdiv.multiply(conj).coerceTo(RealType.class);
                return new ComplexRectImpl((RealType) num.real().divide(denom), (RealType) num.imaginary().divide(denom), exact && denom.isExact());
            } catch (CoercionException ex) {
                Logger.getLogger(ComplexRectImpl.class.getName()).log(Level.SEVERE, "Complex multiplied by conjugate should be of RealType.", ex);
            }
        } else if (divisor.isCoercibleTo(RealType.class)) {
            try {
                RealType scalar = (RealType) divisor.coerceTo(RealType.class);
                return new ComplexRectImpl((RealType) real.divide(scalar), (RealType) imag.divide(scalar), exact && scalar.isExact());
            } catch (CoercionException ex) {
                Logger.getLogger(ComplexRectImpl.class.getName()).log(Level.SEVERE, "Failed to coerce divisor to RealType.", ex);
            }
        }
        throw new UnsupportedOperationException("Unsupported divisor type");
    }

    @Override
    public ComplexType inverse() {
        RealType invscale = (RealType) real.multiply(real).add(imag.multiply(imag));
        if (Zero.isZero(invscale)) {
            if (ComplexType.isExtendedEnabled()) return PointAtInfinity.getInstance();
            throw new ArithmeticException("No inverse for " + this);
        }
        return new ComplexRectImpl((RealType) real.divide(invscale), (RealType) imag.negate().divide(invscale), exact);
    }

    @Override
    public Numeric sqrt() {
        try {
            // Must use coercion since sqrt() can return an integer for perfect squares
            RealType rootreal = (RealType) magnitude().add(real).divide(TWO).sqrt().coerceTo(RealType.class);
            RealType rootimag = (RealType) magnitude().subtract(real).divide(TWO).sqrt().coerceTo(RealType.class);
            if (imag.sign() == Sign.NEGATIVE) rootimag = rootimag.negate();
            final ComplexRectImpl root = new ComplexRectImpl(rootreal, rootimag, rootreal.isExact() && rootimag.isExact());
            root.setMathContext(mctx);
            return root;
        } catch (CoercionException ex) {
            Logger.getLogger(ComplexRectImpl.class.getName()).log(Level.SEVERE, "Failed to coerce sqrt() intermediate result to RealType.", ex);
            throw new IllegalStateException("Unexpected failure to coerce sqrt() intermediate result", ex);
        }
    }
    
    @Override
    public Set<ComplexType> nthRoots(IntegerType n) {
        if (n.asBigInteger().equals(BigInteger.TWO)) {
            ComplexType principalRoot = (ComplexType) sqrt();
            return Set.of(principalRoot, principalRoot.negate());
        }
        ComplexPolarImpl polar = new ComplexPolarImpl(magnitude(), argument(), exact);
        return polar.nthRoots(n);
    }

    /**
     * Compute the n<sup>th</sup> roots of this value.
     * @param n the degree of the roots
     * @return a {@code Set} of {@code n} complex roots
     * @see #nthRoots(IntegerType)
     */
    public Set<ComplexType> nthRoots(long n) {
        return nthRoots(new IntegerImpl(BigInteger.valueOf(n), true));
    }
    
    @Override
    public boolean equals(Object o) {
        if (o instanceof Zero) {
            return exact == ((Zero) o).isExact()
                    && Zero.isZero(this.real)
                    && Zero.isZero(this.imag);
        } else if (o instanceof One) {
            return exact && One.isUnity(this.real) &&
                    Zero.isZero(this.imag);
        }
        if (o instanceof ComplexType) {
            if (o instanceof PointAtInfinity) return false;
            final ComplexType that = (ComplexType) o;
            boolean requal = this.real.equals(that.real());
            boolean iequal = this.imag.equals(that.imaginary());
            boolean exactness = this.exact && that.isExact();
            return requal && iequal && exactness;
        }
        return Zero.isZero(this.imag) && this.real.equals(o);
    }

    @Override
    public int hashCode() {
        int hash = 5;
        hash = 29 * hash + Objects.hashCode(this.real);
        hash = 29 * hash + Objects.hashCode(this.imag);
        hash = 29 * hash + (this.exact ? 1 : 0);
        return hash;
    }
    
    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder();
        buf.append(real);
        switch (imag.sign()) {
            case ZERO:
            case POSITIVE:
                buf.append(" + ").append(imag);
                break;
            case NEGATIVE:
                buf.append(" \u2212 ").append(imag.negate());
                break;
        }
        buf.append('\u2148');
        return buf.toString();
    }

    @Override
    public MathContext getMathContext() {
        return mctx;
    }
}
