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
import tungsten.types.numerics.impl.*;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.text.DecimalFormatSymbols;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A representation of angles measured in degrees. This class internally
 * supports both decimal and sexagesimal representations of angles, and
 * can freely convert between them.  This class also supports conversion
 * to radians, obviating the need to perform this conversion elsewhere.
 * @author Robert Poole, <a href="mailto:tarquin@alum.mit.edu">MIT alumni e-mail</a>
 */
public class AngularDegrees implements Comparable<AngularDegrees> {
    /**
     * The symbol (&deg;) used to denote degrees of arc.
     */
    public static final char DEGREE_SIGN = '\u00B0';
    /**
     * The symbol (&#x2032;) used to denote minutes of arc.
     */
    public static final char MINUTES_SIGN = '\u2032';  // prime, not apostrophe
    /**
     * The symbol (&#x2033;) used to denote seconds of arc.
     */
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
    private static final Pattern DMSpattern = Pattern.compile("([+-]?\\d+)\\u00B0\\s?(\\d+)['\u2032]\\s?(\\d+\\.?\\d*)[\"\u2033]");

    private IntegerType degrees;
    private IntegerType minutes;
    private RealType seconds;
    private RealType decDegrees;
    private final MathContext mctx;

    /**
     * Constructor using degrees, minutes, and seconds (DMS) values.
     * @param degrees an integer value for degrees of arc in the range [0,&nbsp;359],
     *                with corresponding negative values supported
     * @param minutes an integer value for minutes of arc in the range [0,&nbsp;59]
     * @param seconds a real value for seconds of arc in the range [0,&nbsp;60)
     */
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

    private static final RealType DEGREES_IN_CIRCLE = new RealImpl(BigDecimal.valueOf(360L), DEFAULT_CONTEXT);
    /**
     * The range of decimal degrees, 0&deg;&ndash;360&deg;.
     */
    public static final Range<RealType> DECIMAL_DEGREE_RANGE = new Range<>(new RealImpl(BigDecimal.ZERO, DEFAULT_CONTEXT), Range.BoundType.INCLUSIVE,
            DEGREES_IN_CIRCLE, Range.BoundType.EXCLUSIVE);

    /**
     * Constructor that takes decimal degrees.
     * @param decimalDegrees a real value for degrees of arc in the range [0,&nbsp;360),
     *                       with corresponding negative values supported
     */
    public AngularDegrees(RealType decimalDegrees) {
        mctx = decimalDegrees.getMathContext().getPrecision() > DEFAULT_CONTEXT.getPrecision() ?
                decimalDegrees.getMathContext() : DEFAULT_CONTEXT;
        this.decDegrees = fitToRange(decimalDegrees);
        updateDMSvalues();
    }

    /**
     * A {@link String}-based constructor that can handle both decimal
     * (37.5&deg;) and sexagesimal (35&deg;&thinsp;42&prime;&thinsp;13&Prime;)
     * representations.<br>
     * <table style="border: 1px solid black;border-collapse: collapse;">
     *     <caption>Permissible symbol substitutions</caption>
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
            final RealType reSixty = new RealImpl(SIXTY, DEFAULT_CONTEXT);
            if (seconds.compareTo(reSixty) >= 0) {
                throw new IllegalArgumentException("seconds of arc must be \u2265 0 and < 60");
            }
            updateDecDegrees();
        } else {
            m = decimalDegreesPattern.matcher(expression);
            if (!m.find()) throw new IllegalArgumentException("Unable to parse expression " + expression);
            final DecimalFormatSymbols dfSymbols = DecimalFormatSymbols.getInstance();
            final String decPoint = String.valueOf(dfSymbols.getDecimalSeparator());
            decDegrees = new RealImpl(m.group(1), new MathContext(m.group(1).replace(decPoint, "").length()));
            updateDMSvalues();
        }
        mctx = decDegrees.getMathContext().getPrecision() > DEFAULT_CONTEXT.getPrecision() ?
                decDegrees.getMathContext() : DEFAULT_CONTEXT;
    }

    /**
     * Obtain the degrees portion of this angle as an integer.
     * @return the whole number of degrees
     */
    public IntegerType getDegrees() {
        return degrees;
    }

    /**
     * Obtain the minutes portion of this angle as an integer.
     * @return the whole number of minutes
     */
    public IntegerType getMinutes() {
        return minutes;
    }

    /**
     * Obtain the seconds portion of this angle as a real value.
     * @return the seconds
     */
    public RealType getSeconds() {
        return seconds;
    }

    /**
     * Obtain the decimal equivalent of this degree value.
     * @return a real value equivalent to the DMS representation
     */
    public RealType getDecimalDegrees() {
        return decDegrees;
    }

