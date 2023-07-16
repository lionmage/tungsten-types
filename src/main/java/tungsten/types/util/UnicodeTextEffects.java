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
import tungsten.types.numerics.*;
import tungsten.types.numerics.impl.*;
import tungsten.types.util.rendering.matrix.cell.CellRenderingStrategy;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.text.DecimalFormatSymbols;
import java.text.Normalizer;
import java.util.*;
import java.util.stream.LongStream;

/**
 * Utility methods for creating Unicode strings that render with
 * superscript, subscript, overline, etc.
 *
 * @author Robert Poole <a href="mailto:Tarquin.AZ@gmail.com">Tarquin.AZ@gmail.com</a>
 */
public class UnicodeTextEffects {
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
        superscriptMap.put('n', "\u207F");
        superscriptMap.put('=', "\u207C");
        superscriptMap.put('\u221E', "\u1AB2"); // infinity superscript is a combining character
        
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
        
        radicalMap.put(2, "\u221A");
        radicalMap.put(3, "\u221B");
        radicalMap.put(4, "\u221C");
        for (int i = 5; i < 10; i++) {
            radicalMap.put(i, superscriptDigits[i] + "\u221A");
        }
    }
    
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
    
    public static String convertToSuperscript(String source) {
        StringBuilder buf = new StringBuilder();
        
        for (Character c : source.toCharArray()) {
            if (Character.isDigit(c)) {
                int digit = Character.digit(c, 10);
                buf.append(superscriptDigits[digit]);
            } else if (superscriptMap.containsKey(c)) {
                // special case logic
                if (buf.length() > 0 && mapsToCombiningCharacter(c)) buf.append('\u2009');
                buf.append(superscriptMap.get(c));
            }
        }
        return buf.toString();
    }

    public static String convertToSubscript(String source) {
        StringBuilder buf = new StringBuilder();
        
        for (Character c : source.toCharArray()) {
            if (Character.isDigit(c)) {
                int digit = Character.digit(c, 10);
                buf.append(subscriptDigits[digit]);
            } else if (subscriptMap.containsKey(c)) {
                // add thinspace before combining characters
                if (buf.length() > 0 && mapsToCombiningCharacter(c)) buf.append('\u2009');
                buf.append(subscriptMap.get(c));
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
        StringBuilder buf = new StringBuilder();
        
        for (Character c : source.toCharArray()) {
            buf.append(c).append(COMBINING_OVERLINE);
        }
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

    public static String stripCombiningCharacters(final String source) {
        String expanded = Normalizer.isNormalized(source, Normalizer.Form.NFKD) ? source :
                Normalizer.normalize(source, Normalizer.Form.NFKD);
        return expanded.replaceAll("\\p{M}", "");
    }

    /**
     * Compute the width of a {@link CharSequence} or subsequence, measured in displayed characters.
     * This method assumes a monospaced font.<br/>
     * This method will properly handle surrogate pairs and most combining characters
     * (e.g., vinculum, arrow).
     * @param source         a Unicode character sequence
     * @param startInclusive the starting character position of the region for this calculation
     * @param endExclusive   the end limit character position (exclusive)
     * @return the calculated character width of the specified substring
     */
    public static int computeCharacterWidth(CharSequence source, int startInclusive, int endExclusive) {
        if (endExclusive < startInclusive) {
            throw new IndexOutOfBoundsException("Start index must be < end index");
        } else if (endExclusive == startInclusive) {
            return 0;  // avoids a potential exception for a corner case, and executes fast
        }
        int width = Character.codePointCount(source, startInclusive, endExclusive);
        // subtract off any combining characters that don't add to a character's width
        width -= source.subSequence(startInclusive, endExclusive).chars()
                .filter(cp -> Character.getType(cp) == Character.NON_SPACING_MARK)
                .count();
        return width;
    }

    public static int computeCharacterWidth(CharSequence source) {
        return computeCharacterWidth(source, 0, source.length());
    }

    public static int computeCharacterWidth(CharSequence source, int start) {
        return computeCharacterWidth(source, start, source.length());
    }

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

    public static String trimToNFractionDigits(String original, int N) {
        final char DEC_POINT = DecimalFormatSymbols.getInstance().getDecimalSeparator();
        int decPointIdx = original.indexOf(DEC_POINT);
        if (decPointIdx < 0) return original;
        // one of the few times this constructor is warranted, since this will never grow bigger
        StringBuilder buf = new StringBuilder(original);
        trimToNFractionDigits(buf, decPointIdx, N);
        return buf.toString();
    }

    public static int computeActualDecimalPointCharPosition(CharSequence original, int decPos) {
        return computeCharacterWidth(original, 0, decPos);
    }

    public static int computeActualDecimalPointCharPosition(String original) {
        final char DEC_POINT = DecimalFormatSymbols.getInstance().getDecimalSeparator();
        int decPointIdx = original.indexOf(DEC_POINT);
        if (decPointIdx < 0) return computeCharacterWidth(original);
        return computeActualDecimalPointCharPosition(original, decPointIdx);
    }

    private static final RationalType ONE = new RationalImpl(BigInteger.ONE, BigInteger.ONE);

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
                buf.append('\u2064').append(fracPart);
            }
        } catch (CoercionException e) {
            throw new IllegalStateException("While converting the fraction " + frac + " for display", e);
        }
        return buf.toString();
    }

    public static String functionNameForDisplay(String fname, int derivativeOrder, boolean preferPrimes, String... argumentNames) {
        if (derivativeOrder < 0) throw new IllegalArgumentException("Order of derivative must be a non-negative integer");
        StringBuilder buf = new StringBuilder();
        buf.append(fname != null && fname.length() > 0 ? fname : "\u0192");
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

    public static String formatMatrixForDisplay(Matrix<? extends Numeric> M, String superscript, String subscript) {
        Class<? extends Numeric> elementType = OptionalOperations.findTypeFor(M);
        NumericHierarchy htype = NumericHierarchy.forNumericType(elementType);
        if (M.columns() > (long) Integer.MAX_VALUE) throw new UnsupportedOperationException("Column indices > 32 bits are unsupported");
        CellRenderingStrategy strategy = getStrategyForCellType(htype);
        // For small matrices, inspect the cells of all rows.
        // Otherwise, pick a random subset of rows and inspect only those.
        LongStream indexStream = M.rows() <= 10L ? LongStream.range(0L, M.rows()) :
                MathUtils.randomIndexPermutation(M.rows(), M.rows() <= 30L ? M.rows() / 2L : M.rows() / 3L).stream().mapToLong(Long::longValue);
        indexStream.forEach(k -> strategy.inspect(M.getRow(k)));
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
        EMPTY(' ', new RationalImpl(BigInteger.ZERO, BigInteger.ONE)),
        ONE_EIGHTH('\u258F', new RationalImpl("1/8")),
        ONE_FOURTH('\u258E', new RationalImpl("1/4")),
        THREE_EIGHTHS('\u258D', new RationalImpl("3/8")),
        ONE_HALF('\u258C', new RationalImpl("1/2")),
        FIVE_EIGHTHS('\u258B', new RationalImpl("5/8")),
        THREE_FOURTHS('\u258A', new RationalImpl("3/4")),
        SEVEN_EIGHTHS('\u2589', new RationalImpl("7/8")),
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
            final RealType oneHundred = new RealImpl(BigDecimal.valueOf(100L));
            try {
                return (RealType) oneHundred.multiply(value).coerceTo(RealType.class);
            } catch (CoercionException e) {
                throw new IllegalStateException(e);
            }
        }

        public static FractionalHorizontalBlock forFraction(RationalType frac) {
            if (frac.sign() == Sign.NEGATIVE) throw new IllegalArgumentException("Negative fractional values prohibited");
            boolean takeFraction = frac.numerator().magnitude().compareTo(frac.denominator()) > 0; // frac > 1
            RationalType onlyFrac = takeFraction ? (RationalType) frac.subtract(MathUtils.trunc(frac)) : frac;
            for (FractionalHorizontalBlock block : values()) {
                if (onlyFrac.compareTo(block.value) <= 0) return block;
            }
            return FULL;
        }

        public static FractionalHorizontalBlock forFraction(RealType frac) {
            if (frac.sign() == Sign.NEGATIVE) throw new IllegalArgumentException("Negative fractional values prohibited");
            if (Zero.isZero(frac)) return EMPTY;
            if (One.isUnity(frac)) return FULL;
            RealType onlyFrac = (RealType) frac.subtract(MathUtils.trunc(frac));
            for (FractionalHorizontalBlock block : values()) {
                if (onlyFrac.asBigDecimal().compareTo(block.value.asBigDecimal()) <= 0) return block;
            }
            return FULL;
        }
    }

    public enum FractionalVerticalBlock {
        EMPTY(' ', new RationalImpl(BigInteger.ZERO, BigInteger.ONE)),
        ONE_EIGHTH('\u2581', new RationalImpl("1/8")),
        ONE_FOURTH('\u2582', new RationalImpl("1/4")),
        THREE_EIGHTHS('\u2583', new RationalImpl("3/8")),
        ONE_HALF('\u2584', new RationalImpl("1/2")),
        FIVE_EIGHTHS('\u2585', new RationalImpl("5/8")),
        THREE_FOURTHS('\u2586', new RationalImpl("3/4")),
        SEVEN_EIGHTHS('\u2587', new RationalImpl("7/8")),
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
            final RealType oneHundred = new RealImpl(BigDecimal.valueOf(100L));
            try {
                return (RealType) oneHundred.multiply(value).coerceTo(RealType.class);
            } catch (CoercionException e) {
                throw new IllegalStateException(e);
            }
        }

        public static FractionalVerticalBlock forFraction(RationalType frac) {
            if (frac.sign() == Sign.NEGATIVE) throw new IllegalArgumentException("Negative fractional values prohibited");
            boolean takeFraction = frac.numerator().magnitude().compareTo(frac.denominator()) > 0; // frac > 1
            RationalType onlyFrac = takeFraction ? (RationalType) frac.subtract(MathUtils.trunc(frac)) : frac;
            for (FractionalVerticalBlock block : values()) {
                if (onlyFrac.compareTo(block.value) <= 0) return block;
            }
            return FULL;
        }

        public static FractionalVerticalBlock forFraction(RealType frac) {
            if (frac.sign() == Sign.NEGATIVE) throw new IllegalArgumentException("Negative fractional values prohibited");
            if (Zero.isZero(frac)) return EMPTY;
            if (One.isUnity(frac)) return FULL;
            RealType onlyFrac = (RealType) frac.subtract(MathUtils.trunc(frac));
            for (FractionalVerticalBlock block : values()) {
                if (onlyFrac.asBigDecimal().compareTo(block.value.asBigDecimal()) <= 0) return block;
            }
            return FULL;
        }
    }

    /**
     * These represent Unicode graphic blocks that &ldquo;grow&rdquo; down from above.
     * Note that there are fewer defined choices here than for, e.g., {@link FractionalVerticalBlock}.
     * This will limit the accuracy in plotting a histogram with negative values.
     */
    public enum FractionalVerticalInverseBlock {
        EMPTY(' ', new RationalImpl(BigInteger.ZERO, BigInteger.ONE)),
        ONE_EIGHTH('\u2594', new RationalImpl("1/8")),
        ONE_HALF('\u2580', new RationalImpl("1/2")),
        SEVEN_EIGHTHS('\u2593', new RationalImpl("7/8")), // we're close to whole, so fake it with a dark shaded block
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
            final RealType oneHundred = new RealImpl(BigDecimal.valueOf(100L));
            try {
                return (RealType) oneHundred.multiply(value).coerceTo(RealType.class);
            } catch (CoercionException e) {
                throw new IllegalStateException(e);
            }
        }

        public static FractionalVerticalInverseBlock forFraction(RationalType frac) {
            boolean takeFraction = frac.numerator().magnitude().compareTo(frac.denominator()) > 0; // frac > 1
            RationalType onlyFrac = takeFraction ? (RationalType) frac.magnitude().subtract(MathUtils.trunc(frac.magnitude())) : frac.magnitude();
            for (FractionalVerticalInverseBlock block : values()) {
                if (onlyFrac.compareTo(block.value) <= 0) return block;
            }
            return FULL;
        }

        public static FractionalVerticalInverseBlock forFraction(RealType frac) {
            if (Zero.isZero(frac)) return EMPTY;
            if (One.isUnity(frac)) return FULL;
            RealType onlyFrac = (RealType) frac.magnitude().subtract(MathUtils.trunc(frac.magnitude()));
            for (FractionalVerticalInverseBlock block : values()) {
                if (onlyFrac.asBigDecimal().compareTo(block.value.asBigDecimal()) <= 0) return block;
            }
            return FULL;
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
        final RealType blockSize = (RealType) maxVal.divide(new IntegerImpl(BigInteger.valueOf(blockHeight)));

        int width = values.size();
        if (spaceBetween) width += values.size() - 1;
        for (int row = 0; row < blockHeight; row++) {
            StringBuilder buf = new StringBuilder(width);
            RealType rowTop = (RealType) blockSize.multiply(new IntegerImpl(BigInteger.valueOf(row + 1L)));
            RealType rowBottom = (RealType) blockSize.multiply(new IntegerImpl(BigInteger.valueOf(row)));
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
                if (spaceBetween && buf.length() < width) buf.append(row % hruleInterval == 0 ? HorizontalFill.LIGHT_TRIPLE_DASH : HorizontalFill.EMPTY);
            }

            // finally, append completed row to our collection
            rows.add(buf.toString());
        }

        Collections.reverse(rows);
        return rows;
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
        final RealType blockSize = (RealType) maxVal.divide(new IntegerImpl(BigInteger.valueOf(blockWidth)));

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
            RealType barWidth = (RealType) values.get(k).divide(blockSize);
            int solidFill = barWidth.floor().asBigInteger().intValue();
            RealType frac = (RealType) barWidth.subtract(barWidth.floor());
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

        return rows;
    }
}
