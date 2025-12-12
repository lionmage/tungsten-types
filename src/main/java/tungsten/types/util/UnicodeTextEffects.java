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

import tungsten.types.Matrix;
import tungsten.types.Numeric;
import tungsten.types.annotations.RendererSupports;
import tungsten.types.exceptions.CoercionException;
import tungsten.types.exceptions.StrategyNotFoundException;
import tungsten.types.matrix.impl.BasicMatrix;
import tungsten.types.matrix.impl.ColumnarMatrix;
import tungsten.types.matrix.impl.ComplexMatrixAdapter;
import tungsten.types.matrix.impl.ParametricMatrix;
import tungsten.types.numerics.*;
import tungsten.types.numerics.impl.*;
import tungsten.types.util.rendering.matrix.cell.CellRenderingStrategy;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.math.RoundingMode;
import java.text.DecimalFormatSymbols;
import java.text.Normalizer;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.LongStream;

/**
 * Utility methods for creating Unicode strings that render with
 * superscript, subscript, overline, etc.
 *
 * @author Robert Poole <a href="mailto:Tarquin.AZ@gmail.com">Tarquin.AZ@gmail.com</a>
 */
public final class UnicodeTextEffects {
    // Unicode superscript numerals 0 - 9
    private static final String[] superscriptDigits = {
        "\u2070", "\u00B9", "\u00B2", "\u00B3", "\u2074", "\u2075",
        "\u2076", "\u2077", "\u2078", "\u2079"
    };
    // Unicode subscript numerals 0 - 9
    private static final String[] subscriptDigits = {
        "\u2080", "\u2081", "\u2082", "\u2083", "\u2084", "\u2085",
        "\u2086", "\u2087", "\u2088", "\u2089"
    };
    private static final Map<Character, String> superscriptMap;
    private static final Map<Character, String> subscriptMap;
    private static final Map<Integer, String> radicalMap;
    private static final String NEGATIVE_VALUES_PROHIBITED = "Negative fractional values prohibited";

    static {
        superscriptMap = new HashMap<>();
        subscriptMap = new HashMap<>();
        radicalMap = new TreeMap<>();
        
        superscriptMap.put('-', "\u207B");
        superscriptMap.put('\u2212', "\u207B");  // Unicode minus
        superscriptMap.put('+', "\u207A");
        superscriptMap.put('(', "\u207D");
        superscriptMap.put(')', "\u207E");
        superscriptMap.put('i', "\u2071");
        superscriptMap.put('\u2148', "\u2071"); // complex i
        superscriptMap.put('n', "\u207F");
        superscriptMap.put('=', "\u207C");
        superscriptMap.put('\u221E', "\u1AB2"); // infinity superscript is a combining character
        superscriptMap.put('A', "ᴬ");
        superscriptMap.put('B', "ᴮ");
        superscriptMap.put('D', "ᴰ");
        superscriptMap.put('E', "ᴱ");
        superscriptMap.put('G', "ᴳ");
        superscriptMap.put('H', "ᴴ");
        superscriptMap.put('I', "ᴵ");
        superscriptMap.put('J', "ᴶ");
        superscriptMap.put('K', "ᴷ");
        superscriptMap.put('L', "ᴸ");
        superscriptMap.put('M', "ᴹ");
        superscriptMap.put('N', "ᴺ");
        superscriptMap.put('O', "ᴼ");
        superscriptMap.put('P', "ᴾ");
        superscriptMap.put('R', "ᴿ");
        superscriptMap.put('T', "ᵀ");
        superscriptMap.put('U', "ᵁ");
        superscriptMap.put('V', "ⱽ");
        superscriptMap.put('W', "ᵂ");
        superscriptMap.put('a', "ᵃ");
        superscriptMap.put('b', "ᵇ");
        superscriptMap.put('c', "ᶜ");
        superscriptMap.put('d', "ᵈ");
        superscriptMap.put('e', "ᵉ");
        superscriptMap.put('f', "ᶠ");
        superscriptMap.put('g', "ᵍ");
        superscriptMap.put('h', "ʰ");
        superscriptMap.put('j', "ʲ");
        superscriptMap.put('k', "ᵏ");
        superscriptMap.put('l', "ˡ");
        superscriptMap.put('m', "ᵐ");
        superscriptMap.put('o', "ᵒ");
        superscriptMap.put('p', "ᵖ");
        superscriptMap.put('r', "ʳ");
        superscriptMap.put('s', "ˢ");
        superscriptMap.put('t', "ᵗ");
        superscriptMap.put('u', "ᵘ");
        superscriptMap.put('v', "ᵛ");
        superscriptMap.put('w', "ʷ");
        superscriptMap.put('x', "ˣ");
        superscriptMap.put('y', "ʸ");
        superscriptMap.put('z', "ᶻ");

        subscriptMap.put('-', "\u208B");
        subscriptMap.put('\u2212', "\u208B");  // Unicode minus
        subscriptMap.put('+', "\u208A");
        subscriptMap.put('(', "\u208D");
        subscriptMap.put(')', "\u208E");
        subscriptMap.put('=', "\u208C");
        subscriptMap.put('a', "\u2090");
        subscriptMap.put('e', "\u2091");
        subscriptMap.put('o', "\u2092");
        subscriptMap.put('x', "\u2093");
        subscriptMap.put('k', "\u2096");
        subscriptMap.put('n', "\u2099");
        subscriptMap.put('p', "\u209A");
        subscriptMap.put('t', "\u209C");
        subscriptMap.put('\u221E', "\u035A"); // infinity subscript appears to be a combining character
        subscriptMap.put('h', "ₕ");
        subscriptMap.put('i', "ᵢ");
        subscriptMap.put('\u2148', "ᵢ"); // complex i
        subscriptMap.put('j', "ⱼ");
        subscriptMap.put('l', "ₗ");
        subscriptMap.put('m', "ₘ");
        subscriptMap.put('r', "ᵣ");
        subscriptMap.put('s', "ₛ");
        subscriptMap.put('u', "ᵤ");
        subscriptMap.put('v', "ᵥ");
        
        radicalMap.put(2, "\u221A");
        radicalMap.put(3, "\u221B");
        radicalMap.put(4, "\u221C");
        for (int i = 5; i < 10; i++) {
            radicalMap.put(i, superscriptDigits[i] + "\u221A");
        }
    }

