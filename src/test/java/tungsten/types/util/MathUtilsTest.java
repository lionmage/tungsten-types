package tungsten.types.util;
/*
 * The MIT License
 *
 * Copyright © 2023 Robert Poole <Tarquin.AZ@gmail.com>.
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

import org.junit.jupiter.api.Test;
import tungsten.types.Numeric;
import tungsten.types.numerics.IntegerType;
import tungsten.types.numerics.RationalType;
import tungsten.types.numerics.RealType;
import tungsten.types.numerics.impl.IntegerImpl;
import tungsten.types.numerics.impl.Pi;
import tungsten.types.numerics.impl.RationalImpl;
import tungsten.types.numerics.impl.RealImpl;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;

import static org.junit.jupiter.api.Assertions.*;

public class MathUtilsTest {
    @Test
    public void checkGeneralizedBinomialCoefficient() {
        RationalType x = new RationalImpl("2/3");
        IntegerType  k = new IntegerImpl(BigInteger.valueOf(2L));
        RationalType expected = new RationalImpl("-1/9");

        Numeric result = MathUtils.generalizedBinomialCoefficient(x, k);
        assertEquals(expected, result);

        IntegerType x2 = new IntegerImpl("-4");
        k = new IntegerImpl("3");
        IntegerType expected2 = new IntegerImpl("-20");

        result = MathUtils.generalizedBinomialCoefficient(x2, k);
        assertEquals(expected2, result);
    }

    @Test
    public void checkGammaFunction() {
        final MathContext roundingCtx = new MathContext(6);  // only checking 6 significant digits for now

        RationalImpl z = new RationalImpl("1/2");
        z.setMathContext(MathContext.DECIMAL128);
        RealType expectedResult = (RealType) Pi.getInstance(MathContext.DECIMAL128).sqrt();

        Numeric result = MathUtils.gamma(z);

        assertEquals(expectedResult, result,
                "\uD835\uDEAA(1/2) should equal \u221A\uD835\uDF0B");

        RealType z2 = new RealImpl("0.5", MathContext.DECIMAL128);
        result = MathUtils.gamma(z2);
        assertEquals(MathUtils.round(expectedResult, roundingCtx), MathUtils.round((RealType) result, roundingCtx),
                "\uD835\uDEAA(0.5) should equal \u221A\uD835\uDF0B");

        IntegerType three = new IntegerImpl("3");
        IntegerType expectedResult2 = MathUtils.factorial(new IntegerImpl("2"));
        result = MathUtils.gamma(three);

        assertEquals(expectedResult2, result);
    }

    @Test
    public void alternateLogarithms() {
        RealImpl val = new RealImpl("1025.0", MathContext.DECIMAL128);
        final RealType base = new RealImpl("2.0");

        RealType result = MathUtils.log(val, base, MathContext.DECIMAL128);
        assertFalse(result.isExact());  // rounding -> inexact

        IntegerType lgfloor = MathUtils.log2floor(val);
        assertFalse(lgfloor.isExact());  // this should not be an exact value since val is not a power of 2

        final RealType ten = new RealImpl(BigDecimal.TEN, false);

        assertEquals(lgfloor, result.floor());
        assertTrue(val.compareTo(ten) > 0, "lg(1025) should be > lg(1024)");

        val = new RealImpl("1024.0", MathContext.DECIMAL128);
        result = MathUtils.log(val, base, MathContext.DECIMAL128);
        assertFalse(result.isExact());  // there's rounding involved, so this shouldn't be exact either

        assertEquals(ten, result, "lg(1024) should equal 10 exactly");
        assertTrue(result.isCoercibleTo(IntegerType.class));

        lgfloor = MathUtils.log2floor(val);
        assertTrue(lgfloor.isExact());  // this ought to return an exact value since val is a power of 2
        assertEquals(BigInteger.TEN, lgfloor.asBigInteger());
    }
}
