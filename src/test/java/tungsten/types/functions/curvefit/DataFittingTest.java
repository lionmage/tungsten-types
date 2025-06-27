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
import tungsten.types.functions.Term;
import tungsten.types.functions.impl.Polynomial;
import tungsten.types.functions.support.Coordinates;
import tungsten.types.numerics.RealType;
import tungsten.types.numerics.impl.RealImpl;
import tungsten.types.util.MathUtils;
import tungsten.types.util.ingest.coordinates.DataParser;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

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
        RealType a = null, b = null;  // coefficients
        for (Term<RealType, RealType> term : polycurve) {
            if (term.order("x") == 2L) a = term.coefficient();
            if (term.order("x") == 1L) b = term.coefficient();
        }
        assertNotNull(a, "Could not extract a");
        assertNotNull(b, "Could not extract b");
        System.out.println("Extracted a = " + a + ", b = " + b);
        RealType two = new RealImpl("2.00", MathContext.DECIMAL32);
        RealType vertexX = (RealType) b.negate().divide(two.multiply(a));
        ArgMap<RealType> map = new ArgMap<>(Collections.singletonMap("x", vertexX));
        ArgVector<RealType> arg = new ArgVector<>(new String[] {"x"}, map);
        RealType vertexY = polycurve.apply(arg);
        System.out.println("Vertex should be at (" + vertexX + ", " + vertexY + ")");
        // There's a data point at 11.0, 9.26 which is very close to the vertex
        RealType minimum = new RealImpl("9.26", MathContext.DECIMAL32);
        assertTrue(vertexY.compareTo(minimum) > 0);
    }

    @Test
    public void removingOutliers() {
        CurveFitter fitter = new CurveFitter(anscombe3);
        assertEquals(CurveType.CURVE_2D, fitter.characteristic);
        fitter.sortInX();
        NumericFunction<RealType, RealType> curve = fitter.fitToData("linear*");
        assertInstanceOf(Polynomial.class, curve);

        Polynomial<RealType, RealType> withOutlier = (Polynomial<RealType, RealType>) curve;

        final Coordinates outlier = anscombe3.stream().max(Comparator.comparing(Coordinates::getValue)).orElseThrow();
        System.out.println("Outlier detected at " + outlier);
        List<Coordinates> cleaned = anscombe3.stream().filter(datum -> !datum.equals(outlier)).collect(Collectors.toList());
        assertEquals(anscombe3.size() - 1, cleaned.size());

        CurveFitter fitter2 = new CurveFitter(cleaned);
        assertEquals(CurveType.CURVE_2D, fitter2.characteristic);
        fitter2.sortInX();
        NumericFunction<RealType, RealType> curve2 = fitter2.fitToData("linear*");
        assertInstanceOf(Polynomial.class, curve2);

        Polynomial<RealType, RealType> noOutlier = (Polynomial<RealType, RealType>) curve2;

        RealType slope1 = null, slope2 = null;
        for (Term<RealType, RealType> term : withOutlier) {
            if (term.order("x") == 1L) slope1 = term.coefficient();
        }
        assertNotNull(slope1);
        for (Term<RealType, RealType> term : noOutlier) {
            if (term.order("x") == 1L) slope2 = term.coefficient();
        }
        assertNotNull(slope2);
        System.out.println("Slope of original data set: " + slope1);
        System.out.println("Slope of linear fit for cleaned data set: " + slope2);
        assertTrue(slope1.compareTo(slope2) > 0, "Corrected slope should be less than original slope");
    }
}
