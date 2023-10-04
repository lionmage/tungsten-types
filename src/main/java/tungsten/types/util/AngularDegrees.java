package tungsten.types.util;
/*
 * The MIT License
 *
 * Copyright © 2023 Robert Poole <Tarquin.AZ@gmail.com>.
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

import tungsten.types.Numeric;
import tungsten.types.Range;
import tungsten.types.annotations.Experimental;
import tungsten.types.exceptions.CoercionException;
import tungsten.types.numerics.IntegerType;
import tungsten.types.numerics.RealType;
import tungsten.types.numerics.Sign;
import tungsten.types.numerics.impl.IntegerImpl;
import tungsten.types.numerics.impl.One;
import tungsten.types.numerics.impl.Pi;
import tungsten.types.numerics.impl.RealImpl;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A representation of angles measured in degrees. This class internally
 * supports both decimal and sexagesimal representations of angles, and
 * can freely convert between them.  This class also supports conversion
 * to radians, obviating the need to perform this conversion elsewhere.
 * @author Robert Poole
 */
public class AngularDegrees {
    public static final char DEGREE_SIGN = '\u00B0';
    public static final char MINUTES_SIGN = '\u2032';  // prime, not apostrophe
    public static final char SECONDS_SIGN = '\u2033';  // double-prime, not two apostrophes or double quote
    /*
    Since 1 second of arc = 1/3600 degrees ≅ 0.00027... degrees, we need at least 5 decimal places to
    accurately represent this resolution.
     */
    private static final MathContext DEFAULT_CONTEXT = new MathContext(5);
    private static final IntegerType SIXTY = new IntegerImpl(BigInteger.valueOf(60L));
    /**
     * Pattern for matching angles specified in decimal degrees.
     * Note that the decimal point and subsequent digits are optional. A single optional whitespace
     * character is allowed before the terminating degree sign.
     */
    private static final Pattern decimalDegreesPattern = Pattern.compile("([+-]?\\d+\\.?\\d*)\\s?\\u00B0");
    /**
     * Pattern for matching angles specified in degrees, minutes, seconds notation.
     * Note that both the prime &prime; and apostrophe/single quote are permitted for denoting arcminutes.
     * Also note that both the double prime &Prime; and double quote are permitted for denoting arcseconds.
     * There is no substitute for the degree symbool &deg; since it is unambiguous and available on most
     * keyboards through various input schemes.
     */
    private static final Pattern DMSpattern = Pattern.compile("([+-]?\\d+)\\u00B0\\s?(\\d+)['′]\\s?(\\d+\\.?\\d*)[\"″]");

    private IntegerType degrees;
    private IntegerType minutes;
    private RealType seconds;
    private RealType decDegrees;
    private final MathContext mctx;

    public AngularDegrees(IntegerType degrees, IntegerType minutes, RealType seconds) {
        this.degrees = degrees;
        if (minutes.sign() == Sign.NEGATIVE || seconds.sign() == Sign.NEGATIVE) {
            throw new IllegalArgumentException("DMS notation requires minutes and seconds to be positive");
        }
        if (minutes.asBigInteger().intValueExact() > 59) {
            throw new IllegalArgumentException("minutes of arc must be between 0 and 59 inclusive");
        }
        if (seconds.asBigDecimal().compareTo(BigDecimal.valueOf(60L)) >= 0) {
            throw new IllegalArgumentException("seconds of arc must be \u2265 0 and < 60");
        }
        this.minutes = minutes;
        this.seconds = seconds;
        mctx = seconds.getMathContext().getPrecision() > DEFAULT_CONTEXT.getPrecision() ?
                seconds.getMathContext() : DEFAULT_CONTEXT;
        updateDecDegrees();
    }

    private static final RealType DEGREES_IN_CIRCLE = new RealImpl(BigDecimal.valueOf(360L));
    public static final Range<RealType> DECIMAL_DEGREE_RANGE = new Range<>(new RealImpl(BigDecimal.ZERO), Range.BoundType.INCLUSIVE,
            DEGREES_IN_CIRCLE, Range.BoundType.EXCLUSIVE);

    public AngularDegrees(RealType decimalDegrees) {
        mctx = decimalDegrees.getMathContext().getPrecision() > DEFAULT_CONTEXT.getPrecision() ?
                decimalDegrees.getMathContext() : DEFAULT_CONTEXT;
        this.decDegrees = fitToRange(decimalDegrees);
        updateDMSvalues();
    }

