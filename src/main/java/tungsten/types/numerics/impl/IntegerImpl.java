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
import tungsten.types.exceptions.CoercionException;
import tungsten.types.numerics.IntegerType;
import tungsten.types.numerics.NumericHierarchy;
import tungsten.types.numerics.RationalType;
import tungsten.types.numerics.Sign;
import tungsten.types.util.ClassTools;
import tungsten.types.util.UnicodeTextEffects;

import java.math.BigInteger;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Objects;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * An implementation of an integer data type.
 *
 * @author Robert Poole, <a href="mailto:Tarquin.AZ@gmail.com">Gmail</a> or
 *   <a href="mailto:tarquin@alum.mit.edu">MIT alumni e-mail</a>
 */
public class IntegerImpl implements IntegerType {
    private boolean exact = true;
    private final BigInteger val;
    private static final BigInteger NINE = BigInteger.valueOf(9L);

    /**
     * Constructor that initializes {@code this} with a {@link BigInteger}.
     * @param initialVal the value with which to initialize {@code this}
     */
    public IntegerImpl(BigInteger initialVal) {
        if (initialVal == null) {
            throw new IllegalArgumentException("Must supply a non-null value");
        }
        val = initialVal;
    }

    /**
     * Constructor that takes a {@code String} representation and parses it.
     * The string is sanitized before parsing.
     * @param representation the textual representation of an integer value
     */
    public IntegerImpl(String representation) {
        val = new BigInteger(UnicodeTextEffects.sanitizeDecimal(representation));
    }

    /**
     * Constructor that initializes {@code this} with a {@link BigInteger} and
     * a given exactness.
     * @param initialVal the value with which to initialize {@code this}
     * @param exact      whether this value should be considered exact or not
     */
    public IntegerImpl(BigInteger initialVal, boolean exact) {
        this(initialVal);
        this.exact = exact;
    }

    /**
     * Constructor that takes a {@code String} representation and parses it
     * as well as a given exactness.
     * The string is sanitized before parsing.
     * @param representation the textual representation of an integer value
     * @param exact          whether this value should be considered exact or not
     */
    public IntegerImpl(String representation, boolean exact) {
        this(representation);
        this.exact = exact;
    }

    @Override
    public IntegerType magnitude() {
        return new IntegerImpl(val.abs(), exact);
    }

    @Override
    public IntegerType negate() {
        return new IntegerImpl(val.negate(), exact);
    }

    @Override
    public IntegerType modulus(IntegerType divisor) {
        return new IntegerImpl(val.mod(divisor.asBigInteger()));
    }

    @Override
    public boolean isEven() {
        return val.mod(BigInteger.TWO).equals(BigInteger.ZERO);
    }

    @Override
    public boolean isOdd() {
        return !isEven();
    }

    /**
     * Determine if this integer is a perfect square. Negative numbers will
     * always return a false value.
     *
     * @return true if this is a perfect square, false otherwise
     * @see <a href="http://burningmath.blogspot.com/2013/09/how-to-check-if-number-is-perfect-square.html">the
     *   general algorithm</a>
     */
    @Override
    public boolean isPerfectSquare() {
        if (val.equals(BigInteger.ZERO) || val.equals(BigInteger.ONE)) {
            return true;
        }
        if (val.compareTo(BigInteger.ZERO) < 0) {
            return false;
        }
        final int lastDigit = digitAt(0L);
        if (lastDigit == 2 || lastDigit == 3 || lastDigit == 7 || lastDigit == 8) {
            return false;
        }
        // compute the digital root
        IntegerType temp = this;
        do {
            temp = sumDigits(temp);
        } while (temp.asBigInteger().compareTo(NINE) > 0);
        int digroot = temp.asBigInteger().intValueExact();
        if (digroot == 0 || digroot == 1 || digroot == 4 || digroot == 7) {
            // this is a candidate for a perfect square, but the only way
            // to be sure is to take the square root and then square the result,
            // comparing with this value to see if it matches
            // Note that this is taken care of inside IntegerImpl.sqrt()
            IntegerType root = this.sqrt();
            return root.isExact();
        }
        return false;
    }

    protected IntegerType sumDigits(IntegerType temp) {
        BigInteger sum = BigInteger.ZERO;

        for (long idx = 0L; idx < temp.numberOfDigits(); idx++) {
            sum = sum.add(BigInteger.valueOf(temp.digitAt(idx)));
        }
        return new IntegerImpl(sum);
    }

    private transient long numDigitsCache = -1L;
    private final Lock numDigitsLock = new ReentrantLock();

