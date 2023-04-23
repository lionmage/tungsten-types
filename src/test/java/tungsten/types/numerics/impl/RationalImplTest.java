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
import tungsten.types.numerics.IntegerType;
import tungsten.types.numerics.RationalType;
import tungsten.types.numerics.RealType;
import tungsten.types.numerics.Sign;

import java.math.BigDecimal;
import java.math.BigInteger;

import static org.junit.Assert.*;

/**
 *
 * @author tarquin
 */
public class RationalImplTest {
    RationalType improper;
    RationalType improper2;
    RationalType fromString;
    RationalType negFromString;
    
    public RationalImplTest() {
    }
    
    @BeforeClass
    public static void setUpClass() {
    }
    
    @AfterClass
    public static void tearDownClass() {
    }
    
    @Before
    public void setUp() {
        improper = new RationalImpl(BigInteger.ONE, BigInteger.valueOf(-3L));
        improper2 = new RationalImpl(BigInteger.valueOf(-2L), BigInteger.valueOf(-3L));
        fromString = new RationalImpl("5/9");
        negFromString = new RationalImpl("-4/3");
    }
    
    @After
    public void tearDown() {
    }

    /**
     * Test of magnitude method, of class RationalImpl.
     */
    @Test
    public void testMagnitude() {
        System.out.println("magnitude");
        RationalImpl instance = new RationalImpl("-3/5");
        RationalType expResult = new RationalImpl(BigInteger.valueOf(3L), BigInteger.valueOf(5L));
        RationalType result = instance.magnitude();
        assertEquals(expResult, result);
        
        instance = new RationalImpl("4/8");
        expResult = new RationalImpl("1/2");
        result = instance.magnitude();
        assertEquals(expResult, result);
    }

    /**
     * Test of negate method, of class RationalImpl.
     */
    @Test
    public void testNegate() {
        System.out.println("negate");
        RationalType expResult = new RationalImpl("-5/9");
        RationalType result = fromString.negate();
        assertEquals(expResult, result);
    }

    /**
     * Test of numerator method, of class RationalImpl.
     */
    @Test
    public void testNumerator() {
        System.out.println("numerator");
        IntegerType expResult = new IntegerImpl("-1");
        IntegerType result = improper.numerator();
        assertEquals(expResult, result);
    }

    /**
     * Test of denominator method, of class RationalImpl.
     */
    @Test
    public void testDenominator() {
        System.out.println("denominator");
        IntegerType expResult = new IntegerImpl(BigInteger.valueOf(3L));
        IntegerType result = improper2.denominator();
        assertEquals(expResult, result);
    }

    /**
     * Test of asBigDecimal method, of class RationalImpl.
     */
    @Test
    public void testAsBigDecimal() {
        System.out.println("asBigDecimal");
        RationalImpl instance = new RationalImpl("1/4");
        BigDecimal expResult = new BigDecimal("0.25");
        BigDecimal result = instance.asBigDecimal();
        assertEquals(expResult, result);
    }

    /**
     * Test of reduce method, of class RationalImpl.
     */
    @Test
    public void testReduce() {
        System.out.println("reduce");
        RationalImpl instance = new RationalImpl("4/8");
        RationalType result = instance.reduce();
        assertEquals(BigInteger.ONE, result.numerator().asBigInteger());
        assertEquals(BigInteger.valueOf(2L), result.denominator().asBigInteger());
    }

    /**
     * Test of isExact method, of class RationalImpl.
     */
    @Test
    public void testIsExact() {
        System.out.println("isExact");
        RationalType instance = negFromString;
        boolean expResult = true;
        boolean result = instance.isExact();
        assertEquals(expResult, result);
    }

    /**
     * Test of isCoercibleTo method, of class RationalImpl.
     */
    @Test
    public void testIsCoercibleTo() {
        System.out.println("isCoercibleTo");
        Class<? extends Numeric> numtype = RealType.class;
        RationalImpl instance = new RationalImpl("1/2");
        boolean expResult = true;
        boolean result = instance.isCoercibleTo(numtype);
        assertEquals(expResult, result);
    }

    /**
     * Test of coerceTo method, of class RationalImpl.
     */
    @Test
    public void testCoerceTo() {
        System.out.println("coerceTo");
        Class<? extends Numeric> numtype = RealType.class;
        RationalImpl instance = new RationalImpl("1/2");
        Numeric expResult = new RealImpl("0.5");
        try {
            Numeric result = instance.coerceTo(numtype);
            assertEquals(expResult, result);
        } catch (CoercionException coercionException) {
            coercionException.printStackTrace();
            fail("Unexpected coercion failure: " + coercionException.getMessage());
        }
    }

