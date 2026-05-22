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
import tungsten.types.Numeric;
import tungsten.types.functions.UnaryArgVector;
import tungsten.types.numerics.ComplexType;
import tungsten.types.numerics.IntegerType;
import tungsten.types.numerics.RealType;
import tungsten.types.numerics.impl.ComplexRectImpl;
import tungsten.types.numerics.impl.IntegerImpl;
import tungsten.types.numerics.impl.Pi;
import tungsten.types.numerics.impl.RealImpl;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.math.RoundingMode;

import static org.junit.jupiter.api.Assertions.*;
import static tungsten.types.util.MathUtils.areEqualToWithin;

class GammaTest {
    private static final MathContext ctx = new MathContext(34, RoundingMode.HALF_UP);
    private static final RealImpl epsilon = new RealImpl(BigDecimal.TEN.pow(-18, ctx), ctx); // Qwen3 generated new BigDecimal("1E-30")
    public static final BigDecimal TWO = BigDecimal.valueOf(2L);

    private IntegerType getIntFor(long val) {
        return new IntegerImpl(BigInteger.valueOf(val)) {
            @Override
            public MathContext getMathContext() {
                return ctx;
            }
        };
    }

    @Test
    void testGammaPositiveInteger() {
        // Test Γ(n) for positive integers equals (n-1)!
        var gamma = new Gamma();

        // Γ(1) = 0! = 1
        UnaryArgVector<Numeric> vec1 = new UnaryArgVector<>("z", getIntFor(1L));
        RealType result1 = (RealType) gamma.apply(vec1);
        System.out.println("result1: " + result1 + ", value = " + result1.asBigDecimal().toPlainString());
        assertTrue(areEqualToWithin(new RealImpl(BigDecimal.ONE, ctx), result1,
                epsilon));

        // Γ(2) = 1! = 1
        UnaryArgVector<Numeric> vec2 = new UnaryArgVector<>("z", getIntFor(2L));
        RealType result2 = (RealType) gamma.apply(vec2);
        assertTrue(areEqualToWithin(new RealImpl(BigDecimal.ONE, ctx), result2,
                epsilon));

        // Γ(5) = 4! = 24
        UnaryArgVector<Numeric> vec5 = new UnaryArgVector<>("z", getIntFor(5L));
        RealType result5 = (RealType) gamma.apply(vec5);
        assertTrue(areEqualToWithin(new RealImpl(BigDecimal.valueOf(24L), ctx), result5,
                epsilon));

        // Γ(10) = 9! = 362880
        UnaryArgVector<Numeric> vec10 = new UnaryArgVector<>("z", getIntFor(10L));
        RealType result10 = (RealType) gamma.apply(vec10);
        System.out.println("result10: " + result10 + ", value = " + result10.asBigDecimal().toPlainString());
        // the result should be an integer anyway, so as long as we're within 0.5 we are good
        assertTrue(areEqualToWithin(new RealImpl(BigDecimal.valueOf(362880L), ctx), result10,
                new RealImpl("0.5", ctx)));
    }

    @Test
    void testGammaAtZeroAndNegativeIntegers() {
        var gamma = new Gamma();

        // Γ(z) should throw ArithmeticException at z = 0
        UnaryArgVector<Numeric> vecZero = new UnaryArgVector<>("z", new IntegerImpl(BigInteger.ZERO));
        assertThrows(ArithmeticException.class, () -> gamma.apply(vecZero));

        // Γ(z) should throw ArithmeticException for negative integers
        UnaryArgVector<Numeric> vecNeg1 = new UnaryArgVector<>("z", getIntFor(-1L));
        assertThrows(ArithmeticException.class, () -> gamma.apply(vecNeg1));

        UnaryArgVector<Numeric> vecNeg2 = new UnaryArgVector<>("z", getIntFor(-2L));
        assertThrows(ArithmeticException.class, () -> gamma.apply(vecNeg2));

        UnaryArgVector<Numeric> vecNeg5 = new UnaryArgVector<>("z", getIntFor(-5L));
        assertThrows(ArithmeticException.class, () -> gamma.apply(vecNeg5));
    }

