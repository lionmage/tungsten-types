/* 
 * The MIT License
 *
 * Copyright © 2018 Robert Poole <Tarquin.AZ@gmail.com>.
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
package tungsten.types.numerics.impl;

import org.junit.*;
import tungsten.types.Numeric;
import tungsten.types.exceptions.CoercionException;
import tungsten.types.numerics.RationalType;
import tungsten.types.numerics.RealType;
import tungsten.types.numerics.Sign;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.math.RoundingMode;

import static org.junit.Assert.*;

/**
 *
 * @author tarquin
 */
public class RealImplTest {
    
    public RealImplTest() {
    }
    
    @BeforeClass
    public static void setUpClass() {
    }
    
    @AfterClass
    public static void tearDownClass() {
    }
    
    @Before
    public void setUp() {
    }
    
    @After
    public void tearDown() {
    }

    /**
     * Test of isIrrational method, of class RealImpl.
     */
    @Test
    public void testIsIrrational() {
        System.out.println("isIrrational");
        RealImpl instance = new RealImpl("2");
        instance.setMathContext(MathContext.DECIMAL128);
        boolean expResult = true;
        boolean result = ((RealType) instance.sqrt()).isIrrational();
        assertEquals("Square root of 2 should be irrational", expResult, result);
    }

    /**
     * Test of magnitude method, of class RealImpl.
     */
    @Test
    public void testMagnitude() {
        System.out.println("magnitude");
        RealImpl instance = new RealImpl("-78");
        RealType expResult = new RealImpl("78");
        RealType result = instance.magnitude();
        assertEquals(expResult, result);
    }

    /**
     * Test of asBigDecimal method, of class RealImpl.
     */
    @Test
    public void testAsBigDecimal() {
        System.out.println("asBigDecimal");
        RealImpl instance = new RealImpl("10");
        BigDecimal expResult = BigDecimal.TEN;
        BigDecimal result = instance.asBigDecimal();
        assertEquals(expResult, result);
    }

    /**
     * Test of sign method, of class RealImpl.
     * This also implicitly tests negate().
     */
    @Test
    public void testSign() {
        System.out.println("sign");
        RealImpl instance = new RealImpl("-67.55");
        Sign expResult = Sign.NEGATIVE;
        Sign result = instance.sign();
        assertEquals(expResult, result);
        
        RealType derived = instance.negate();
        expResult = Sign.POSITIVE;
        result = derived.sign();
        assertEquals(expResult, result);
        
        result = ((RealType) instance.add(derived)).sign();
        assertEquals(Sign.ZERO, result);
    }

    /**
     * Test of isExact method, of class RealImpl.
     */
    @Test
    public void testIsExact() {
        System.out.println("isExact");
        RealImpl instance = new RealImpl("2");
        instance.setMathContext(MathContext.DECIMAL128);
        boolean expResult = true;
        boolean result = instance.isExact();
        assertEquals(expResult, result);
        
        // sqrt should not be exact
        expResult = false;
        result = instance.sqrt().isExact();
        assertEquals("Square root of 2 should not be exact", expResult, result);
    }

    /**
     * Test of isCoercibleTo method, of class RealImpl.
     */
    @Test
    public void testIsCoercibleTo() {
        System.out.println("isCoercibleTo");
        Class<? extends Numeric> numtype = RationalType.class;
        RealImpl instance = new RealImpl("0.25");
        boolean expResult = true;
        boolean result = instance.isCoercibleTo(numtype);
        assertEquals(expResult, result);
        
        instance = new RealImpl("2");
        instance.setMathContext(MathContext.DECIMAL128);
        RealType root = (RealType) instance.sqrt();
        expResult = false;
        result = root.isCoercibleTo(numtype);
        assertEquals(expResult, result);
    }

    /**
     * Test of coerceTo method, of class RealImpl.
     */
    @Test
    public void testCoerceTo() {
        System.out.println("coerceTo");
        Class<? extends Numeric> numtype = RationalType.class;
        RealImpl instance = new RealImpl("0.25");
        instance.setMathContext(MathContext.DECIMAL128);
        Numeric expResult = new RationalImpl("1/4");
        try {
            Numeric result = instance.coerceTo(numtype);
            assertEquals(expResult, result);
        } catch (CoercionException coercionException) {
            coercionException.printStackTrace();
            fail("Unable to coerce real to rational.");
        }
    }

