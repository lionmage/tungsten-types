/*
 * The MIT License
 *
 * Copyright © 2025 Robert Poole <Tarquin.AZ@gmail.com>.
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

package tungsten.types.functions.indexed;

import org.junit.jupiter.api.Test;
import tungsten.types.functions.indexed.examples.HarmonicSeries;
import tungsten.types.functions.indexed.examples.PiSeries;
import tungsten.types.numerics.IntegerType;
import tungsten.types.numerics.RationalType;
import tungsten.types.numerics.RealType;
import tungsten.types.numerics.impl.EulerMascheroni;
import tungsten.types.numerics.impl.IntegerImpl;
import tungsten.types.numerics.impl.Pi;
import tungsten.types.numerics.impl.RealImpl;
import tungsten.types.util.MathUtils;

import java.math.MathContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static tungsten.types.util.MathUtils.areEqualToWithin;

/**
 * Tests for indexed functions.
 */
public class TestOperations {
    @Test
    public void testHarmonicSeries() {
        IntegerType N = new IntegerImpl("10000", true);

        HarmonicSeries series = new HarmonicSeries(MathContext.DECIMAL128);
        RationalType result1 = series.evaluate(N);
        EulerMascheroni gamma = EulerMascheroni.getInstance(MathContext.DECIMAL128);

        RealType result2 = MathUtils.ln(N, MathContext.DECIMAL128);
        assertEquals(result2, result1.subtract(gamma));
    }

    @Test
    public void testPiSeries() {
        IntegerType N = new IntegerImpl("5000", true);

        PiSeries series = new PiSeries(MathContext.DECIMAL128);
        RealType result1 = series.evaluate(N);
        RealType epsilon = new RealImpl("0.0000001", MathContext.DECIMAL128);

        assertTrue(areEqualToWithin(Pi.getInstance(MathContext.DECIMAL128), result1, epsilon));
    }
}