    /**
     * Test of add method, of class RationalImpl.
     */
    @Test
    public void testAdd() {
        System.out.println("add");
        Numeric addend = new RationalImpl("1/3");
        RationalImpl instance = new RationalImpl("1/2");
        Numeric expResult = new RationalImpl("5/6");
        Numeric result = instance.add(addend);
        assertEquals(expResult, result);
    }

    /**
     * Test of subtract method, of class RationalImpl.
     */
    @Test
    public void testSubtract() {
        System.out.println("subtract");
        Numeric subtrahend = new RationalImpl("1/3");
        RationalImpl instance = new RationalImpl("8/9");
        Numeric expResult = new RationalImpl("5/9");
        Numeric result = instance.subtract(subtrahend);
        assertEquals(expResult, result);
    }

    /**
     * Test of multiply method, of class RationalImpl.
     */
    @Test
    public void testMultiply() {
        System.out.println("multiply");
        Numeric multiplier = new IntegerImpl("3");
        RationalImpl instance = new RationalImpl("1/3");
        Numeric expResult = new IntegerImpl(BigInteger.ONE);
        Numeric result = instance.multiply(multiplier);
        assertEquals(expResult, result);
        
        multiplier = new RationalImpl("2/3");
        expResult = new RationalImpl("2/9");
        result = instance.multiply(multiplier);
        assertEquals(expResult, result);
    }

    /**
     * Test of divide method, of class RationalImpl.
     */
    @Test
    public void testDivide() {
        System.out.println("divide");
        Numeric divisor = new IntegerImpl(BigInteger.valueOf(2L));
        RationalImpl instance = new RationalImpl("3/8");
        Numeric expResult = new RationalImpl("3/16");
        Numeric result = instance.divide(divisor);
        assertEquals(expResult, result);
        
        divisor = new RationalImpl("1/2");
        expResult = new RationalImpl("3/4");
        result = instance.divide(divisor);
        assertEquals(expResult, result);
        
        instance = new RationalImpl("1/2");
        // divisor is still 1/2
        expResult = new IntegerImpl(BigInteger.ONE);
        result = instance.divide(divisor);
        assertEquals(expResult, result);
    }

    /**
     * Test of inverse method, of class RationalImpl.
     */
    @Test
    public void testInverse() {
        System.out.println("inverse");
        RationalImpl instance = new RationalImpl("1/3");
        Numeric expResult = new IntegerImpl(BigInteger.valueOf(3L));
        Numeric result = instance.inverse();
        assertEquals(expResult, result);
        
        instance = new RationalImpl("3/5");
        expResult = new RationalImpl("5/3");
        result = instance.inverse();
        assertEquals(expResult, result);
    }

    /**
     * Test of sqrt method, of class RationalImpl.
     */
    @Test
    public void testSqrt() {
        System.out.println("sqrt");
        RationalImpl instance = new RationalImpl("1/4");
        RationalType expResult = new RationalImpl("1/2");
        RationalType result = (RationalType) instance.sqrt();
        assertEquals(expResult, result);
    }

    /**
     * Test of equals method, of class RationalImpl.
     */
    @Test
    public void testEquals() {
        System.out.println("equals");
        Object other = new RationalImpl("7/8");
        RationalImpl instance = new RationalImpl(BigInteger.valueOf(7L), BigInteger.valueOf(8L));
        boolean expResult = true;
        boolean result = instance.equals(other);
        assertEquals(expResult, result);
    }

    /**
     * Test of compareTo method, of class RationalImpl.
     */
    @Test
    public void testCompareTo() {
        System.out.println("compareTo");
        RationalType o = new RationalImpl("3/9");
        RationalImpl instance = new RationalImpl("1/3");
        int result = instance.compareTo(o);
        assertEquals(0, result);
        
        o = new RationalImpl("1/2");
        result = instance.compareTo(o);
        assertTrue(result < 0);
    }

    /**
     * Test of sign method, of class RationalImpl.
     */
    @Test
    public void testSign() {
        System.out.println("sign");
        RationalImpl instance = new RationalImpl("2/3");
        Sign expResult = Sign.POSITIVE;
        Sign result = instance.sign();
        assertEquals(expResult, result);
        
        instance = new RationalImpl("-42/77");
        expResult = Sign.NEGATIVE;
        result = instance.sign();
        assertEquals(expResult, result);
    }
    
}