    /**
     * Express this angle (stored in degrees, minutes, and seconds) as
     * a real value in radians.
     * @return this angle in radians
     */
    public RealType asRadians() {
        final MathContext compctx = new MathContext(mctx.getPrecision() + 2, mctx.getRoundingMode());
        // grab a couple extra digits of pi for better rounding behavior
        final Pi pi = Pi.getInstance(compctx);
        final RealType two = new RealImpl(BigDecimal.valueOf(2L), compctx);
        final RealType halfCircleDegrees = (RealType) DEGREES_IN_CIRCLE.divide(two);
        // if decDegrees.mathContext.precision < mctx.precision, this will promote decDegrees to a higher precision
        return (RealType) MathUtils.round(decDegrees, mctx).multiply(pi).divide(halfCircleDegrees);
    }

    /**
     * Add two angles represented in degrees, minutes, and seconds.
     * @param addend the angle in DMS to add to {@code this}
     * @return the sum of two angles in DMS notation
     */
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
            // it's more efficient to do this in one shot, and we need guaranteed integer division with truncation
            BigInteger[] results = minutes.asBigInteger().divideAndRemainder(SIXTY.asBigInteger());
            degrees = (IntegerType) degrees.add(new IntegerImpl(results[0]));
            minutes = new IntegerImpl(results[1]);
        }

        return new AngularDegrees(degrees, minutes, seconds);
    }

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

        return new AngularDegrees(degrees, minutes, seconds);
    }

    /**
     * Normalize this representation of an angle so that it falls
     * within the range of [0,&nbsp;360) degrees.
     * @apiNote Unlike most other methods that generate a brand new {@link AngularDegrees}
     *   object, this method will modify DMS values in-place. This may be useful for
     *   after-the-fact clean-up of data, for example.
     */
    public void normalizeRange() {
        final IntegerType fullCircle = new IntegerImpl(DEGREES_IN_CIRCLE.asBigDecimal().toBigInteger());
        if (degrees.sign() == Sign.NEGATIVE) {
            while (degrees.sign() == Sign.NEGATIVE) {
                degrees = (IntegerType) degrees.add(fullCircle);
            }
            if (!Zero.isZero(minutes)) minutes = (IntegerType) SIXTY.subtract(minutes);
            if (!Zero.isZero(seconds)) {
                try {
                    // just some code paranoia, on the off-chance that the result
                    // of this subtraction is something other than a real value
                    seconds = (RealType) SIXTY.subtract(seconds).coerceTo(RealType.class);
                } catch (CoercionException e) {
                    throw new IllegalStateException("While normalizing negative DMS angle", e);
                }
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
        try {
            RealType realMinutes = (RealType) decDegrees.subtract(degrees).multiply(SIXTY).coerceTo(RealType.class);
            this.minutes = realMinutes.floor();
            this.seconds = (RealType) realMinutes.subtract(minutes).multiply(SIXTY).coerceTo(RealType.class);
        } catch (CoercionException e) {
            Logger.getLogger(AngularDegrees.class.getName()).log(Level.WARNING,
                    "Unexpected failure computing minutes or seconds as real", e);
            if (this.minutes == null) this.minutes = new IntegerImpl(BigInteger.ZERO);
            this.seconds = new RealImpl(BigDecimal.ZERO, mctx);
        }
    }

    /**
     * Check whether the given {@link String} is an angle
     * expressed in decimal degrees notation.
     * @param input the textual value to check
     * @return true if the provided text is in decimal degrees format
     */
    public static boolean isDecimalDegrees(String input) {
        Matcher m = decimalDegreesPattern.matcher(input);
        return m.find();
    }

    /**
     * Determines if the given {@link String} is an angle
     * expressed in degrees-minutes-seconds (DMS) notation.
     * @param input the textual value to check
     * @return true if the provided text is in degrees-minutes-seconds format
     */
    public static boolean isDMS(String input) {
        Matcher m = DMSpattern.matcher(input);
        return m.find();
    }

    /**
     * Factory method to generate an instance of {@link AngularDegrees}
     * from an angle expressed in radians.
     * @param radians an angle in radians
     * @return a representation of the same angle in degrees
     */
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

    @Override
    public int compareTo(AngularDegrees o) {
        return this.decDegrees.compareTo(o.decDegrees);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AngularDegrees that = (AngularDegrees) o;
        return Objects.equals(degrees, that.degrees) && Objects.equals(minutes, that.minutes) && Objects.equals(seconds, that.seconds);
    }

    @Override
    public int hashCode() {
        return Objects.hash(degrees, minutes, seconds);
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