    private UnicodeTextEffects() {
        // this class should never be instantiable
    }

    /**
     * Given an integer, generate a superscript representing that integer.
     * @param n any integer
     * @return a {@code String} in the form of a superscript of the value of {@code n}
     */
    public static String numericSuperscript(int n) {
        StringBuilder buf = new StringBuilder();
        int digit;
        int k = Math.abs(n);
        do {
            digit = k % 10;
            k /= 10;
            
            buf.insert(0, superscriptDigits[digit]);
        } while (k != 0);
        if (n < 0) buf.insert(0, superscriptMap.get('-'));
        return buf.toString();
    }

    /**
     * Given an integer, generate a subscript representing that integer.
     * @param n any integer
     * @return a {@code String} in the form of a subscript of the value of {@code n}
     */
    public static String numericSubscript(int n) {
        StringBuilder buf = new StringBuilder();
        int digit;
        int k = Math.abs(n);
        do {
            digit = k % 10;
            k /= 10;
            
            buf.insert(0, subscriptDigits[digit]);
        } while (k != 0);
        if (n < 0) buf.insert(0, subscriptMap.get('-'));
        return buf.toString();
    }

    /**
     * Given a {@code String}, generate a superscripted version.
     * @param source the {@code String} to convert into a superscript
     * @return a {@code String} that is a superscripted version of {@code source}
     */
    public static String convertToSuperscript(String source) {
        StringBuilder buf = new StringBuilder();
        
        for (Character c : source.toCharArray()) {
            if (Character.isDigit(c)) {
                int digit = Character.digit(c, 10);
                buf.append(superscriptDigits[digit]);
            } else if (superscriptMap.containsKey(c)) {
                // special case logic
                if (!buf.isEmpty() && mapsToCombiningCharacter(c)) buf.append('\u2009');
                buf.append(superscriptMap.get(c));
            }
        }
        return buf.toString();
    }

    /**
     * Given a {@code String}, generate a subscripted version.
     * @param source the {@code String} to convert into a subscript
     * @return a {@code String} that is a subscripted version of {@code source}
     */
    public static String convertToSubscript(String source) {
        StringBuilder buf = new StringBuilder();
        boolean warnOnce = true;
        
        for (Character c : source.toCharArray()) {
            if (Character.isDigit(c)) {
                int digit = Character.digit(c, 10);
                buf.append(subscriptDigits[digit]);
            } else if (subscriptMap.containsKey(c)) {
                // add thinspace before combining characters
                if (!buf.isEmpty() && mapsToCombiningCharacter(c)) buf.append('\u2009');
                buf.append(subscriptMap.get(c));
            }
            if (warnOnce && Character.isUpperCase(c) && !subscriptMap.containsKey(c)) {
                Logger.getLogger(UnicodeTextEffects.class.getName()).log(Level.WARNING,
                        "Input string \"{0}\" contains upper-case characters which have no subscript mapping.", source);
                warnOnce = false;
            }
        }
        return buf.toString();
    }

    private static boolean mapsToCombiningCharacter(Character c) {
        if (c == '\u221E') return true;  // right now, we only have one super/subscript character that does this
        // there will likely be more case logic here
        return false;
    }
    
    private static final char COMBINING_OVERLINE = '\u0305';
    private static final char NEGATIVE_SIGN = '\u2212';

    /**
     * Given a {@link String}, generate a new {@link String}
     * containing the characters of the input with each
     * character followed by a combining
     * <a href="https://en.wikipedia.org/wiki/Overline">overline</a>,
     * overbar, or vinculum. Note that according to the Unicode standard for
     * <a href="https://en.wikipedia.org/wiki/Combining_character">combining characters</a>,
     * the overline character should follow the character it is modifying.
     * For some fonts and for some rendering systems, this may not render correctly;
     * it is not uncommon to see the overbar offset to the right. On such
     * systems, a common hack is to put the overline character first.
     * One example of such an environment is Groovy console in IntelliJ&nbsp;IDEA.
     * Such hacks are non-portable (i.e., do not render properly in other
     * applications, terminals, text consoles, shells, etc.) and will not be used.
     * @param source the original {@link String} to decorate
     * @return a transformed {@link String} which renders as the original text
     *   entirely covered by an overline
     */
    public static String overline(String source) {
        final StringBuilder buf = new StringBuilder();

        source.codePoints().forEach(cp -> buf.appendCodePoint(cp).append(COMBINING_OVERLINE));
        return buf.toString();
    }

    /**
     * Render an integer value as a {@link String} with an
     * <a href="https://en.wikipedia.org/wiki/Overline">overline</a>,
     * overbar, or vinculum covering all digits. Note that according to
     * the Unicode standard for
     * <a href="https://en.wikipedia.org/wiki/Combining_character">combining characters</a>,
     * the overline character should follow the character it is modifying.
     * For some fonts and for some rendering systems, this may not render correctly;
     * it is not uncommon to see the overbar offset to the right. On such
     * systems, a common hack is to put the overline character first.
     * One example of such an environment is Groovy console in IntelliJ&nbsp;IDEA.
     * Such hacks are non-portable (i.e., do not render properly in other
     * applications, terminals, text consoles, shells, etc.) and will not be used.
     * @param n the integer whose value will be rendered as a string
     * @return a string representation of {@code n}
     */
    public static String overline(int n) {
        StringBuilder buf = new StringBuilder();
        int digit;
        int k = Math.abs(n);
        do {
            digit = k % 10;
            k /= 10;
            
            buf.insert(0, digit);
            buf.insert(1, COMBINING_OVERLINE);
        } while (k != 0);
        if (n < 0) buf.insert(0, NEGATIVE_SIGN);  // Unicode representation of negative sign
        return buf.toString();
    }
    
