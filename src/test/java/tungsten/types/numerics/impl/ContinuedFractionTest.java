/*
 * The MIT License
 *
 * Copyright © 2024 Robert Poole <Tarquin.AZ@gmail.com>.
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
 *
 */

package tungsten.types.numerics.impl;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import tungsten.types.Numeric;
import tungsten.types.numerics.RealType;
import tungsten.types.util.ANSITextEffects;
import tungsten.types.util.MathUtils;

import java.math.BigDecimal;
import java.math.MathContext;

public class ContinuedFractionTest {
    @Test
    public void testPow() {
        ContinuedFraction cf = new ContinuedFraction(2L, 2L);
        System.out.println("Generated cf: " + cf.asBigDecimal().toPlainString());
        ContinuedFraction toFive = cf.pow(5L);
        toFive.setMathContext(MathContext.DECIMAL64);
        System.out.println("cf^5 = " + toFive.asBigDecimal().toPlainString());
        RealType decVersion = new RealImpl("97.65625", MathContext.DECIMAL64);
        assertEquals(0, decVersion.compareTo(toFive));
    }

    @Test
    public void testPiApprox() {
        ContinuedFraction picf = ContinuedFraction.pi(MathContext.DECIMAL128);
        System.out.println("\uD835\uDF0B as a CF: " + picf);
        String cfstr = picf.asBigDecimal().toPlainString();
        System.out.println("In decimal notation: " + cfstr);
        Pi pi = Pi.getInstance(MathContext.DECIMAL128);
        String pistr = pi.asBigDecimal().toPlainString();
        System.out.println("From the BBP algorithm: " + pistr);
        // let's show the diff
        int diffpos = ANSITextEffects.findFirstDifference(pistr, cfstr);
        assertTrue(diffpos > 10, "There should be at least 9 significant digits in common");
//        System.out.println( ANSITextEffects.highlightSection(pistr, 0, diffpos, ANSITextEffects.Effect.BOLD));
        System.out.println("Showing digits in common: " +
                ANSITextEffects.highlightSelection(pistr, 0, diffpos, ANSITextEffects.Effect.BOLD, ANSITextEffects.Effect.BG_CYAN));
    }

    @Test
    public void testSqrt() {
        ContinuedFraction cf22 = new ContinuedFraction(22L);
        Numeric root22 = cf22.sqrt();
        assertInstanceOf(ContinuedFraction.class, root22);
        ContinuedFraction cfroot22 = (ContinuedFraction) root22;
        assertEquals(4, cfroot22.termAt(0L));
        assertEquals(8L, cfroot22.termAt(6L));
        assertEquals(2L, cfroot22.termAt(2L));

        ContinuedFraction cf82 = new ContinuedFraction(82L);
        Numeric root82 = cf82.sqrt();
        assertInstanceOf(ContinuedFraction.class, root82);
        ContinuedFraction cfroot82 = (ContinuedFraction) root82;
        assertEquals(9L, cfroot82.termAt(0L));
        assertEquals(18L, cfroot82.termAt(1L));

        RealType val = new RealImpl("9.7", MathContext.DECIMAL128);
        ContinuedFraction cfVal = new ContinuedFraction(val);
        cfVal.setMathContext(MathContext.DECIMAL128);
        System.out.println("For 9.7, CF is " + cfVal);

        Numeric root = cfVal.sqrt();
        assertInstanceOf(ContinuedFraction.class, root);
        Numeric decRoot = val.sqrt();
        String target = ((RealType) decRoot).asBigDecimal().toPlainString();
        String generated = ((RealType) root).asBigDecimal().toPlainString();
        int diffPos = ANSITextEffects.findFirstDifference(target, generated);
        assertTrue(diffPos > 8);
        System.out.println("With highlighted digits: " +
                ANSITextEffects.highlightSelection(generated, 0, diffPos, ANSITextEffects.Effect.BOLD, ANSITextEffects.Effect.BG_CYAN));
    }

