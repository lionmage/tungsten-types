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

package tungsten.types.functions.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tungsten.types.annotations.Differentiable;
import tungsten.types.functions.UnaryArgVector;
import tungsten.types.numerics.IntegerType;
import tungsten.types.numerics.RealType;
import tungsten.types.numerics.impl.*;
import tungsten.types.functions.ArgVector;
import tungsten.types.functions.UnaryFunction;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;

import static org.junit.jupiter.api.Assertions.*;
import static tungsten.types.util.MathUtils.areEqualToWithin;
import tungsten.types.util.AngularDegrees;

class CosTest {
    private MathContext mctx;
    private RealType epsilon;
    private Cos cosFunction;
    private final BigDecimal two = BigDecimal.valueOf(2);
    private final BigDecimal three = BigDecimal.valueOf(3);

    @BeforeEach
    void setUp() {
        mctx = MathContext.DECIMAL128;
        epsilon = new RealImpl(BigDecimal.valueOf(1e-6), mctx);
        cosFunction = new Cos("theta", mctx);
    }

    @Test
    void testApplyAtZero() {
        ArgVector<RealType> args = new UnaryArgVector<>("theta", 
                new RealImpl(BigDecimal.ZERO, mctx));
        RealType result = cosFunction.apply(args);

        RealType expected = new RealImpl(BigDecimal.ONE, mctx);
        assertTrue(areEqualToWithin(expected, result, epsilon),
                "cos(0) should equal 1");
    }

    @Test
    void testApplyAtPiDividedBy2() {
        Pi pi = Pi.getInstance(mctx);
        ArgVector<RealType> args = new UnaryArgVector<>("theta",
                (RealType) pi.divide(new RealImpl(two, mctx)));
        RealType result = cosFunction.apply(args);

        RealType expected = new RealImpl(BigDecimal.ZERO, mctx);
        assertTrue(areEqualToWithin(expected, result, epsilon),
                "cos(π/2) should equal 0");
    }

    @Test
    void testApplyAtPiDividedBy6() {
        Pi pi = Pi.getInstance(mctx);
        ArgVector<RealType> args = new UnaryArgVector<>("theta",
                (RealType) pi.divide(new RealImpl(BigDecimal.valueOf(6), mctx)));
        RealType result = cosFunction.apply(args);

        // Expected value is √3/2 ≈ 0.8660...
        RealType expected = new RealImpl(three.sqrt(mctx).divide(two, mctx), mctx);
        assertTrue(areEqualToWithin(expected, result, epsilon),
                "cos(π/6) should equal √3/2");
    }

    @Test
    void testApplyAtPiDividedBy4() {
        Pi pi = Pi.getInstance(mctx);
        ArgVector<RealType> args = new UnaryArgVector<>("theta",
                (RealType) pi.divide(new RealImpl(BigDecimal.valueOf(4), mctx)));
        RealType result = cosFunction.apply(args);

        // Expected value is √2/2 ≈ 0.7071...
        RealType expected = new RealImpl(two.sqrt(mctx).divide(two, mctx), mctx);
        assertTrue(areEqualToWithin(expected, result, epsilon),
                "cos(π/4) should equal √2/2");
    }

    @Test
    void testApplyAtPiDividedBy3() {
        Pi pi = Pi.getInstance(mctx);
        ArgVector<RealType> args = new UnaryArgVector<>("theta",
                (RealType) pi.divide(new RealImpl(three, mctx)));
        RealType result = cosFunction.apply(args);

        // Expected value is 0.5
        RealType expected = new RealImpl(BigDecimal.valueOf(0.5), mctx);
        assertTrue(areEqualToWithin(expected, result, epsilon),
                "cos(π/3) should equal 0.5");
    }

    @Test
    void testApplyAtPi() {
        Pi pi = Pi.getInstance(mctx);
        ArgVector<RealType> args = new UnaryArgVector<>("theta", pi);
        RealType result = cosFunction.apply(args);

        RealType expected = new RealImpl(BigDecimal.ONE.negate(), mctx);
        assertTrue(areEqualToWithin(expected, result, epsilon),
                "cos(π) should equal -1");
    }

    @Test
    void testApplyAt3PiDividedBy2() {
        Pi pi = Pi.getInstance(mctx);
        ArgVector<RealType> args = new UnaryArgVector<>("theta",
                (RealType) pi.multiply(new RealImpl(three, mctx))
                        .divide(new RealImpl(two, mctx)));
        RealType result = cosFunction.apply(args);

        RealType expected = new RealImpl(BigDecimal.ZERO, mctx);
        assertTrue(areEqualToWithin(expected, result, epsilon),
                "cos(3π/2) should equal 0");
    }

    @Test
    void testApplyAt2Pi() {
        Pi pi = Pi.getInstance(mctx);
        ArgVector<RealType> args = new UnaryArgVector<>("theta",
                (RealType) pi.multiply(new RealImpl(two, mctx)));
        RealType result = cosFunction.apply(args);

        RealType expected = new RealImpl(BigDecimal.ONE, mctx);
        assertTrue(areEqualToWithin(expected, result, epsilon),
                "cos(2π) should equal 1");
    }