    /**
     * This is based on an algorithm posted by OldCurmudgeon on StackOverflow.
     * It has been modified to avoid code repetition and for compatibility with
     * small values (e.g., 2- and 3-digit numbers).
     *
     * @return the number of digits in this integral value
     * @see <a href="https://stackoverflow.com/questions/18828377/biginteger-count-the-number-of-decimal-digits-in-a-scalable-method">the
     *   original StackOverflow article</a>
     */
    @Override
    public long numberOfDigits() {
        // corner case for zero
        if (val.equals(BigInteger.ZERO)) {
            return 1L;
        }

        numDigitsLock.lock();
        try {
            if (numDigitsCache > 0L) {
                return numDigitsCache;
            }

            long digits = 0L;
            BigInteger temp = val.abs();
            int bits;

            // Serious reductions.
            do {
                // calculate bitLength
                bits = temp.bitLength();
                // 4 > log₂(10) so we should not reduce it too far
                int reduce = bits / 4;
                // Divide by 10^reduce
                temp = temp.divide(BigInteger.TEN.pow(reduce));
                // Removed that many decimal digits.
                digits += reduce;
            } while (bits > 4); // Now 4 bits or less - add 1 if necessary.
            // using intValue() instead of intValueExact() so we don't throw
            // any unwanted exceptions
            if (temp.intValue() > 0) {  // original algorithm compared with 9
                digits++;
            }
            numDigitsCache = digits;
            return digits;
        } finally {
            numDigitsLock.unlock();
        }
    }

    @Override
    public int digitAt(long position) throws IndexOutOfBoundsException {
        if (position < 0L) {
            throw new IndexOutOfBoundsException("Negative index is not supported");
        }
        if (position == 0L) {
            // optimization to avoid complex logic below for a common case
            return val.mod(BigInteger.TEN).intValue();
        }

        BigInteger temp = val.abs();
        long count = 0L;
        do {
            BigInteger[] resultAndRemainder = temp.divideAndRemainder(BigInteger.TEN);
            if (count == position) {
                return resultAndRemainder[1].intValue();
            }
            temp = resultAndRemainder[0];
            count++;
        } while (!temp.equals(BigInteger.ZERO));
        // if we fell through here, it means position is not valid
        throw new IndexOutOfBoundsException("Index " + position + " exceeds max value " + (count - 1L));
    }
    
    @Override
    public Numeric pow(IntegerType exponent) {
        final BigInteger MAX_INT = BigInteger.valueOf(Integer.MAX_VALUE);
        Sign expSign = exponent.sign();
        if (exponent.asBigInteger().abs().compareTo(MAX_INT) > 0) {
            IntegerType halfExponent = new IntegerImpl(exponent.asBigInteger().abs().shiftRight(1));
            IntegerType intermediate = (IntegerType) this.pow(halfExponent);
            intermediate = (IntegerType) intermediate.multiply(intermediate); // square the intermediate result
            if (exponent.isOdd()) {
                // and handle the corner case where we had an odd exponent
                intermediate = (IntegerType) intermediate.multiply(this);
            }
            return expSign == Sign.NEGATIVE ? intermediate.inverse() : intermediate;
        }
        if (expSign == Sign.NEGATIVE) {
            IntegerType negexp = exponent.negate();
            return new RationalImpl(BigInteger.ONE,
                    val.pow(negexp.asBigInteger().intValueExact()), this.isExact());
        }
        return new IntegerImpl(val.pow(exponent.asBigInteger().intValueExact()), this.isExact());
    }

    /**
     * Compute {@code this}<sup>n</sup>mod&nbsp;m.
     * @param n an integer exponent represented as a {@code long} for convenience
     * @param m an integer value for taking the modulus
     * @return the result of exponentiation followed by taking the modulus
     */
    @Override
    public IntegerType powMod(long n, IntegerType m) {
        return new IntegerImpl(val.modPow(BigInteger.valueOf(n), m.asBigInteger()), exact && m.isExact());
    }

    /**
     * Compute {@code this}<sup>n</sup>&nbsp;mod&nbsp;m.
     * @param n an integer exponent
     * @param m an integer value for taking the modulus
     * @return the result of exponentiation followed by taking the modulus
     */
    @Override
    public IntegerType powMod(IntegerType n, IntegerType m) {
        return new IntegerImpl(val.modPow(n.asBigInteger(), m.asBigInteger()), exact && m.isExact());
    }

    @Override
    public BigInteger asBigInteger() {
        return val;
    }

    @Override
    public boolean isExact() {
        return exact;
    }

    @Override
    public boolean isCoercibleTo(Class<? extends Numeric> numtype) {
        if (numtype == Numeric.class) return true;
        NumericHierarchy hval = NumericHierarchy.forNumericType(numtype);
        return hval != null;  // integer can be upconverted to any known type
    }