    /**
     * Generates a {@code String} representation of a radical, e.g., a square
     * root, a cube root, or a root of some other degree.
     * @param <T> the type of the particular {@link Numeric} instance being rendered
     * @param radicand the {@link Numeric} value to be rendered inside the radical
     * @param degree the degree of the root to be rendered
     * @return a best-effort rendering of the specified radical
     */
    public static <T extends Numeric> String radical(T radicand, int degree) {
        if (degree < 2) {
            throw new IllegalArgumentException("Cannot render a radical with degree < 2 \u2014 arg was " + degree);
        }
        StringBuilder buf = new StringBuilder();
        if (degree >= 10) {
            buf.append(numericSuperscript(degree / 10));
            buf.append(radicalMap.getOrDefault(degree % 10, superscriptDigits[degree % 10] + "\u221A"));
        } else {
            buf.append(radicalMap.get(degree));
        }
        if (radicand instanceof ComplexType || OptionalOperations.sign(radicand) == Sign.NEGATIVE) {
            buf.append('(').append(radicand).append(')');
        } else {
            // Note: attempted to render this with overline, but the results were not very good
            // Issues with combining overline being used as a vinculum not lining up with the
            // characters below, not connecting to or lining up with the radical sign, etc.
            buf.append(radicand);
        }
        return buf.toString();
    }

    /**
     * Remove all combining characters from a {@link String} after first
     * normalizing it to {@link Normalizer.Form#NFKD NFKD} compatibility
     * decomposition form.
     * @param source the original {@code String}
     * @return the original {@code String} with all combining characters removed
     */
    public static String stripCombiningCharacters(final String source) {
        String expanded = Normalizer.isNormalized(source, Normalizer.Form.NFKD) ? source :
                Normalizer.normalize(source, Normalizer.Form.NFKD);
        return expanded.replaceAll("\\p{M}", "");
    }

    /**
     * Returns {@code true} if the source {@link String} contains any vulgar fractions,
     * including U+215F (Fraction Numerator One).  All vulgar fractions are part of the
     * Number Forms Unicode character block.
     * @param source the {@code String} to test
     * @return true if {@code source} contains any fraction characters, false otherwise
     */
    public static boolean hasVulgarFraction(final String source) {
        return source.chars().anyMatch(ch -> ch >= 0x2150 && ch <= 0x215F);
    }

    /**
     * Sanitize a decimal value to make it suitable for parsing,
     * e.g. with {@code String}-based constructors for {@link Numeric}
     * subtypes.  The Unicode symbol U+2212 (MINUS SIGN) is converted
     * into the locale-specific minus sign, and grouping separators
     * are stripped out.  Decimal point characters remain unchanged.
     * @param decStr a decimal value, with or without a decimal point
     * @return a sanitized string representing the same numeric value
     */
    public static String sanitizeDecimal(String decStr) {
        final DecimalFormatSymbols df = DecimalFormatSymbols.getInstance();
        String intermediate = decStr.replace(NEGATIVE_SIGN, df.getMinusSign());
        StringBuilder buf = new StringBuilder(intermediate.length());
        for (char c : intermediate.toCharArray()) {
            if (c != df.getGroupingSeparator()) buf.append(c);
        }
        return buf.toString();
    }

    /**
     * Expand any vulgar fraction into a proper fraction.  This method
     * is suitable for pre-processing a {@code String} containing fractions
     * for parsing and ingestion.<br>
     * <table>
     *     <caption>Examples</caption>
     *     <tr><th>Original</th><th>Converted</th></tr>
     *     <tr><td>&#x215F;30</td><td>1/30</td></tr>
     *     <tr><td>&#x2151;</td><td>1/9</td></tr>
     * </table>
     * @param source the {@code String} to be expanded
     * @return a {@code String} with all vulgar fractions expanded into a canonical form
     * @apiNote The current implementation will convert U+2044 Fraction Slash into a
     *   regular solidus (forward slash) character, which is guaranteed to be easily parseable.
     *   This transformation is not strictly necessary for use with e.g. the
     *   {@link RationalImpl#RationalImpl(String)} and related constructors. This transformation
     *   does have the advantage of rendering plainly, which makes this useful for debugging.
     */
    public static String expandFractions(final String source) {
        return Normalizer.normalize(source, Normalizer.Form.NFKC).replace('\u2044', '/');
    }

    private static final Map<Integer, String> romanDigits = new HashMap<>();
    // indices of Roman numeral glyphs that must only be used in the terminal position
    private static final List<Integer> terminalOnly = List.of(2, 3, 6, 7, 8, 11, 12);
    static {
        romanDigits.put(1000, "\u216F"); // M
        romanDigits.put(900, "\u216D\u216F"); // CM
        romanDigits.put(500, "\u216E");  // D
        romanDigits.put(400, "\u216D\u216E");  // CD
        romanDigits.put(100, "\u216D"); // C
        romanDigits.put(90, "\u2169\u216D"); // XC
        romanDigits.put(50, "\u216C"); // L
        romanDigits.put(40, "\u2169\u216C");  // XL
        romanDigits.put(12, "\u216B"); // XII, must only be used in terminal case
        romanDigits.put(11, "\u216A"); // XI, must only be used in terminal case
        romanDigits.put(10, "\u2169"); // X
        romanDigits.put(9, "\u2168");  // IX
        romanDigits.put(8, "\u2167");  // VIII
        romanDigits.put(7, "\u2166");  // VII
        romanDigits.put(6, "\u2165");  // VI
        romanDigits.put(5, "\u2164");  // V
        romanDigits.put(4, "\u2163");  // IV
        romanDigits.put(3, "\u2162");  // III
        romanDigits.put(2, "\u2161");  // II
        romanDigits.put(1, "\u2160");  // I
    }

    /**
     * Format a vector name for display.  By default, this takes
     * the form of an over-arrow.
     * @param name the variable name representing a vector
     * @return the variable name decorated with an over-arrow
     */
    public static String vectorNameForDisplay(String name) {
        if (name == null || name.isBlank()) return "";
        return name.strip() + '\u20D7'; // U+20D7 is the combining over-arrow
    }