    @Test
    void testApplyWithAngularDegrees() {
        // Convert 60 degrees to radians (π/3)
        IntegerType zero = new IntegerImpl(BigInteger.ZERO);
        RealType rezero = new RealImpl(zero, mctx);
        AngularDegrees degrees = new AngularDegrees(new IntegerImpl("60"), zero, rezero);
        ArgVector<RealType> args = new UnaryArgVector<>("theta",
                degrees.asRadians());
        RealType result = cosFunction.apply(args);

        // Expected value is 0.5
        RealType expected = new RealImpl(BigDecimal.valueOf(0.5), mctx);
        assertTrue(areEqualToWithin(expected, result, epsilon),
                "cos(60°) should equal 0.5");
    }

    @Test
    void testDerivativeAtZero() {
        UnaryFunction<RealType, RealType> derivative = cosFunction.diff();

        ArgVector<RealType> args = new UnaryArgVector<>("theta",
                new RealImpl(BigDecimal.ZERO, mctx));
        RealType result = derivative.apply(args);

        // The derivative of cos(x) is -sin(x), and sin(0) = 0
        RealType expected = new RealImpl(BigDecimal.ZERO, mctx);
        assertTrue(areEqualToWithin(expected, result, epsilon),
                "cos'(0) should equal 0");
    }

    @Test
    void testDerivativeAtPiDividedBy2() {
        UnaryFunction<RealType, RealType> derivative = cosFunction.diff();

        Pi pi = Pi.getInstance(mctx);
        ArgVector<RealType> args = new UnaryArgVector<>("theta",
                (RealType) pi.divide(new RealImpl(two, mctx)));
        RealType result = derivative.apply(args);

        // The derivative of cos(x) is -sin(x), and sin(π/2) = 1
        // So at π/2, the derivative should be -1
        RealType expected = new RealImpl(BigDecimal.ONE.negate(), mctx);
        assertTrue(areEqualToWithin(expected, result, epsilon),
                "cos'(π/2) should equal -1");
    }

    @Test
    void testDerivativeAtPi() {
        UnaryFunction<RealType, RealType> derivative = cosFunction.diff();

        Pi pi = Pi.getInstance(mctx);
        ArgVector<RealType> args = new UnaryArgVector<>("theta", pi);
        RealType result = derivative.apply(args);

        // The derivative of cos(x) is -sin(x), and sin(π) = 0
        RealType expected = new RealImpl(BigDecimal.ZERO, mctx);
        assertTrue(areEqualToWithin(expected, result, epsilon),
                "cos'(π) should equal 0");
    }

    @Test
    void testNumericDerivativeWithSimpleDerivative() {
        SimpleDerivative<RealType> simpleDerivative = new SimpleDerivative<>(epsilon);

        UnaryFunction<RealType, RealType> derivative = simpleDerivative.apply(cosFunction);

        Pi pi = Pi.getInstance(mctx);
        
        // Test at 0
        ArgVector<RealType> args1 = new UnaryArgVector<>("theta",
                new RealImpl(BigDecimal.ZERO, mctx));
        RealType result1 = derivative.apply(args1);
        // The derivative of cos(x) is -sin(x), so at 0 it should be 0
        RealType expected1 = new RealImpl(BigDecimal.ZERO, mctx);
        assertTrue(areEqualToWithin(expected1, result1, epsilon),
                "Numeric cos'(0) should be close to 0");

        // Test at π/2
        ArgVector<RealType> args2 = new UnaryArgVector<>("theta",
                (RealType) pi.divide(new RealImpl(two, mctx)));
        RealType result2 = derivative.apply(args2);
        // At π/2, the derivative should be -1
        RealType expected2 = new RealImpl(BigDecimal.ONE.negate(), mctx);
        assertTrue(areEqualToWithin(expected2, result2, epsilon),
                "Numeric cos'(π/2) should be close to -1");

        // Test at π
        ArgVector<RealType> args3 = new UnaryArgVector<>("theta", pi);
        RealType result3 = derivative.apply(args3);
        // At π, the derivative should be 0
        RealType expected3 = new RealImpl(BigDecimal.ZERO, mctx);
        assertTrue(areEqualToWithin(expected3, result3, epsilon),
                "Numeric cos'(π) should be close to 0");
    }

    @Test
    void testPeriodicity() {
        Pi pi = Pi.getInstance(mctx);
        RealType period = cosFunction.period();

        // Verify period is 2π
        RealType expectedPeriod = (RealType) pi.multiply(new RealImpl(two, mctx));
        assertTrue(areEqualToWithin(expectedPeriod, period, epsilon),
                "Period of cos should be 2π");

        // Test periodicity: cos(x) == cos(x + period)
        ArgVector<RealType> args1 = new UnaryArgVector<>("theta",
                (RealType) pi.divide(new RealImpl(two, mctx)));
        RealType result1 = cosFunction.apply(args1);

        ArgVector<RealType> args2 = new UnaryArgVector<>("theta",
                (RealType) pi.divide(new RealImpl(two, mctx)).add(period));
        RealType result2 = cosFunction.apply(args2);

        assertTrue(areEqualToWithin(result1, result2, epsilon),
                "cos(x) should equal cos(x + period)");
    }

    @Test
    void testDifferentiableAnnotation() throws NoSuchMethodException {
        Method diffMethod = Cos.class.getMethod("diff");
        assertNotNull(diffMethod);
        assertTrue(diffMethod.isAnnotationPresent(Differentiable.class));
    }
}
