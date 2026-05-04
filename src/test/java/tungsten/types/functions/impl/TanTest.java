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

import org.junit.jupiter.api.Test;
import tungsten.types.functions.ArgVector;
import tungsten.types.functions.UnaryArgVector;
import tungsten.types.functions.UnaryFunction;
import tungsten.types.numerics.RealType;
import tungsten.types.numerics.impl.Pi;
import tungsten.types.numerics.impl.RealImpl;
import tungsten.types.util.AngularDegrees;
import tungsten.types.util.MathUtils;

import java.math.BigDecimal;
import java.math.MathContext;

import static org.junit.jupiter.api.Assertions.*;

class TanTest {
    private final MathContext mctx = new MathContext(10);
    private final RealImpl epsilon = new RealImpl(BigDecimal.valueOf(0.0001), mctx);
    private final RealImpl four = new RealImpl(BigDecimal.valueOf(4L), mctx);
    private final RealImpl three = new RealImpl(BigDecimal.valueOf(3L), mctx);
    private final RealImpl two = new RealImpl(BigDecimal.valueOf(2L), mctx);

    @Test
    void testApplyWithRadians() {
        String varName = "theta";
        RealType piOver4 = (RealType) Pi.getInstance(mctx).divide(four);
        
        Tan tan = new Tan(varName, mctx);
        ArgVector<RealType> args = new UnaryArgVector<>(varName, piOver4);
        
        RealType result = tan.apply(args);
        RealType expected = MathUtils.tan(piOver4);
        
        assertTrue(MathUtils.areEqualToWithin(result, expected, epsilon),
                "tan(π/4) should be approximately 1.0");
    }

    @Test
    void testApplyWithDegrees() {
        String varName = "theta";
        AngularDegrees angle30 = new AngularDegrees("30°");
        RealType radians = angle30.asRadians();
        
        Tan tan = new Tan(varName, mctx);
        ArgVector<RealType> args = new UnaryArgVector<>(varName, radians);
        
        RealType result = tan.apply(args);
        RealType expected = MathUtils.tan(radians);
        RealType expectedDecimal = (RealType) three.sqrt().divide(three); // new RealImpl(BigDecimal.valueOf(Math.sqrt(3) / 3), mctx);
        
        assertTrue(MathUtils.areEqualToWithin(result, expected, epsilon),
                "tan(30°) should be approximately √3/3");
        assertTrue(MathUtils.areEqualToWithin(expected, expectedDecimal, epsilon),
                "Expected value should match √3/3");
    }

    @Test
    void testApplyWithDMS() {
        String varName = "theta";
        AngularDegrees angle4530 = new AngularDegrees("45°30′0″");
        RealType radians = angle4530.asRadians();
        // this should be 0.793, but due to rounding error with the default precision,
        // will be more like 0.803
        System.out.println("45°30′0″ in radians: " + radians);
        
        Tan tan = new Tan(varName, mctx);
        ArgVector<RealType> args = new UnaryArgVector<>(varName, radians);
        
        RealType result = tan.apply(args);
        RealType expected = MathUtils.tan(radians);
        
        assertTrue(MathUtils.areEqualToWithin(result, expected, epsilon),
                "tan(45°30′) should be computed correctly");
    }

    @Test
    void testDerivativeAtZero() {
        String varName = "theta";
        RealType zero = new RealImpl(BigDecimal.ZERO, mctx);
        
        Tan tan = new Tan(varName, mctx);
        SimpleDerivative<RealType> derivative = new SimpleDerivative<>(epsilon);
        
        UnaryFunction<RealType, RealType> tanPrime = derivative.apply(tan);
        ArgVector<RealType> args = new UnaryArgVector<>(varName, zero);
        
        RealType result = tanPrime.apply(args);
        // The derivative of tan(x) is sec²(x), and sec(0) = 1, so sec²(0) = 1
        RealType expected = new RealImpl(BigDecimal.ONE, mctx);
        
        assertTrue(MathUtils.areEqualToWithin(result, expected, epsilon),
                "tan'(0) should be 1.0");
    }