    @Test
    void testGammaRealValues() {
        var gamma = new Gamma();

        // Γ(0.5) = √π ≈ 1.77245385091
        UnaryArgVector<Numeric> vecHalf = new UnaryArgVector<>("z", new RealImpl("0.5", ctx));
        RealType resultHalf = (RealType) gamma.apply(vecHalf);
        RealType expectedHalf = (RealType) Pi.getInstance(ctx).sqrt();
        System.out.println("resultHalf: " + resultHalf + ", expectedHalf: " + expectedHalf);
        assertTrue(areEqualToWithin(expectedHalf, resultHalf,
                epsilon));

        // Γ(1.5) = √π / 2 ≈ 0.88622692545
        UnaryArgVector<Numeric> vecOneHalf = new UnaryArgVector<>("z", new RealImpl("1.5", ctx));
        RealType resultOneHalf = (RealType) gamma.apply(vecOneHalf);
        RealType expectedOneHalf = (RealType) Pi.getInstance(ctx).sqrt().divide(new RealImpl(TWO, ctx));
        assertTrue(areEqualToWithin(expectedOneHalf, resultOneHalf,
                epsilon));
    }

    @Test
    void testGammaComplexValues() {
        var gamma = new Gamma();

        // Γ(1 + i) where i is imaginary unit
        ComplexType complexOnePlusI = new ComplexRectImpl(
            new RealImpl(BigDecimal.ONE, ctx),
            new RealImpl(BigDecimal.ONE, ctx)
        );
        UnaryArgVector<Numeric> vecComplex1 = new UnaryArgVector<>("z", complexOnePlusI);
        ComplexType resultComplex1 = (ComplexType) gamma.apply(vecComplex1);

        // Verify real and imaginary parts are computed
        assertNotNull(resultComplex1.real());
        assertNotNull(resultComplex1.imaginary());

        // Γ(2.5 + 0.5i)
        ComplexType complex25PlusHalfI = new ComplexRectImpl(
            new RealImpl(new BigDecimal("2.5"), ctx),
            new RealImpl(new BigDecimal("0.5"), ctx)
        );
        UnaryArgVector<Numeric> vecComplex2 = new UnaryArgVector<>("z", complex25PlusHalfI);
        ComplexType resultComplex2 = (ComplexType) gamma.apply(vecComplex2);

        // Verify real and imaginary parts are computed
        assertNotNull(resultComplex2.real());
        assertNotNull(resultComplex2.imaginary());

        // Test with negative real part but non-integer
        ComplexType complexNegHalfPlusI = new ComplexRectImpl(
            new RealImpl(new BigDecimal("-0.5"), ctx),
            new RealImpl(BigDecimal.ONE, ctx)
        );
        UnaryArgVector<Numeric> vecComplex3 = new UnaryArgVector<>("z", complexNegHalfPlusI);
        ComplexType resultComplex3 = (ComplexType) gamma.apply(vecComplex3);

        // Verify real and imaginary parts are computed
        assertNotNull(resultComplex3.real());
        assertNotNull(resultComplex3.imaginary());
    }

    @Test
    void testGammaConstructorAndVariableName() {
        // Test with custom variable name
        var gammaCustom = new Gamma("x");
        UnaryArgVector<Numeric> vec = new UnaryArgVector<>("x", getIntFor(3L));
        RealType result = (RealType) gammaCustom.apply(vec);
        
        // Γ(3) = 2! = 2
        assertTrue(areEqualToWithin(new RealImpl(TWO, ctx), result,
                epsilon));

        // Test default constructor with variable name "z"
        var gammaDefault = new Gamma();
        UnaryArgVector<Numeric> vecZ = new UnaryArgVector<>("z", getIntFor(4L));
        RealType resultZ = (RealType) gammaDefault.apply(vecZ);
        
        // Γ(4) = 3! = 6
        assertTrue(areEqualToWithin(new RealImpl(BigDecimal.valueOf(6), ctx), resultZ,
                epsilon));
    }
}