    /**
     * A {@link String}-based constructor that can handle both decimal
     * (37.5&deg;) and sexagesimal (35&deg;&thinsp;42&prime;&thinsp;13&Prime;)
     * representations.<br/>
     * Permissible symbol substitutions:
     * <table>
     *     <tr><th>Preferred</th><th>Alternate</th><th>Meaning</th></tr>
     *     <tr><td>&prime;</td><td>'</td><td>minutes</td></tr>
     *     <tr><td>&Prime;</td><td>&quot;</td><td>seconds</td></tr>
     * </table>
     * Values parsed from the input {@code expression} are subject to further validation,
     * e.g., minutes and seconds must fall in the interval [0,&nbsp;60).
     * @param expression a string representing an angular value in degrees
     */
    public AngularDegrees(String expression) {
        Matcher m = DMSpattern.matcher(expression);
        if (m.find()) {
            degrees = new IntegerImpl(m.group(1));
            minutes = new IntegerImpl(m.group(2));
            if (minutes.compareTo(SIXTY) >= 0) {
                throw new IllegalArgumentException("minutes of arc must be between 0 and 59 inclusive");
            }
            seconds = new RealImpl(m.group(3));
            if (seconds.asBigDecimal().compareTo(BigDecimal.valueOf(60L)) >= 0) {
                throw new IllegalArgumentException("seconds of arc must be \u2265 0 and < 60");
            }
            updateDecDegrees();
        } else {
            m = decimalDegreesPattern.matcher(expression);
            if (!m.find()) throw new IllegalArgumentException("Unable to parse expression " + expression);
            decDegrees = new RealImpl(m.group(1));
            updateDMSvalues();
        }
        mctx = decDegrees.getMathContext().getPrecision() > DEFAULT_CONTEXT.getPrecision() ?
                decDegrees.getMathContext() : DEFAULT_CONTEXT;
    }

    public IntegerType getDegrees() {
        return degrees;
    }

    public IntegerType getMinutes() {
        return minutes;
    }

    public RealType getSeconds() {
        return seconds;
    }

    public RealType getDecimalDegrees() {
        return decDegrees;
    }

    /**
     * Express this angle (stored in degrees, minutes, and seconds) as
     * a real value in radians.
     * @return this angle in radians
     */
    public RealType asRadians() {
        final Pi pi = Pi.getInstance(mctx);
        final RealType two = new RealImpl(BigDecimal.valueOf(2L), mctx);
        final RealType halfCircleDegrees = (RealType) DEGREES_IN_CIRCLE.divide(two);
        return (RealType) decDegrees.multiply(pi).divide(halfCircleDegrees);
    }

    public AngularDegrees add(AngularDegrees addend) {
        final MathContext ctx = MathUtils.inferMathContext(List.of(this.getSeconds(), addend.getSeconds()));
        final RealType reSixty = new RealImpl(SIXTY, ctx);
        final Numeric one = One.getInstance(ctx);
        RealType seconds = (RealType) this.seconds.add(addend.seconds);
        IntegerType minutes = (IntegerType) this.minutes.add(addend.minutes);
        IntegerType degrees = (IntegerType) this.degrees.add(addend.degrees);
        while (seconds.compareTo(reSixty) >= 0) {
            seconds = (RealType) seconds.subtract(reSixty);
            minutes = (IntegerType) minutes.add(one);
        }
        if (minutes.compareTo(SIXTY) >= 0) {
            degrees = (IntegerType) degrees.add(minutes.divide(SIXTY));
            minutes = minutes.modulus(SIXTY);
        }

        return new AngularDegrees(degrees, minutes, seconds);
    }

    private static final IntegerType fullCircle = new IntegerImpl("360");

    @Experimental
    public AngularDegrees subtract(AngularDegrees subtrahend) {
        final MathContext ctx = MathUtils.inferMathContext(List.of(this.getSeconds(), subtrahend.getSeconds()));
        final RealType reSixty = new RealImpl(SIXTY, ctx);
        final Numeric one = One.getInstance(ctx);
        RealType seconds = (RealType) this.seconds.subtract(subtrahend.seconds);
        IntegerType minutes = (IntegerType) this.minutes.subtract(subtrahend.minutes);
        IntegerType degrees = (IntegerType) this.degrees.subtract(subtrahend.degrees);
        while (seconds.sign() == Sign.NEGATIVE) {
            seconds = (RealType) seconds.add(reSixty);
            minutes = (IntegerType) minutes.subtract(one);
        }
        while (minutes.sign() == Sign.NEGATIVE) {
            degrees = (IntegerType) degrees.subtract(one);
            minutes = (IntegerType) minutes.add(SIXTY);
        }
        while (degrees.sign() == Sign.NEGATIVE) {
            degrees = (IntegerType) degrees.add(fullCircle);
        }

        return new AngularDegrees(degrees, minutes, seconds);
    }

