/*
 * The MIT License
 *
 * Copyright © 2026 Robert Poole <Tarquin.AZ@gmail.com>.
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

// ... existing code ...
package tungsten.types.functions.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tungsten.types.functions.UnaryArgVector;
import tungsten.types.numerics.IntegerType;
import tungsten.types.numerics.RealType;
import tungsten.types.numerics.impl.IntegerImpl;
import tungsten.types.numerics.impl.Pi;
import tungsten.types.numerics.impl.RealImpl;
import tungsten.types.functions.ArgVector;
import tungsten.types.functions.UnaryFunction;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;

import static org.junit.jupiter.api.Assertions.*;
import static tungsten.types.util.MathUtils.areEqualToWithin;
//import static tungsten.types.numerics.impl.AngularDegrees.toRadians;
import tungsten.types.util.AngularDegrees;

class SinTest {
    private MathContext mctx;
    private RealType epsilon;
    private Sin sinFunction;
    private final BigDecimal two = BigDecimal.valueOf(2);
    private final BigDecimal three = BigDecimal.valueOf(3);

    @BeforeEach
    void setUp() {
        mctx = MathContext.DECIMAL128;
        epsilon = new RealImpl(BigDecimal.valueOf(1e-6), mctx);
        sinFunction = new Sin("theta", mctx);
    }

    @Test
    void testApplyAtZero() {
        ArgVector<RealType> args = new UnaryArgVector<>("theta", 
                new RealImpl(BigDecimal.ZERO, mctx));
        RealType result = sinFunction.apply(args);

        RealType expected = new RealImpl(BigDecimal.ZERO, mctx);
        assertTrue(areEqualToWithin(expected, result, epsilon),
                "sin(0) should equal 0");
    }

    @Test
    void testApplyAtPiDividedBy2() {
        Pi pi = Pi.getInstance(mctx);
        ArgVector<RealType> args = new UnaryArgVector<>("theta",
                (RealType) pi.divide(new RealImpl(two, mctx)));
        RealType result = sinFunction.apply(args);

        RealType expected = new RealImpl(BigDecimal.ONE, mctx);
        assertTrue(areEqualToWithin(expected, result, epsilon),
                "sin(π/2) should equal 1");
    }

    @Test
    void testApplyAtPiDividedBy6() {
        Pi pi = Pi.getInstance(mctx);
        ArgVector<RealType> args = new UnaryArgVector<>("theta",
                (RealType) pi.divide(new RealImpl(BigDecimal.valueOf(6), mctx)));
        RealType result = sinFunction.apply(args);

        // Expected value is 0.5
        RealType expected = new RealImpl(BigDecimal.valueOf(0.5), mctx);
        assertTrue(areEqualToWithin(expected, result, epsilon),
                "sin(π/6) should equal 0.5");
    }

    @Test
    void testApplyAtPiDividedBy4() {
        Pi pi = Pi.getInstance(mctx);
        ArgVector<RealType> args = new UnaryArgVector<>("theta",
                (RealType) pi.divide(new RealImpl(BigDecimal.valueOf(4), mctx)));
        RealType result = sinFunction.apply(args);

        // Expected value is √2/2 ≈ 0.7071...
        RealType expected = new RealImpl(two.sqrt(mctx).divide(two, mctx), mctx);
        assertTrue(areEqualToWithin(expected, result, epsilon),
                "sin(π/4) should equal √2/2");
    }

    @Test
    void testApplyAtPiDividedBy3() {
        Pi pi = Pi.getInstance(mctx);
        ArgVector<RealType> args = new UnaryArgVector<>("theta",
                (RealType) pi.divide(new RealImpl(three, mctx)));
        RealType result = sinFunction.apply(args);

        // Expected value is √3/2 ≈ 0.8660...
        RealType expected = new RealImpl(three.sqrt(mctx).divide(two, mctx), mctx);
        assertTrue(areEqualToWithin(expected, result, epsilon),
                "sin(π/3) should equal √3/2");
    }

    @Test
    void testApplyAtPi() {
        Pi pi = Pi.getInstance(mctx);
        ArgVector<RealType> args = new UnaryArgVector<>("theta", pi);
        RealType result = sinFunction.apply(args);

        RealType expected = new RealImpl(BigDecimal.ZERO, mctx);
        assertTrue(areEqualToWithin(expected, result, epsilon),
                "sin(π) should equal 0");
    }

    @Test
    void testApplyAt3PiDividedBy2() {
        Pi pi = Pi.getInstance(mctx);
        ArgVector<RealType> args = new UnaryArgVector<>("theta",
                (RealType) pi.multiply(new RealImpl(three, mctx))
                        .divide(new RealImpl(two, mctx)));
        RealType result = sinFunction.apply(args);

        RealType expected = new RealImpl(BigDecimal.ONE.negate(), mctx);
        assertTrue(areEqualToWithin(expected, result, epsilon),
                "sin(3π/2) should equal -1");
    }

    @Test
    void testApplyAt2Pi() {
        Pi pi = Pi.getInstance(mctx);
        ArgVector<RealType> args = new UnaryArgVector<>("theta",
                (RealType) pi.multiply(new RealImpl(two, mctx)));
        RealType result = sinFunction.apply(args);

        RealType expected = new RealImpl(BigDecimal.ZERO, mctx);
        assertTrue(areEqualToWithin(expected, result, epsilon),
                "sin(2π) should equal 0");
    }

    @Test
    void testApplyWithAngularDegrees() {
        // Convert 30 degrees to radians (π/6)
        IntegerType zero = new IntegerImpl(BigInteger.ZERO);
        RealType rezero = new RealImpl(zero, mctx);
        AngularDegrees degrees = new AngularDegrees(new IntegerImpl("30"), zero, rezero);
        ArgVector<RealType> args = new UnaryArgVector<>("theta",
                degrees.asRadians());
        RealType result = sinFunction.apply(args);

        // Expected value is 0.5
        RealType expected = new RealImpl(BigDecimal.valueOf(0.5), mctx);
        assertTrue(areEqualToWithin(expected, result, epsilon),
                "sin(30°) should equal 0.5");
    }

    @Test
    void testDerivativeAtZero() {
        UnaryFunction<RealType, RealType> derivative = sinFunction.diff();

        ArgVector<RealType> args = new UnaryArgVector<>("theta",
                new RealImpl(BigDecimal.ZERO, mctx));
        RealType result = derivative.apply(args);

        // The derivative of sin(x) is cos(x), and cos(0) = 1
        RealType expected = new RealImpl(BigDecimal.ONE, mctx);
        assertTrue(areEqualToWithin(expected, result, epsilon),
                "sin'(0) should equal 1");
    }

    @Test
    void testDerivativeAtPiDividedBy2() {
        UnaryFunction<RealType, RealType> derivative = sinFunction.diff();

        Pi pi = Pi.getInstance(mctx);
        ArgVector<RealType> args = new UnaryArgVector<>("theta",
                (RealType) pi.divide(new RealImpl(two, mctx)));
        RealType result = derivative.apply(args);

        // The derivative of sin(x) is cos(x), and cos(π/2) = 0
        RealType expected = new RealImpl(BigDecimal.ZERO, mctx);
        assertTrue(areEqualToWithin(expected, result, epsilon),
                "sin'(π/2) should equal 0");
    }

    @Test
    void testNumericDerivativeWithSimpleDerivative() {
        SimpleDerivative<RealType> simpleDerivative = new SimpleDerivative<>(epsilon);

        UnaryFunction<RealType, RealType> derivative = simpleDerivative.apply(sinFunction);

        Pi pi = Pi.getInstance(mctx);
        
        // Test at 0
        ArgVector<RealType> args1 = new UnaryArgVector<>("theta",
                new RealImpl(BigDecimal.ZERO, mctx));
        RealType result1 = derivative.apply(args1);
        RealType expected1 = new RealImpl(BigDecimal.ONE, mctx);
        assertTrue(areEqualToWithin(expected1, result1, epsilon),
                "Numeric sin'(0) should be close to 1");

        // Test at π/2
        ArgVector<RealType> args2 = new UnaryArgVector<>("theta",
                (RealType) pi.divide(new RealImpl(two, mctx)));
        RealType result2 = derivative.apply(args2);
        RealType expected2 = new RealImpl(BigDecimal.ZERO, mctx);
        assertTrue(areEqualToWithin(expected2, result2, epsilon),
                "Numeric sin'(π/2) should be close to 0");
    }

    @Test
    void testPeriodicity() {
        Pi pi = Pi.getInstance(mctx);
        RealType period = sinFunction.period();

        // Verify period is 2π
        RealType expectedPeriod = (RealType) pi.multiply(new RealImpl(two, mctx));
        assertTrue(areEqualToWithin(expectedPeriod, period, epsilon),
                "Period of sin should be 2π");

        // Test periodicity: sin(x) == sin(x + period)
        RealImpl divisor = new RealImpl(BigDecimal.valueOf(4), mctx);
        ArgVector<RealType> args1 = new UnaryArgVector<>("theta",
                (RealType) pi.divide(divisor));
        RealType result1 = sinFunction.apply(args1);

        ArgVector<RealType> args2 = new UnaryArgVector<>("theta",
                (RealType) pi.divide(divisor).add(period));
        RealType result2 = sinFunction.apply(args2);

        assertTrue(areEqualToWithin(result1, result2, epsilon),
                "sin(x) should equal sin(x + period)");
    }
}
// ... existing code ...