    @Test
    public void parsing() {
        final String source = " [ 9 ; 3,2,  4 ]  ";  // screwy formatting
        ContinuedFraction fromString = new ContinuedFraction(source, MathContext.DECIMAL128);
        assertEquals(9L, fromString.termAt(0L));
        assertEquals(4L, fromString.terms());
        System.out.println("From parsed string: " + fromString);
        assertEquals("[9; 3, 2, 4]", fromString.toString());

        final String source2 = " [3; ^1, 6]";  // √15
        ContinuedFraction sqrt15 = new ContinuedFraction(source2, MathContext.DECIMAL64);
        System.out.println("\u221A15 = " + sqrt15);
        assertEquals(-1L, sqrt15.terms());
        assertEquals(3L, sqrt15.termAt(0L));
        assertEquals(6L, sqrt15.termAt(8L));  // indexing into the repeating section
        assertEquals(1L, sqrt15.termAt(9L));

        BigDecimal decimalRoot = sqrt15.asBigDecimal();
        System.out.println("As BigDecimal: " + decimalRoot.toPlainString());
        // the name of the following variable is aspirational
        BigDecimal fifteen = decimalRoot.pow(2, MathContext.DECIMAL64);
        System.out.println("(\u221A15)\u00B2=" + fifteen.toPlainString());
        BigDecimal error = BigDecimal.valueOf(15L).subtract(fifteen, MathContext.DECIMAL64).abs();
        System.out.println("Absolute error: " + error);
        final BigDecimal epsilon = BigDecimal.TEN.pow(-12, MathContext.DECIMAL64);
        assertTrue(epsilon.compareTo(error) > 0);
        // the following section is currently broken, because the square roots
        // of integers are a worst-case scenario for multiplying continued fractions
//        ContinuedFraction square = sqrt15.pow(2L);
//        square.setMathContext(MathContext.DECIMAL64);
//        System.out.println("Square is " + square);
//        assertEquals(15L, square.termAt(0L));
        // the multiply method should fall back to RealImpl if the result of multiplication is erroneous
        Numeric square = sqrt15.multiply(sqrt15);
        System.out.println("Square is " + square);
        assertInstanceOf(RealType.class, square, "sqrt15*sqrt15 should be a real");
        RealType diff = (RealType) new RealImpl("15.0", MathContext.DECIMAL64).subtract(square);
        System.out.println("Difference from actual value is " + diff);
        assertTrue(diff.asBigDecimal().compareTo(epsilon) < 0);
    }

    @Test
    public void irrationalRoots() {
        Numeric root = new ContinuedFraction(462L).sqrt();
        assertInstanceOf(ContinuedFraction.class, root);
        ContinuedFraction cfroot = (ContinuedFraction) root;
        assertEquals(21L, cfroot.termAt(0L));
        assertEquals(cfroot.termAt(1L), cfroot.termAt(3L));
        assertEquals(cfroot.termAt(2L), cfroot.termAt(6L));

        // this appears to be a torture test for Gosper's algorithm
        ContinuedFraction fman = ContinuedFraction.freiman(MathContext.DECIMAL128);
        assertEquals(16L, fman.termAt(14L));
        assertEquals(7L, fman.termAt(13L));
        assertEquals(117L, fman.termAt(10L));
        System.out.println("Freiman's constant computed as: " + fman);
        // this constant has a very long period
        final long repeatStart = 66759L;
        assertEquals(fman.termAt(5L), fman.termAt(repeatStart));
        assertEquals(fman.termAt(18L), fman.termAt(repeatStart + 13L));
    }

    @Test
    public void nthRoot() {
        ContinuedFraction f = new ContinuedFraction(34L);
        f.setMathContext(MathContext.DECIMAL128);
        var root = f.nthRoot(5L);
        System.out.println("5th root of 34: " + root);
        String decString = root.asBigDecimal().toPlainString();
        System.out.println("In decimal notation: " + decString);
        // value obtained from https://en.wikipedia.org/wiki/Nth_root#Using_Newton's_method
        assertTrue(decString.startsWith("2.02439745849988504251081724554"));
        final String target = "2.02439 74584 99885 04251 08172 45541 93741 91146 21701 07311";
        var reduced = target.replaceAll("\\s", "");
        int diffpos = ANSITextEffects.findFirstDifference(reduced, decString);
        System.out.println("With highlighted digits: " +
                ANSITextEffects.highlightSelection(decString, 0, diffpos, ANSITextEffects.Effect.BOLD, ANSITextEffects.Effect.BG_YELLOW));
        System.out.println("vs. expected value: " +
                ANSITextEffects.highlightSelection(reduced, 0, diffpos, ANSITextEffects.Effect.ITALIC, ANSITextEffects.Effect.BLUE, ANSITextEffects.Effect.BG_YELLOW));
    }

