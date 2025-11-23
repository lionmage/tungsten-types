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

package tungsten.types.numerics.impl;

import org.junit.jupiter.api.Test;
import tungsten.types.Numeric;
import tungsten.types.numerics.ComplexType;
import tungsten.types.numerics.RealType;
import static org.junit.jupiter.api.Assertions.*;
import static tungsten.types.util.MathUtils.areEqualToWithin;

import java.math.BigDecimal;
import java.math.MathContext;

/**
 * Tests for polar complex values.
 *
 * @author Robert Poole, <a href="mailto:Tarquin.AZ@gmail.com">Gmail</a>
 */
public class ComplexPolarImplTest {
    private final RealType four = new RealImpl("4.00", MathContext.DECIMAL128);
    private final RealType two = new RealImpl("2.00", MathContext.DECIMAL128);
    private final RealType three = new RealImpl(BigDecimal.valueOf(3L), MathContext.DECIMAL128);
    private final RealType epsilon = new RealImpl("0.00000001", MathContext.DECIMAL64);

    @Test
    public void testSqrt() {
        RealType arg = (RealType) Pi.getInstance(MathContext.DECIMAL128).divide(two);
        ComplexPolarImpl polar = new ComplexPolarImpl(four, arg);
        polar.setMathContext(MathContext.DECIMAL128);
        Numeric result = polar.sqrt();
        assertInstanceOf(ComplexType.class, result);
        ComplexType cresult = (ComplexType) result;
        assertEquals(two, cresult.magnitude());
        RealType expectedArg = (RealType) Pi.getInstance(MathContext.DECIMAL128).divide(four);
        assertTrue(areEqualToWithin(expectedArg, cresult.argument(), epsilon));
    }

    @Test
    public void negation() {
        RealType arg = (RealType) three.multiply(Pi.getInstance(MathContext.DECIMAL128)).divide(four);
        ComplexPolarImpl polar = new ComplexPolarImpl(two, arg);
        ComplexType neg = polar.negate();
        assertInstanceOf(ComplexPolarImpl.class, neg);
        assertTrue(areEqualToWithin(Pi.getInstance(MathContext.DECIMAL128),
                arg.subtract(neg.argument()).magnitude(), epsilon));
        Numeric diffsum = polar.add(neg);
        assertInstanceOf(ComplexType.class, diffsum);
        assertTrue(Zero.isZero(diffsum));
        // now negate the negation
        ComplexType negOfNeg = neg.negate();
        assertInstanceOf(ComplexPolarImpl.class, negOfNeg);
        assertFalse(negOfNeg.isExact());
        ComplexPolarImpl.equalToWithin(polar, (ComplexPolarImpl) negOfNeg, epsilon);
    }
}
