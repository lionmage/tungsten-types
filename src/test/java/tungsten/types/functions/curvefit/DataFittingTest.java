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

package tungsten.types.functions.curvefit;

import org.junit.jupiter.api.Test;
import tungsten.types.functions.ArgMap;
import tungsten.types.functions.ArgVector;
import tungsten.types.functions.NumericFunction;
import tungsten.types.functions.impl.Polynomial;
import tungsten.types.functions.support.Coordinates;
import tungsten.types.numerics.RealType;
import tungsten.types.numerics.impl.RealImpl;
import tungsten.types.util.MathUtils;
import tungsten.types.util.ingest.coordinates.DataParser;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class DataFittingTest {
    private final List<Coordinates> anscombe1, anscombe2, anscombe3, anscombe4;

    public DataFittingTest() {
        DataParser parser = new DataParser(MathContext.DECIMAL32, CurveType.CURVE_2D);
        // strike up the quartet
        anscombe1 = parser.read(getClass().getClassLoader().getResourceAsStream("anscombe1.data"));
        anscombe2 = parser.read(getClass().getClassLoader().getResourceAsStream("anscombe2.data"));
        anscombe3 = parser.read(getClass().getClassLoader().getResource("anscombe3.data"));
        anscombe4 = parser.read(getClass().getClassLoader().getResource("anscombe4.data"));
    }

    private final RealType THREE = new RealImpl(BigDecimal.valueOf(3L), MathContext.DECIMAL32);
    private final RealType EPSILON = new RealImpl("0.001", MathContext.DECIMAL32);

    @Test
    public void basicLinearFit() {
        CurveFitter fitter = new CurveFitter(anscombe1);
        assertEquals(CurveType.CURVE_2D, fitter.characteristic);
        fitter.sortInX();
        NumericFunction<RealType, RealType> curve = fitter.fitToData("linear*");
        assertInstanceOf(Polynomial.class, curve);

        Polynomial<RealType, RealType> polycurve = (Polynomial<RealType, RealType>) curve;
        ArgMap<RealType> zxMap = new ArgMap<>("[x:0.00]", RealType.class);
        ArgVector<RealType> zerox = new ArgVector<>(new String[] {"x"}, zxMap);
        RealType intercept = polycurve.apply(zerox);
        System.out.println("y intercept for " + polycurve + " = " + intercept);
        assertTrue(MathUtils.areEqualToWithin(THREE, intercept, EPSILON));

        RealType negSix = new RealImpl("-6.00", MathContext.DECIMAL32);
        zxMap.put("x", negSix);
        ArgVector<RealType> newargs = new ArgVector<>(new String[] {"x"}, zxMap);
        RealType result = polycurve.apply(newargs);
        RealType expected = new RealImpl(BigDecimal.ZERO, MathContext.DECIMAL32);
        System.out.println("For x = " + negSix + ", " + polycurve + " = " + result);
        assertTrue(MathUtils.areEqualToWithin(expected, result, EPSILON));
    }

    @Test
    public void quadraticFit() {
        CurveFitter fitter = new CurveFitter(anscombe2);
        assertEquals(CurveType.CURVE_2D, fitter.characteristic);
        fitter.sortInX();
        NumericFunction<RealType, RealType> curve = fitter.fitToData("parabolic*");
        assertInstanceOf(Polynomial.class, curve);

        Polynomial<RealType, RealType> polycurve = (Polynomial<RealType, RealType>) curve;
        assertEquals(3L, polycurve.countTerms());
        assertEquals(2L, polycurve.order("x"));
        System.out.println("Parabolic fit for Anscombe 2 is " + polycurve);
    }
}