    /**
     * Render the supplied value in Roman numerals.
     * @param number    the value to render
     * @param smallCase if true, render in small case (may be lower case or small caps depending on font)
     * @return a representation of {@code number} in Roman numerals
     * @throws IllegalArgumentException if {@code number} is 0 or negative
     */
    public static String inRomanNumerals(int number, boolean smallCase) {
        if (number < 1) throw new IllegalArgumentException("Cannot render " + number + " in Roman numerals");
        SortedSet<Integer> digitValues = new TreeSet<>(Comparator.reverseOrder());
        digitValues.addAll(romanDigits.keySet());
        StringBuilder result = new StringBuilder();

        for (Integer value : digitValues) {
            if (terminalOnly.contains(value)) {
                if (number == value) {
                    result.append(romanDigits.get(value));
                    break;
                }
                // otherwise skip this value
                continue;
            }
            while (number >= value) {
                number -= value;
                result.append(romanDigits.get(value));
            }
        }

        if (smallCase) {
            // the glyphs for small Roman numerals exist between 0x2170 and 0x217F
            // rather than store these redundantly, we can take the existing characters
            // and add 0x10 to each
            for (int i = 0; i < result.length(); i++) {
                result.setCharAt(i, (char) (result.charAt(i) + 0x10));
            }
        }
        return result.toString();
    }

    /**
     * Compute the width of a {@link CharSequence} or subsequence, measured in displayed characters.
     * This method assumes a monospaced font.<br>
     * This method will properly handle surrogate pairs and most combining characters
     * (e.g., vinculum, arrow).
     * @param source         a Unicode character sequence
     * @param startInclusive the starting character position of the region for this calculation
     * @param endExclusive   the end limit character position (exclusive)
     * @return the calculated character width of the specified substring
     */
    public static int computeCharacterWidth(CharSequence source, int startInclusive, int endExclusive) {
        if (endExclusive < startInclusive) {
            throw new IndexOutOfBoundsException("Start index must be \u2264 end index");
        } else if (endExclusive == startInclusive) {
            return 0;  // avoids a potential exception for a corner case, and executes fast
        }
        int width = Character.codePointCount(source, startInclusive, endExclusive);
        // subtract off any combining characters that don't add to a character's width
        width -= (int) source.subSequence(startInclusive, endExclusive).chars()
                .filter(cp -> Character.getType(cp) == Character.NON_SPACING_MARK)
                .count();
        return width;
    }

    /**
     * Compute the character width of an entire character sequence.
     * @param source a Unicode character sequence
     * @return the calculated character width of the specified character sequence
     */
    public static int computeCharacterWidth(CharSequence source) {
        return computeCharacterWidth(source, 0, source.length());
    }

    /**
     * Compute the character width of a character sequence, starting at the specified
     * position and continuing to the end of the sequence.
     * @param source a Unicode character sequence
     * @param start  the start position (0-based)
     * @return the calculated character width of the specified sequence, beginning at {@code start}
     */
    public static int computeCharacterWidth(CharSequence source, int start) {
        return computeCharacterWidth(source, start, source.length());
    }

    /**
     * Given a {@link StringBuilder} containing a representation of a decimal fraction,
     * trim the {@code StringBuilder} to the given number of fractional digits.
     * @param buffer        a {@code StringBuilder} containing a decimal value representation
     * @param decPointIndex the position of a decimal point in this representation
     * @param N             the number of fractional digits to allow
     */
    public static void trimToNFractionDigits(StringBuilder buffer, int decPointIndex, int N) {
        if (buffer.length() < decPointIndex) {
            throw new IllegalArgumentException("Decimal point index must be \u2264 buffer length");
        }
        if (buffer.length() == decPointIndex) return;  // no changes required
        int index = decPointIndex + 1;
        int digitCount = 0;
        while (index < buffer.length() && digitCount < N) {
            if (Character.isDigit(buffer.codePointAt(index))) {
                index = Character.offsetByCodePoints(buffer, index, 1);
                digitCount++;
            }
            // skip over any combining characters; we assume all combining characters fit into a single char
            if (index < buffer.length() && Character.getType(buffer.charAt(index)) == Character.NON_SPACING_MARK) {
                index++;
            }
        }
        if (digitCount == N && index < buffer.length()) {
            buffer.setLength(index);
        }
    }

    /**
     * Similar to {@link #trimToNFractionDigits(StringBuilder, int, int)} but
     * finds the position of the decimal point itself and does not mutate any
     * of its arguments.
     * @param original a {@code String} containing a representation of a decimal fraction
     * @param N        the number of decimal fraction digits to allow
     * @return a {@code String} containing a decimal value with only {@code N} fractional digits
     */
    public static String trimToNFractionDigits(String original, int N) {
        final char DEC_POINT = DecimalFormatSymbols.getInstance().getDecimalSeparator();
        int decPointIdx = original.indexOf(DEC_POINT);
        if (decPointIdx < 0) return original;
        // one of the few times this constructor is warranted, since this will never grow bigger
        StringBuilder buf = new StringBuilder(original);
        trimToNFractionDigits(buf, decPointIdx, N);
        return buf.toString();
    }

    /**
     * Compute the position of a decimal point in a given {@code CharSequence}.
     * @param original the character sequence to inspect
     * @param decPos   the assumed position of a decimal point with standard indexing
     * @return the actual position of the decimal point, taking combining characters and
     *   surrogate pairs into account
     */
    public static int computeActualDecimalPointCharPosition(CharSequence original, int decPos) {
        return computeCharacterWidth(original, 0, decPos);
    }

    /**
     * Compute the position of a decimal point in a {@code String} representation
     * of some numeric value.  If a decimal point is not found, it is assumed to
     * exist just past the end of {@code original}.
     * @param original the {@code String} to inspect
     * @return the position of the decimal point in {@code original}
     */
    public static int computeActualDecimalPointCharPosition(String original) {
        final char DEC_POINT = DecimalFormatSymbols.getInstance().getDecimalSeparator();
        int decPointIdx = original.indexOf(DEC_POINT);
        if (decPointIdx < 0) return computeCharacterWidth(original);
        return computeActualDecimalPointCharPosition(original, decPointIdx);
    }

    private static final RationalType ONE = new RationalImpl(BigInteger.ONE, BigInteger.ONE, MathContext.DECIMAL64);