    public void normalizeRange() {
        final IntegerType fullCircle = new IntegerImpl(BigInteger.valueOf(360L));
        if (degrees.sign() == Sign.NEGATIVE) {
            while (degrees.sign() == Sign.NEGATIVE) {
                degrees = (IntegerType) degrees.add(fullCircle);
            }
            minutes = (IntegerType) SIXTY.subtract(minutes);
            try {
                // just some code paranoia, on the off-chance that the result
                // of this subtraction is something other than a real value
                seconds = (RealType) SIXTY.subtract(seconds).coerceTo(RealType.class);
            } catch (CoercionException e) {
                throw new IllegalStateException("While normalizing negative DMS angle", e);
            }
        }
        if (degrees.compareTo(fullCircle) >= 0) {
            degrees = degrees.modulus(fullCircle);
        }
        updateDecDegrees();
    }

    private RealType fitToRange(RealType input) {
        RealType current = input;
        while (DECIMAL_DEGREE_RANGE.isBelow(current)) {
            current = (RealType) current.add(DEGREES_IN_CIRCLE);
        }
        while (DECIMAL_DEGREE_RANGE.isAbove(current)) {
            current = (RealType) current.subtract(DEGREES_IN_CIRCLE);
        }

        return current;
    }

    private void updateDecDegrees() {
        if (minutes == null) minutes = new IntegerImpl(BigInteger.ZERO);
        if (seconds == null) seconds = new RealImpl(BigDecimal.ZERO, DEFAULT_CONTEXT);
        try {
            if (degrees.sign() == Sign.NEGATIVE) {
                decDegrees = (RealType) degrees.subtract(minutes.divide(SIXTY))
                        .subtract(seconds.divide(SIXTY.multiply(SIXTY))).coerceTo(RealType.class);
            } else {
                decDegrees = (RealType) degrees.add(minutes.divide(SIXTY)).add(seconds.divide(SIXTY.multiply(SIXTY)))
                        .coerceTo(RealType.class);
            }
        } catch (CoercionException e) {
            throw new IllegalStateException("While computing decimal equivalent of " + this, e);
        }
    }

    private void updateDMSvalues() {
        this.degrees = decDegrees.floor();
        RealType realMinutes = (RealType) decDegrees.subtract(degrees).multiply(SIXTY);
        this.minutes = realMinutes.floor();
        this.seconds = (RealType) realMinutes.subtract(minutes).multiply(SIXTY);
    }

    public static boolean isDecimalDegrees(String input) {
        Matcher m = decimalDegreesPattern.matcher(input);
        return m.find();
    }

    public static boolean isDMS(String input) {
        Matcher m = DMSpattern.matcher(input);
        return m.find();
    }

    public static AngularDegrees forRadiansValue(RealType radians) {
        final Pi pi = Pi.getInstance(radians.getMathContext());
        final RealType two = new RealImpl(BigDecimal.valueOf(2L), radians.getMathContext());
        final RealType halfCircleDegrees = (RealType) DEGREES_IN_CIRCLE.divide(two);
        return new AngularDegrees((RealType) radians.multiply(halfCircleDegrees).divide(pi));
    }

    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder();

        buf.append(degrees).append(DEGREE_SIGN);
        if (minutes != null) {
            buf.append('\u2009').append(minutes).append(MINUTES_SIGN);
        }
        if (seconds != null) {
            buf.append('\u2009').append(seconds).append(SECONDS_SIGN);
        }

        return buf.toString();
    }

    /*
    Methods necessary for Groovy operator overloading follow.
     */
    AngularDegrees plus(AngularDegrees operand) {
        return this.add(operand);
    }
    AngularDegrees minus(AngularDegrees operand) {
        return this.subtract(operand);
    }
    AngularDegrees plus(RealType operand) {
        AngularDegrees that = new AngularDegrees(operand);
        return this.add(that);
    }
    AngularDegrees minus(RealType operand) {
        AngularDegrees that = new AngularDegrees(operand);
        return this.subtract(that);
    }
}