    @Override
    public Numeric coerceTo(Class<? extends Numeric> numtype) throws CoercionException {
        if (numtype == Numeric.class) {
            if (exact) {
                if (val.equals(BigInteger.ZERO)) return ExactZero.getInstance(getMathContext());
                if (val.equals(BigInteger.ONE)) return One.getInstance(getMathContext());
            }
            return this;
        }
        NumericHierarchy hval = NumericHierarchy.forNumericType(numtype);
        if (hval == null) {
            Logger.getLogger(IntegerImpl.class.getName()).log(Level.SEVERE,
                    "NumericHierarchy for target type of {0} when converting {1} is null.",
                    new Object[] { numtype.getTypeName(), val });
            throw new CoercionException("Specified type cannot be inferred", this.getClass(), numtype);
        }
        switch (hval) {
            case INTEGER:
                return this;
            case RATIONAL:
                return new RationalImpl(this);
            case REAL:
                return new RealImpl(this);
            case COMPLEX:
                return new ComplexRectImpl(new RealImpl(this));
            default:
                throw new CoercionException("Cannot coerce integer to specified type",
                        this.getClass(), numtype);
        }
    }

    @Override
    public Numeric add(Numeric addend) {
        if (addend instanceof IntegerType) {
            IntegerType that = (IntegerType) addend;
            return new IntegerImpl(val.add(that.asBigInteger()), exact && that.isExact());
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
                Logger.getLogger(IntegerImpl.class.getName()).log(Level.SEVERE,
                        "Failed to coerce type during integer add.", ex);
            }
        }
        throw new UnsupportedOperationException("Addition operation unsupported");
    }

    @Override
    public Numeric subtract(Numeric subtrahend) {
        if (subtrahend instanceof IntegerType) {
            IntegerType that = (IntegerType) subtrahend;
            return new IntegerImpl(val.subtract(that.asBigInteger()), exact && that.isExact());
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
                Logger.getLogger(IntegerImpl.class.getName()).log(Level.SEVERE,
                        "Failed to coerce type during integer subtract.", ex);
            }
        }
        throw new UnsupportedOperationException("Subtraction operation unsupported");
    }

    @Override
    public Numeric multiply(Numeric multiplier) {
        final boolean exactness = this.isExact() && multiplier.isExact();
        if (multiplier instanceof IntegerType) {
            final IntegerType that = (IntegerType) multiplier;
            return new IntegerImpl(val.multiply(that.asBigInteger()), exactness);
        } else if (multiplier instanceof RationalType) {
            final RationalType that = (RationalType) multiplier;
            BigInteger numResult = val.multiply(that.numerator().asBigInteger());
            BigInteger denomResult = that.denominator().asBigInteger();
            final BigInteger gcd = numResult.gcd(denomResult);
            if (gcd.equals(denomResult)) {
                // reducing would give a denominator of 1, so result is an integer
                return new IntegerImpl(numResult.divide(gcd), exactness);
            } else {
                return new RationalImpl(numResult.divide(gcd), denomResult.divide(gcd), exactness);
            }
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
                Logger.getLogger(IntegerImpl.class.getName()).log(Level.SEVERE,
                        "Failed to coerce type during integer multiply.", ex);
            }
        }
        throw new UnsupportedOperationException("Multiplication operation unsupported");
    }

    @Override
    public Numeric divide(Numeric divisor) {
        if (divisor instanceof IntegerType) {
            final IntegerType that = (IntegerType) divisor;
            final boolean exactness = this.isExact() && divisor.isExact();
            BigInteger[] resultAndRemainder = val.divideAndRemainder(that.asBigInteger());
            // if the remainder is 0, we can return an integer
            if (resultAndRemainder[1].equals(BigInteger.ZERO)) {
                return new IntegerImpl(resultAndRemainder[0], exactness);
            } else {
                return new RationalImpl(val, that.asBigInteger(), exactness).reduce();
            }
        } else if (divisor instanceof RationalType) {
            return this.multiply(divisor.inverse());
        } else {
            Class<?> iface = ClassTools.getInterfaceTypeFor(divisor.getClass());
            if (iface == Numeric.class) {
                // to avoid infinite recursion
                return divisor.inverse().multiply(this);
            }
            // if we got here, divisor is probably something like Real or ComplexType
            try {
                return this.coerceTo((Class<? extends Numeric>) iface).divide(divisor);
            } catch (CoercionException ex) {
                Logger.getLogger(IntegerImpl.class.getName()).log(Level.SEVERE,
                        "Failed to coerce type during integer divide.", ex);
            }
        }
        throw new UnsupportedOperationException("Division operation unsupported");
    }

    @Override
    public Numeric inverse() {
        if (val.abs().equals(BigInteger.ONE)) {
            // 1 and -1 are their own inverses
            return this;
        } else if (val.equals(BigInteger.ZERO)) {
            throw new ArithmeticException("Cannot compute inverse of 0");
        }
        RationalImpl rational = new RationalImpl(BigInteger.ONE, val, exact);
        // preserve MathContext
        rational.setMathContext(this.getMathContext());
        return rational;
    }

    /**
     * This computes an integer square root.  Thus, it will only give
     * exact results for perfect squares.  If you want a closer decimal
     * approximation, try using {@link #coerceTo(Class) } with
     * a type argument of {@code RealType.class}.
     * The original algorithm was presented by Edward Falk on StackOverflow, with
     * a couple necessary corrections by me. The current version delegates to
     * {@link BigInteger#sqrt()} (available since Java 9).
     * @return an integer approximation of a square root
     * @see <a href="https://stackoverflow.com/questions/4407839/how-can-i-find-the-square-root-of-a-java-biginteger">the StackOverflow article</a>
     */
    @Override
    public IntegerType sqrt() {
        if (val.signum() < 0) {
            throw new ArithmeticException("Cannot obtain square root of negative integers");
        }
        final BigInteger result = val.sqrt();
        final boolean exactness = exact && result.multiply(result).equals(val);
        final MathContext rootCtx = getMathContext();  // we explicitly inherit the MathContext, which may be custom
        return new IntegerImpl(result, exactness) {
            @Override
            public MathContext getMathContext() {
                return rootCtx;
            }
        };
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof Zero) {
            return exact == ((Zero) other).isExact() && val.equals(BigInteger.ZERO);
        } else if (other instanceof One) {
            return exact && val.equals(BigInteger.ONE);
        }
        if (other instanceof IntegerType) {
            final IntegerType that = (IntegerType) other;
            if (this.isExact() != that.isExact()) return false;
            return val.equals(that.asBigInteger());
        } else if (other instanceof Numeric) {
            final Numeric that = (Numeric) other;
            if (that.isCoercibleTo(IntegerType.class)) {
                if (this.isExact() != that.isExact()) return false;

                try {
                    final IntegerType asInt = (IntegerType) that.coerceTo(IntegerType.class);
                    return this.equals(asInt);
                } catch (CoercionException e) {
                    throw new IllegalStateException("Failed to coerce to IntegerType after test for coercibility", e);
                }
            }
        }
        return false;
    }

    @Override
    public int hashCode() {
        int hash = 5;
        hash = 59 * hash + (this.exact ? 1 : 0);
        hash = 59 * hash + Objects.hashCode(this.val);
        return hash;
    }

    @Override
    public int compareTo(IntegerType o) {
        return this.val.compareTo(o.asBigInteger());
    }

    @Override
    public Sign sign() {
        return Sign.fromValue(val);
    }

    @Override
    public MathContext getMathContext() {
        int digits = numberOfDigits() > (long) Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) numberOfDigits();
        return new MathContext(digits, RoundingMode.HALF_UP);
    }
    
    @Override
    public String toString() {
        return val.toString();
    }

    /*
     Groovy-specific methods below here.
     */

    @Override
    public IntegerType power(Numeric operand) {
        if (operand.isCoercibleTo(IntegerType.class)) {
            try {
                IntegerType converted = (IntegerType) operand.coerceTo(IntegerType.class);
                BigInteger exponent = converted.asBigInteger();
                return new IntegerImpl(val.pow(exponent.intValueExact()));
            } catch (CoercionException e) {
                throw new IllegalStateException("Failed to coerce " + operand, e);
            }
        }
        throw new ArithmeticException("Unsupported exponent type");
    }

    @Override
    public IntegerType or(IntegerType operand) {
        return new IntegerImpl(val.or(operand.asBigInteger()));
    }

    @Override
    public IntegerType and(IntegerType operand) {
        return new IntegerImpl(val.and(operand.asBigInteger()));
    }

    @Override
    public IntegerType xor(IntegerType operand) {
        return new IntegerImpl(val.xor(operand.asBigInteger()));
    }

    @Override
    public IntegerType leftShift(IntegerType operand) {
        return new IntegerImpl(val.shiftLeft(operand.asBigInteger().intValueExact()));
    }

    @Override
    public IntegerType rightShift(IntegerType operand) {
        return new IntegerImpl(val.shiftRight(operand.asBigInteger().intValueExact()));
    }

    @Override
    public IntegerType next() {
        return new IntegerImpl(val.add(BigInteger.ONE));
    }

    @Override
    public IntegerType previous() {
        return new IntegerImpl(val.subtract(BigInteger.ONE));
    }

    @Override
    public IntegerType bitwiseNegate() {
        return new IntegerImpl(val.not());
    }
}