    @Test
    void testDerivativeAtPiOver4() {
        String varName = "theta";
        RealType pi = Pi.getInstance(mctx);
        RealType quarterPi = (RealType) pi.divide(four);
        
        Tan tan = new Tan(varName, mctx);
        SimpleDerivative<RealType> derivative = new SimpleDerivative<>(epsilon);
        
        UnaryFunction<RealType, RealType> tanPrime = derivative.apply(tan);
        ArgVector<RealType> args = new UnaryArgVector<>(varName, quarterPi);
        
        RealType result = tanPrime.apply(args);
        // The derivative of tan(x) is sec²(x), and sec(π/4) = √2, so sec²(π/4) = 2

        assertTrue(MathUtils.areEqualToWithin(result, two, epsilon),
                "tan'(π/4) should be approximately 2.0");
    }

    @Test
    void testDerivativeWithDegreesInput() {
        String varName = "theta";
        AngularDegrees angle60 = new AngularDegrees("60°");
        RealType radians = angle60.asRadians();
        
        Tan tan = new Tan(varName, mctx);
        SimpleDerivative<RealType> derivative = new SimpleDerivative<>(epsilon);
        
        UnaryFunction<RealType, RealType> tanPrime = derivative.apply(tan);
        ArgVector<RealType> args = new UnaryArgVector<>(varName, radians);
        
        RealType result = tanPrime.apply(args);
        // The derivative of tan(x) is sec²(x)
        // For x = 60° = π/3, cos(π/3) = 1/2, so sec(π/3) = 2 and sec²(π/3) = 4

        assertTrue(MathUtils.areEqualToWithin(result, four, epsilon),
                "tan'(60°) should be approximately 4.0");
    }

    @Test
    void testPeriodicity() {
        String varName = "theta";
        RealType pi = Pi.getInstance(mctx);
        RealType twoPi = (RealType) pi.multiply(two);
        
        Tan tan = new Tan(varName, mctx);
        assertTrue(MathUtils.areEqualToWithin(pi, tan.period(), epsilon),
                "Period of tan(x) is π");
        
        // Test that tan(x + 2π) = tan(x)
        ArgVector<RealType> args1 = new UnaryArgVector<>(varName, (RealType) pi.divide(four));
        ArgVector<RealType> args2 = new UnaryArgVector<>(varName, (RealType) pi.divide(four).add(twoPi));
        
        RealType result1 = tan.apply(args1);
        RealType result2 = tan.apply(args2);
        
        assertTrue(MathUtils.areEqualToWithin(result1, result2, epsilon),
                "tan(x + 2π) should equal tan(x)");
    }

    @Test
    void testConstructorWithDefaultName() {
        Tan tan = new Tan(mctx);
        RealType piOver6 = (RealType) Pi.getInstance(mctx).divide(new RealImpl(BigDecimal.valueOf(6L), mctx));
        
        ArgVector<RealType> args = new UnaryArgVector<>(MathUtils.THETA, piOver6);
        RealType result = tan.apply(args);
        
        // tan(π/6) = 1/√3 ≈ 0.577...
        RealType expected = MathUtils.tan(piOver6);
        
        assertTrue(MathUtils.areEqualToWithin(result, expected, epsilon),
                "tan(π/6) with default argument name should work correctly");
    }

    @Test
    void testApplyWithIndexBasedAccess() {
        String varName = "theta";
        Tan tan = new Tan(varName, mctx);
        
        RealType angle = (RealType) Pi.getInstance(mctx).divide(three);
        ArgVector<RealType> args = new UnaryArgVector<>("zz", angle);
        // Remove the variable name mapping to test index-based access
        // This simulates a scenario where only positional access is available

        // NOTE: Qwen3 generated the above comment, but didn't actually generate the logic
        // therefore, I constructed the ArgVector using a variable name
        // different from that assigned to Tan.
        
        RealType result = tan.apply(args);
        RealType expected = MathUtils.tan(angle);
        
        assertTrue(MathUtils.areEqualToWithin(result, expected, epsilon),
                "tan(x) should work with positional argument access");
    }
}
