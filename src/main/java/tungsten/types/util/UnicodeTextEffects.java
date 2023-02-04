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

import tungsten.types.Numeric;
import tungsten.types.exceptions.CoercionException;
import tungsten.types.numerics.ComplexType;
import tungsten.types.numerics.IntegerType;
import tungsten.types.numerics.RationalType;
import tungsten.types.numerics.Sign;
import tungsten.types.numerics.impl.RationalImpl;
import tungsten.types.numerics.impl.Zero;

import java.math.BigInteger;
import java.text.Normalizer;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

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
        superscriptMap.put('+', "\u207A");
        superscriptMap.put('(', "\u207D");
        superscriptMap.put(')', "\u207E");
        superscriptMap.put('i', "\u2071");
        superscriptMap.put('n', "\u207F");
        superscriptMap.put('=', "\u207C");
        
        subscriptMap.put('-', "\u208B");
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
                int digit = c - '0';
                buf.append(superscriptDigits[digit]);
            } else if (superscriptMap.containsKey(c)) {
                buf.append(superscriptMap.get(c));
            }
        }
        return buf.toString();
    }

    public static String convertToSubscript(String source) {
        StringBuilder buf = new StringBuilder();
        
        for (Character c : source.toCharArray()) {
            if (Character.isDigit(c)) {
                int digit = c - '0';
                buf.append(subscriptDigits[digit]);
            } else if (subscriptMap.containsKey(c)) {
                buf.append(subscriptMap.get(c));
            }
        }
        return buf.toString();
    }
    
    private static final char COMBINING_OVERLINE = '\u0305';
    private static final char NEGATIVE_SIGN = '\u2212';

    public static String overline(String source) {
        StringBuilder buf = new StringBuilder();
        
        for (Character c : source.toCharArray()) {
            buf.append(c).append(COMBINING_OVERLINE);
        }
        return buf.toString();
    }
    
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
            buf.append(radicand);
        }
        return buf.toString();
    }

    public static String stripCombiningCharacters(final String source) {
        String expanded = Normalizer.isNormalized(source, Normalizer.Form.NFKD) ? source :
                Normalizer.normalize(source, Normalizer.Form.NFKD);
        return expanded.replaceAll("\\p{M}", "");
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
            throw new IllegalStateException("While converting the fraction " + frac + " for display.", e);
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
}