    /**
     * Format a rational value as a proper fraction.  Whole values are separated from
     * the reduced fraction part, and the two parts are separated by the invisible plus
     * glyph.
     * @param frac any rational value
     * @return a {@code String} representing {@code frac}
     */
    public static String properFractionForDisplay(RationalType frac) {
        if (frac.magnitude().compareTo(ONE) < 0) return frac.toString();

        StringBuilder buf = new StringBuilder();
        if (frac.sign() == Sign.NEGATIVE) buf.append(NEGATIVE_SIGN);
        RationalType val = frac.magnitude().reduce();
        IntegerType whole = val.floor();
        buf.append(whole);
        try {
            RationalType fracPart = (RationalType) val.subtract(whole).coerceTo(RationalType.class);
            if (!Zero.isZero(fracPart)) {
                // U+2064 INVISIBLE PLUS is ideal for separating the whole from the fraction part
                buf.append('\u2064');
                if (One.isUnity(fracPart.numerator())) {
                    // U+215F fraction numerator one
                    buf.append('\u215F').append(fracPart.denominator());
                } else {
                    // insert a little extra thinspace since many fonts may not support correct fraction rendering
                    buf.append('\u2009').append(fracPart);
                }
            }
        } catch (CoercionException e) {
            throw new IllegalStateException("While converting the fraction " + frac + " for display", e);
        }
        return buf.toString();
    }

    /**
     * Format a function name for proper display.
     * @param fname           the name of the function; if {@code null}, uses &fnof;
     * @param derivativeOrder the order of the derivative applied to the function; 0 is the function itself
     * @param preferPrimes    if {@code true}, prefer the use of primes instead of numeric subscripts
     * @param argumentNames   one or more argument names
     * @return a representation of the specified function
     */
    public static String functionNameForDisplay(String fname, int derivativeOrder, boolean preferPrimes, String... argumentNames) {
        if (derivativeOrder < 0) throw new IllegalArgumentException("Order of derivative must be a non-negative integer");
        StringBuilder buf = new StringBuilder();
        buf.append(fname != null && !fname.isEmpty() ? fname : "\u0192");
        switch (derivativeOrder) {
            case 0:
                // the function itself
                if (!preferPrimes) buf.append(subscriptDigits[0]);
                break;
            case 1:
                if (preferPrimes) buf.append('\u2032'); // prime
                else buf.append(subscriptDigits[1]);
                break;
            case 2:
                if (preferPrimes) buf.append('\u2033'); // double-prime
                else buf.append(subscriptDigits[2]);
                break;
            case 3:
                if (preferPrimes) buf.append('\u2034'); // triple-prime
                else buf.append(subscriptDigits[3]);
                break;
            case 4:
                if (preferPrimes) buf.append('\u2057'); // quadruple-prime
                else buf.append(subscriptDigits[4]);
                break;
            default:
                // beyond a quadruple-prime, it would get messy to render with just primes
                buf.append(numericSubscript(derivativeOrder));
                break;
        }
        buf.append('(');
        if (argumentNames != null) {
            // using thinspace as the whitespace separator
            buf.append(String.join(",\u2009", argumentNames));
        }
        buf.append(')');

        return buf.toString();
    }

    /**
     * Format the given matrix in a manner suitable for display.
     * @param M           the {@code Matrix} to render
     * @param superscript a numeric superscript, can be null
     * @param subscript   a numeric subscript, can be null
     * @return a {@code String} containing a multi-line formatted representation of {@code M}
     */
    public static String formatMatrixForDisplay(Matrix<? extends Numeric> M, Numeric superscript, Numeric subscript) {
        String sup = null;
        String sub = null;
        if (superscript instanceof IntegerType) {
            sup = numericSuperscript(((IntegerType) superscript).asBigInteger().intValueExact());
        } else if (superscript != null) {
            sup = convertToSuperscript(superscript.toString());
        }
        if (subscript instanceof  IntegerType) {
            sub = numericSubscript(((IntegerType) subscript).asBigInteger().intValueExact());
        } else if (subscript != null) {
            sub = convertToSubscript(subscript.toString());
        }
        return formatMatrixForDisplay(M, sup, sub);
    }

    /**
     * Format the given matrix in a manner suitable for display.
     * The supplied {@code superscript} and {@code subscript} arguments
     * are included as-is; if you want these to render in the
     * appropriate size and font, consider using {@link #convertToSuperscript(String)}
     * and {@link #convertToSubscript(String)}, respectively, to convert plaintext.
     * @param M           the {@code Matrix} to render
     * @param superscript a superscript, can be null
     * @param subscript   a subscript, can be null
     * @return a {@code String} containing a multi-line formatted representation of {@code M}
     */
    public static String formatMatrixForDisplay(Matrix<? extends Numeric> M, String superscript, String subscript) {
        Class<? extends Numeric> elementType = OptionalOperations.findTypeFor(M);
        NumericHierarchy htype = NumericHierarchy.forNumericType(elementType);
        if (M.columns() > (long) Integer.MAX_VALUE) throw new UnsupportedOperationException("Column indices > 32 bits are unsupported");
        if (htype == null) {
            // ensure M is represented as some concrete type
            if (MathUtils.containsAny(M, ComplexType.class)) {
                M = new ComplexMatrixAdapter(M);
                htype = NumericHierarchy.COMPLEX;
            } else {
                // create a RealType view of M
                if (M instanceof BasicMatrix) M = ((BasicMatrix<? extends Numeric>) M).upconvert(RealType.class);
                else if (M instanceof ColumnarMatrix) M = ((ColumnarMatrix<? extends Numeric>) M).upconvert(RealType.class);
                else if (M instanceof ParametricMatrix) M = ((ParametricMatrix<? extends Numeric>) M).upconvert(RealType.class);
                else M = new BasicMatrix<>(M).upconvert(RealType.class);  // if all else fails, clone M with a BasicMatrix and then upconvert that

                htype = NumericHierarchy.REAL;
            }
        }
        CellRenderingStrategy strategy = getStrategyForCellType(htype);
        // For small matrices, inspect the cells of all rows.
        // Otherwise, pick a random subset of rows and inspect only those.
        LongStream indexStream = M.rows() <= 10L ? LongStream.range(0L, M.rows()) :
                MathUtils.randomIndexPermutation(M.rows(), M.rows() <= 30L ? M.rows() / 2L : M.rows() / 3L).stream().mapToLong(Long::longValue);
        final Matrix<? extends Numeric> finalM = M;
        indexStream.forEach(k -> strategy.inspect(finalM.getRow(k)));
        // now assemble the thing
        StringBuilder buf = new StringBuilder();
        for (long row = 0L; row < M.rows(); row++) {
            // append the beginning of the line
            if (row == 0L) buf.append("\u23A1 ");
            else if (row == M.rows() - 1L) buf.append("\u23A3 ");
            else buf.append("\u23A2 ");

            buf.append(strategy.render(M.getRow(row)));

            // append the end
            if (row == 0L) {
                buf.append(" \u23A4");
                if (superscript != null) buf.append(superscript); // may need to insert thinspace here
            }
            else if (row == M.rows() - 1L) {
                buf.append(" \u23A6");
                if (subscript != null) buf.append(subscript); // may need thinspace here, too
            }
            else buf.append(" \u23A5");
            if (row != M.rows() - 1L) buf.append('\n');  // EOL for all but the last line
        }

        return buf.toString();
    }