    @Test
    public void logarithms() {
        ContinuedFraction cf1 = new ContinuedFraction(5L, 5L);  // 5.2
        cf1.setMathContext(MathContext.DECIMAL128);

        final ContinuedFraction two = new ContinuedFraction(2L);
        two.setMathContext(MathContext.DECIMAL128);

        ContinuedFraction lg = MathUtils.log(cf1, two);
        System.out.println("CF for log\u2082 of " + cf1 + " is " + lg);
        lg.setMathContext(MathContext.DECIMAL128);
        final String lgExpected = "2.37851162325373";  // to 15 decimal places
        String lgComputed = lg.asBigDecimal().toPlainString();
        int lgDiff = ANSITextEffects.findFirstDifference(lgExpected, lgComputed);
        System.out.println("lg(5.2): " +
                ANSITextEffects.highlightSelection(lgComputed, 0, lgDiff, ANSITextEffects.Effect.BOLD, ANSITextEffects.Effect.BG_GREEN));
        assertTrue(lgDiff > 12, "Expected 12 significant digits, but got " + (lgDiff - 1)); // ignoring decimal point

        ContinuedFraction ln = MathUtils.log(cf1, ContinuedFraction.euler(MathContext.DECIMAL128));
        ln.setMathContext(MathContext.DECIMAL128);
        final String lnExpected = "1.648658625587382";  // to 15 decimal places
        String lnComputed = ln.asBigDecimal().toPlainString();
        int lnDiff = ANSITextEffects.findFirstDifference(lnExpected, lnComputed);
        assertTrue(lnDiff > 14);
        System.out.println("ln(5.2): " +
                ANSITextEffects.highlightSelection(lnComputed, 0, lnDiff, ANSITextEffects.Effect.ITALIC, ANSITextEffects.Effect.BG_YELLOW));

        ContinuedFraction thousandth = new ContinuedFraction(0L, 1000L);  // 0.001
        thousandth.setMathContext(MathContext.DECIMAL128);
        final String thouExpected = "-6.907755278982137";
        ContinuedFraction thouLN = MathUtils.log(thousandth, ContinuedFraction.euler(MathContext.DECIMAL128));
        thouLN.setMathContext(MathContext.DECIMAL128);
        assertEquals(-7L, thouLN.termAt(0L));
        String thouComputed = thouLN.asBigDecimal().toPlainString();
        int thouDiff = ANSITextEffects.findFirstDifference(thouExpected, thouComputed);
        assertTrue(thouDiff > 14);
        System.out.println("ln(0.001): " +
                ANSITextEffects.highlightSelection(thouComputed, 0, thouDiff, ANSITextEffects.Effect.BOLD, ANSITextEffects.Effect.BG_CYAN));

        ContinuedFraction half = new ContinuedFraction(0L, 2L);
        half.setMathContext(MathContext.DECIMAL128);
        final String lnhalfExpected = "-0.693147180559945";
        ContinuedFraction halfLN = MathUtils.log(half, ContinuedFraction.euler(MathContext.DECIMAL128));
        halfLN.setMathContext(MathContext.DECIMAL128);
        assertEquals(-1L, halfLN.termAt(0L));
        String halfComputed = halfLN.asBigDecimal().toPlainString();
        int halfDiff = ANSITextEffects.findFirstDifference(lnhalfExpected, halfComputed);
        System.out.println("ln(0.5): " +
                ANSITextEffects.highlightSelection(halfComputed, 0, halfDiff, ANSITextEffects.Effect.BOLD, ANSITextEffects.Effect.RED));
        System.out.println("CF for ln of " + half + " is " + halfLN);
        assertTrue(halfDiff > 14, "Expected 14 significant digits, but got " + (halfDiff - 1));
    }

    @Test
    public void magicTricksWithEuler() {
        long[] sqExpected = {7L, 2L, 1L, 1L, 3L, 18L, 5L, 1L, 1L, 6L, 30L, 8L, 1L, 1L, 9L, 42L, 11L, 1L, 1L, 12L, 54L};
        ContinuedFraction e = ContinuedFraction.euler(MathContext.DECIMAL128);
        assertEquals("[2; …]", e.toString());
        System.out.println("\u212F (dec) = " + e.asBigDecimal().toPlainString());
        ContinuedFraction sq = e.pow(2L);
        sq.setMathContext(MathContext.DECIMAL128);
        System.out.println("\u212F\u00B2 = " + sq);
        System.out.println("in decimal format: " + sq.asBigDecimal().toPlainString());
        for (int idx = 0; idx < sqExpected.length; idx++) {
            assertEquals(sqExpected[idx], sq.termAt(idx));
        }
    }
}
