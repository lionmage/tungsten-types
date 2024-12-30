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
}