    private static CellRenderingStrategy getStrategyForCellType(NumericHierarchy type) {
        ServiceLoader<CellRenderingStrategy> serviceLoader = ServiceLoader.load(CellRenderingStrategy.class);
        return serviceLoader.stream().filter(p -> p.type().isAnnotationPresent(RendererSupports.class))
                .filter(p -> p.type().getAnnotation(RendererSupports.class).type() == type)
                .map(ServiceLoader.Provider::get).findFirst()
                .orElseThrow(() -> new StrategyNotFoundException("No strategy found for cells of type " + type));
    }

    public enum ShadedBlock {
        EMPTY(' '), LIGHT('\u2591'), MEDIUM('\u2592'), DARK('\u2593'), SOLID('\u2588');

        private final char rep;

        ShadedBlock(char representation) {
            this.rep = representation;
        }

        public String nOf(int n) {
            return String.valueOf(rep).repeat(Math.max(0, n));
        }

        @Override
        public String toString() {
            return Character.toString(rep);
        }
    }

    public enum FractionalHorizontalBlock {
        EMPTY(' ', new RationalImpl(BigInteger.ZERO, BigInteger.ONE, MathContext.DECIMAL64)),
        ONE_EIGHTH('\u258F', new RationalImpl("1/8", MathContext.DECIMAL64)),
        ONE_FOURTH('\u258E', new RationalImpl("1/4", MathContext.DECIMAL64)),
        THREE_EIGHTHS('\u258D', new RationalImpl("3/8", MathContext.DECIMAL64)),
        ONE_HALF('\u258C', new RationalImpl("1/2", MathContext.DECIMAL64)),
        FIVE_EIGHTHS('\u258B', new RationalImpl("5/8", MathContext.DECIMAL64)),
        THREE_FOURTHS('\u258A', new RationalImpl("3/4", MathContext.DECIMAL64)),
        SEVEN_EIGHTHS('\u2589', new RationalImpl("7/8", MathContext.DECIMAL64)),
        FULL('\u2588', ONE);

        private final char rep;
        private final RationalType value;

        FractionalHorizontalBlock(char representation, RationalType value) {
            this.rep = representation;
            this.value = value;
        }

        @Override
        public String toString() {
            return Character.toString(rep);
        }

        public String nOf(int n) { return String.valueOf(rep).repeat(n); }

        public RealType fillPercentage() {
            final MathContext ctx = new MathContext(3, RoundingMode.HALF_UP);
            final RealType oneHundred = new RealImpl(BigDecimal.valueOf(100L), ctx);
            try {
                return (RealType) oneHundred.multiply(value).coerceTo(RealType.class);
            } catch (CoercionException e) {
                throw new IllegalStateException("While obtaining fill percentage", e);
            }
        }

        public static FractionalHorizontalBlock forFraction(RationalType frac) {
            if (frac.sign() == Sign.NEGATIVE) throw new IllegalArgumentException(NEGATIVE_VALUES_PROHIBITED);
            boolean takeFraction = frac.numerator().magnitude().compareTo(frac.denominator()) > 0; // frac > 1
            RationalType onlyFrac = takeFraction ? (RationalType) frac.subtract(MathUtils.trunc(frac)) : frac;
            FractionalHorizontalBlock block = FULL;
            for (FractionalHorizontalBlock candidate : values()) {
                if (candidate.value.compareTo(onlyFrac) > 0) continue;
                block = candidate;
            }
            return block;
        }

        public static FractionalHorizontalBlock forFraction(RealType frac) {
            if (frac.sign() == Sign.NEGATIVE) throw new IllegalArgumentException(NEGATIVE_VALUES_PROHIBITED);
            if (Zero.isZero(frac)) return EMPTY;
            if (One.isUnity(frac)) return FULL;
            RealType onlyFrac = (RealType) frac.subtract(MathUtils.trunc(frac));
            FractionalHorizontalBlock block = FULL;
            for (FractionalHorizontalBlock candidate : values()) {
                if (candidate.value.asBigDecimal().compareTo(onlyFrac.asBigDecimal()) > 0) continue;
                block = candidate;
            }
            return block;
        }
    }

    public enum FractionalVerticalBlock {
        EMPTY(' ', new RationalImpl(BigInteger.ZERO, BigInteger.ONE, MathContext.DECIMAL64)),
        ONE_EIGHTH('\u2581', new RationalImpl("1/8", MathContext.DECIMAL64)),
        ONE_FOURTH('\u2582', new RationalImpl("1/4", MathContext.DECIMAL64)),
        THREE_EIGHTHS('\u2583', new RationalImpl("3/8", MathContext.DECIMAL64)),
        ONE_HALF('\u2584', new RationalImpl("1/2", MathContext.DECIMAL64)),
        FIVE_EIGHTHS('\u2585', new RationalImpl("5/8", MathContext.DECIMAL64)),
        THREE_FOURTHS('\u2586', new RationalImpl("3/4", MathContext.DECIMAL64)),
        SEVEN_EIGHTHS('\u2587', new RationalImpl("7/8", MathContext.DECIMAL64)),
        FULL('\u2588', ONE);