    /**
     * Test of add method, of class RealImpl.
     */
    @Test
    public void testAdd() {
        System.out.println("add");
        Numeric addend = new RealImpl("0.1");
        RealImpl instance = new RealImpl(BigDecimal.ZERO);
        Numeric expResult = new RealImpl(BigDecimal.ONE);
        Numeric result = instance;
        for (int k = 0; k < 10; k++) result = result.add(addend);
        assertEquals(expResult, result);
    }

    /**
     * Test of subtract method, of class RealImpl.
     */
    @Test
    public void testSubtract() {
        System.out.println("subtract");
        Numeric subtrahend = new RealImpl("0.1");
        RealImpl instance = new RealImpl(BigDecimal.ONE);
        Numeric expResult = new RealImpl(BigDecimal.ZERO);
        Numeric result = instance;
        for (int k = 0; k < 10; k++) result = result.subtract(subtrahend);
        assertEquals(expResult, result);
    }

    /**
     * Test of multiply method, of class RealImpl.
     */
    @Test
    public void testMultiply() {
        System.out.println("multiply");
        Numeric multiplier = new RationalImpl("3/1000");
        RealImpl instance = new RealImpl("10.0");
        instance.setMathContext(MathContext.DECIMAL128);
        Numeric expResult = new RealImpl("0.03");
        Numeric result = instance.multiply(multiplier);
        assertEquals(expResult, result);
    }

    /**
     * Test of divide method, of class RealImpl.
     */
    @Test
    public void testDivide() {
        System.out.println("divide");
        Numeric divisor = new RealImpl("4.0");
        RealImpl instance = new RealImpl("3.0");
        instance.setMathContext(MathContext.DECIMAL128);
        Numeric expResult = new RealImpl("0.75");
        Numeric result = instance.divide(divisor);
        assertEquals(expResult, result);
    }

    /**
     * Test of inverse method, of class RealImpl.
     */
    @Test
    public void testInverse() {
        System.out.println("inverse");
        RealImpl instance = new RealImpl("100.0");
        instance.setMathContext(MathContext.DECIMAL128);
        Numeric expResult = new RationalImpl("1/100");
        Numeric result = instance.inverse();
        assertEquals(expResult, result);
        
        instance = new RealImpl("1.2");
        instance.setMathContext(MathContext.DECIMAL32);
        expResult = new RealImpl("0.8333333", false);
        result = instance.inverse();
        assertFalse(result.isExact());
        assertEquals(expResult, result);
    }

    /**
     * Test of sqrt method, of class RealImpl.
     */
    @Test
    public void testSqrt() {
        System.out.println("sqrt");
        MathContext myctx = new MathContext(10, RoundingMode.HALF_EVEN);
        RealImpl instance = new RealImpl("0.25");
        instance.setMathContext(myctx);
        Numeric expResult = new RealImpl("0.5");
        Numeric result = instance.sqrt();
        assertEquals(expResult, result);
        assertFalse(((RealType) result).isIrrational());
        
        instance = new RealImpl("16.0");
        instance.setMathContext(myctx);
        expResult = new IntegerImpl(BigInteger.valueOf(4L));
        result = instance.sqrt();
        assertEquals(expResult, result);
        
        instance = new RealImpl("2.0");
        instance.setMathContext(myctx);
        result = instance.sqrt();
        assertFalse(result.isExact());
        assertTrue(((RealType) result).isIrrational());
    }

    /**
     * Test of isIntegralValue method, of class RealImpl.
     */
    @Test
    public void testIsIntegralValue() {
        System.out.println("isIntegralValue");
        RealImpl instance = new RealImpl("15.766");
        boolean expResult = false;
        boolean result = instance.isIntegralValue();
        assertEquals(expResult, result);
        
        instance = new RealImpl("86");
        expResult = true;
        result = instance.isIntegralValue();
        assertEquals(expResult, result);
    }
    
}