        private final char rep;
        private final RationalType value;

        FractionalVerticalBlock(char representation, RationalType value) {
            this.rep = representation;
            this.value = value;
        }

        @Override
        public String toString() {
            return Character.toString(rep);
        }

        public RealType fillPercentage() {
            final MathContext ctx = new MathContext(3, RoundingMode.HALF_UP);
            final RealType oneHundred = new RealImpl(BigDecimal.valueOf(100L), ctx);
            try {
                return (RealType) oneHundred.multiply(value).coerceTo(RealType.class);
            } catch (CoercionException e) {
                throw new IllegalStateException("While obtaining fill percentage", e);
            }
        }

        public static FractionalVerticalBlock forFraction(RationalType frac) {
            if (frac.sign() == Sign.NEGATIVE) throw new IllegalArgumentException(NEGATIVE_VALUES_PROHIBITED);
            boolean takeFraction = frac.numerator().magnitude().compareTo(frac.denominator()) > 0; // frac > 1
            RationalType onlyFrac = takeFraction ? (RationalType) frac.subtract(MathUtils.trunc(frac)) : frac;
            FractionalVerticalBlock block = FULL;
            for (FractionalVerticalBlock candidate : values()) {
                if (candidate.value.compareTo(onlyFrac) <= 0) continue;
                block = candidate;
            }
            return block;
        }

        public static FractionalVerticalBlock forFraction(RealType frac) {
            if (frac.sign() == Sign.NEGATIVE) throw new IllegalArgumentException(NEGATIVE_VALUES_PROHIBITED);
            if (Zero.isZero(frac)) return EMPTY;
            if (One.isUnity(frac)) return FULL;
            FractionalVerticalBlock block = FULL;
            RealType onlyFrac = (RealType) frac.subtract(MathUtils.trunc(frac));
            for (FractionalVerticalBlock candidate : values()) {
                if (candidate.value.asBigDecimal().compareTo(onlyFrac.asBigDecimal()) > 0) continue;
                block = candidate;
            }
            return block;
        }
    }

    /**
     * These represent Unicode graphic blocks that &ldquo;grow&rdquo; down from above.
     * Note that there are fewer defined choices here than for, e.g., {@link FractionalVerticalBlock}.
     * This will limit the accuracy in plotting a histogram with negative values.
     */
    public enum FractionalVerticalInverseBlock {
        EMPTY(' ', new RationalImpl(BigInteger.ZERO, BigInteger.ONE, MathContext.DECIMAL64)),
        ONE_EIGHTH('\u2594', new RationalImpl("1/8", MathContext.DECIMAL64)),
        ONE_HALF('\u2580', new RationalImpl("1/2", MathContext.DECIMAL64)),
        SEVEN_EIGHTHS('\u2593', new RationalImpl("7/8", MathContext.DECIMAL64)), // we're close to whole, so fake it with a dark shaded block
        FULL('\u2588', ONE);

        private final char rep;
        private final RationalType value;

        FractionalVerticalInverseBlock(char representation, RationalType value) {
            this.rep = representation;
            this.value = value;
        }

        @Override
        public String toString() {
            return Character.toString(rep);
        }

        public RealType fillPercentage() {
            final MathContext ctx = new MathContext(3, RoundingMode.HALF_UP);
            final RealType oneHundred = new RealImpl(BigDecimal.valueOf(100L), ctx);
            try {
                return (RealType) oneHundred.multiply(value).coerceTo(RealType.class);
            } catch (CoercionException e) {
                throw new IllegalStateException("While obtaining fill percentage", e);
            }
        }

        public static FractionalVerticalInverseBlock forFraction(RationalType frac) {
            boolean takeFraction = frac.numerator().magnitude().compareTo(frac.denominator()) > 0; // frac > 1
            RationalType onlyFrac = takeFraction ? (RationalType) frac.magnitude().subtract(MathUtils.trunc(frac.magnitude())) : frac.magnitude();
            FractionalVerticalInverseBlock block = FULL;
            for (FractionalVerticalInverseBlock candidate : values()) {
                if (candidate.value.compareTo(onlyFrac) > 0) continue;
                block = candidate;
            }
            return block;
        }

        public static FractionalVerticalInverseBlock forFraction(RealType frac) {
            if (Zero.isZero(frac)) return EMPTY;
            if (One.isUnity(frac)) return FULL;
            RealType onlyFrac = (RealType) frac.magnitude().subtract(MathUtils.trunc(frac.magnitude()));
            FractionalVerticalInverseBlock block = FULL;
            for (FractionalVerticalInverseBlock candidate : values()) {
                if (candidate.value.asBigDecimal().compareTo(onlyFrac.asBigDecimal()) > 0) continue;
                block = candidate;
            }
            return block;
        }
    }

    public enum HorizontalFill {
        EMPTY(' '), LIGHT_TRIPLE_DASH('\u2504'), LIGHT_QUADRUPLE_DASH('\u2508'),
        HEAVY_TRIPLE_DASH('\u2505'), HEAVY_QUADRUPLE_DASH('\u2509');

        private final char rep;

        HorizontalFill(char representation) {
            this.rep = representation;
        }

        public String nOf(int n) {
            return String.valueOf(rep).repeat(Math.max(0, n));
        }

        @Override
        public String toString() {
            return Character.toString(rep);
        }
    }

    public enum VerticalFill {
        EMPTY(' '), LIGHT_TRIPLE_DASH('\u2506'), LIGHT_QUADRUPLE_DASH('\u250A'),
        HEAVY_TRIPLE_DASH('\u2507'), HEAVY_QUADRUPLE_DASH('\u250B');

        private final char rep;

        VerticalFill(char representation) {
            this.rep = representation;
        }

        @Override
        public String toString() {
            return Character.toString(rep);
        }
    }

    /**
     * Generate a histogram using Unicode symbols given a set of data values. Data is presented as a
     * series of vertical bars, one for each data point present in the input, laid out left-to-right.
     *
     * @param values        a {@link List} of real values, each representing a histogram &ldquo;bucket&rdquo;
     * @param blockHeight   the height of the generated histogram in character rows
     * @param hruleInterval the frequency of horizontal rules to be plotted; horizontal rules will be generated
     *                      every {@code hruleInterval} rows
     * @param spaceBetween  if true, generate horizontal fill between vertical bars representing data
     * @return a {@link List} of {@link String}s representing the histogram plot, in rendering order
     */
    public static List<String> histoPlot(List<RealType> values, int blockHeight, int hruleInterval, boolean spaceBetween) {
        List<String> rows = new ArrayList<>(blockHeight);
        // no negatives
        if (values.parallelStream().anyMatch(value -> value.sign() == Sign.NEGATIVE)) throw new IllegalArgumentException("Negative histogram values are unsupported");
        final RealType maxVal = values.parallelStream().max(RealType::compareTo).orElseThrow();
        try {
            final RealType blockSize = (RealType) maxVal.divide(new IntegerImpl(BigInteger.valueOf(blockHeight))).coerceTo(RealType.class);

            int width = values.size();
            if (spaceBetween) width += values.size() - 1;
            for (int row = 0; row < blockHeight; row++) {
                StringBuilder buf = new StringBuilder(width);
                RealType rowTop = (RealType) blockSize.multiply(new IntegerImpl(BigInteger.valueOf(row + 1L))).coerceTo(RealType.class);
                RealType rowBottom = (RealType) blockSize.multiply(new IntegerImpl(BigInteger.valueOf(row))).coerceTo(RealType.class);
                for (RealType value : values) {
                    if (value.compareTo(rowBottom) <= 0) {
                        buf.append(row % hruleInterval == 0 ? HorizontalFill.LIGHT_TRIPLE_DASH : HorizontalFill.EMPTY);
                    } else {
                        // plot the full or partial block
                        if (value.compareTo(rowTop) >= 0) {
                            buf.append(FractionalVerticalBlock.FULL);
                        } else {
                            RealType frac = (RealType) value.subtract(rowBottom).divide(blockSize);
                            buf.append(FractionalVerticalBlock.forFraction(frac));
                        }
                    }
                    if (spaceBetween && buf.length() < width)
                        buf.append(row % hruleInterval == 0 ? HorizontalFill.LIGHT_TRIPLE_DASH : HorizontalFill.EMPTY);
                }

                // finally, append completed row to our collection
                rows.add(buf.toString());
            }

            Collections.reverse(rows);
            return rows;
        } catch (CoercionException ex) {
            Logger.getLogger(UnicodeTextEffects.class.getName()).log(Level.SEVERE,
                    "While generating histogram plot", ex);
            throw new IllegalStateException(ex);
        }
    }

    /**
     * Generate a bar chart / histogram plot with each datum represented by a horizontal bar.
     *
     * @param labels          the labels for the data points; if null, no labels are rendered
     * @param labelBlockWidth the width of the labels, in character blocks; ignored if no labels are present
     * @param values          the real-valued data points or &ldquo;buckets&rdquo; to be plotted
     * @param blockWidth      the width of the plot, in character blocks
     * @param vruleInterval   the frequency of vertical rules to be plotted; vertical rules
     *                        will be generated every {@code vruleInterval} columns
     * @param spaceBetween    if true, generate vertical fill between horizontal bars
     *                        representing data
     * @return a {@link List} of {@link String}s representing the histogram plot, in rendering order
     */
    public static List<String> horizontalHistoPlot(String[] labels, int labelBlockWidth, List<RealType> values, int blockWidth, int vruleInterval, boolean spaceBetween) {
        if (labels != null && labels.length != values.size()) throw new IllegalArgumentException("Number of labels must match number of samples");
        List<String> rows = new ArrayList<>(spaceBetween ? values.size() * 2 - 1 : values.size());

        if (values.parallelStream().anyMatch(value -> value.sign() == Sign.NEGATIVE)) throw new IllegalArgumentException("Negative histogram values are unsupported");
        final RealType maxVal = values.parallelStream().max(RealType::compareTo).orElseThrow();
        try {
            final RealType blockSize = (RealType) maxVal.divide(new IntegerImpl(BigInteger.valueOf(blockWidth))).coerceTo(RealType.class);

            for (int k = 0; k < values.size(); k++) {
                StringBuilder buf = new StringBuilder(blockWidth + (labels == null ? 0 : labelBlockWidth));
                if (labels != null) {
                    // if there are labels, render them right-justified
                    if (labels[k].length() > labelBlockWidth) {
                        // label is too long, so render with an ellipsis
                        buf.append(labels[k], 0, labelBlockWidth - 1).append('\u2026');
                    } else {
                        int fill = labelBlockWidth - labels[k].length();
                        if (fill > 0) buf.append(" ".repeat(fill));
                        buf.append(labels[k]);
                    }
                }
                // compute the number of solid blocks
                RealType barWidth = (RealType) values.get(k).divide(blockSize).coerceTo(RealType.class);
                int solidFill = barWidth.floor().asBigInteger().intValue();
                RealType frac = (RealType) barWidth.subtract(barWidth.floor()).coerceTo(RealType.class);
                buf.append(FractionalHorizontalBlock.FULL.nOf(solidFill)).append(FractionalHorizontalBlock.forFraction(frac));
                // fill in the remainder of the row
                for (int j = solidFill + 1; j < blockWidth; j++) {
                    buf.append(j % vruleInterval == 0 ? VerticalFill.LIGHT_TRIPLE_DASH : VerticalFill.EMPTY);
                }
                rows.add(buf.toString());

                // and then add a row of space if needed
                if (spaceBetween && k < values.size() - 1) {
                    buf = new StringBuilder(blockWidth + (labels == null ? 0 : labelBlockWidth));
                    if (labels != null) {
                        buf.append(" ".repeat(labelBlockWidth));
                    }
                    for (int j = 0; j < blockWidth; j++) {
                        buf.append(j % vruleInterval == 0 ? VerticalFill.LIGHT_TRIPLE_DASH : VerticalFill.EMPTY);
                    }

                    rows.add(buf.toString());
                }
            }
        } catch (CoercionException ex) {
            Logger.getLogger(UnicodeTextEffects.class.getName()).log(Level.SEVERE,
                    "While generating horizontal histogram plot", ex);
            throw new IllegalStateException(ex);
        }

        return rows;
    }
}
