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

import java.math.BigInteger;

import static org.junit.Assert.*;

/**
 *
 * @author tarquin
 */
public class IntegerImplTest {
    
    public IntegerImplTest() {
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
     * Test of magnitude method, of class IntegerImpl.
     */
    @Test
    public void testMagnitude() {
        System.out.println("magnitude");
        IntegerImpl instance = new IntegerImpl("1000");
        IntegerType expResult = new IntegerImpl(BigInteger.valueOf(1000L));
        IntegerType result = instance.magnitude();
        assertEquals(expResult, result);
        
        instance = new IntegerImpl("-7");
        expResult = new IntegerImpl(BigInteger.valueOf(7L));
        result = instance.magnitude();
        assertEquals(expResult, result);
    }

    /**
     * Test of negate method, of class IntegerImpl.
     */
    @Test
    public void testNegate() {
        System.out.println("negate");
        IntegerImpl instance = new IntegerImpl("1000");
        IntegerType expResult = new IntegerImpl(BigInteger.valueOf(-1000L));
        IntegerType result = instance.negate();
        assertEquals(expResult, result);

        instance = new IntegerImpl("-7");
        expResult = new IntegerImpl(BigInteger.valueOf(7L));
        result = instance.magnitude();
        assertEquals(expResult, result);
    }

    /**
     * Test of modulus method, of class IntegerImpl.
     */
    @Test
    public void testModulus() {
        System.out.println("modulus");
        IntegerType divisor = new IntegerImpl(BigInteger.TEN);
        IntegerImpl instance = new IntegerImpl(BigInteger.valueOf(86753L));
        IntegerType expResult = new IntegerImpl("3");
        IntegerType result = instance.modulus(divisor);
        assertEquals(expResult, result);
    }

    /**
     * Test of isEven method, of class IntegerImpl.
     */
    @Test
    public void testIsEven() {
        System.out.println("isEven");
        IntegerImpl instance = new IntegerImpl(BigInteger.ONE);
        boolean expResult = false;
        boolean result = instance.isEven();
        assertEquals(expResult, result);
        
        instance = new IntegerImpl(BigInteger.valueOf(4096L));
        expResult = true;
        result = instance.isEven();
        assertEquals(expResult, result);
    }

    /**
     * Test of isOdd method, of class IntegerImpl.
     */
    @Test
    public void testIsOdd() {
        System.out.println("isOdd");
        IntegerImpl instance = new IntegerImpl(BigInteger.ONE);
        boolean expResult = true;
        boolean result = instance.isOdd();
        assertEquals(expResult, result);
        
        instance = new IntegerImpl(BigInteger.valueOf(4096L));
        expResult = false;
        result = instance.isOdd();
        assertEquals(expResult, result);
    }

    /**
     * Test of isPerfectSquare method, of class IntegerImpl.
     */
    @Test
    public void testIsPerfectSquare() {
        System.out.println("isPerfectSquare");
        IntegerImpl instance = new IntegerImpl("18");
        boolean result = instance.isPerfectSquare();
        assertEquals("18 is not a perfect square", false, result);
        
        instance = new IntegerImpl("100");
        result = instance.isPerfectSquare();
        assertEquals(true, result);
        
        instance = new IntegerImpl("4096");
        result = instance.isPerfectSquare();
        assertEquals(true, result);
    }

    /**
     * Test of numberOfDigits method, of class IntegerImpl.
     */
    @Test
    public void testNumberOfDigits() {
        System.out.println("numberOfDigits");
        IntegerImpl inst1 = new IntegerImpl("8675309");
        long result1 = inst1.numberOfDigits();
        assertEquals(7L, result1);
        IntegerImpl inst_base = new IntegerImpl("132");
        long result_base = inst_base.numberOfDigits();
        assertEquals("Baseline test", 3L, result_base);
        IntegerImpl inst2 = new IntegerImpl("-32");
        long result2 = inst2.numberOfDigits();
        assertEquals("Negative number test", 2L, result2);
        IntegerImpl inst3 = new IntegerImpl(BigInteger.ZERO);
        long result3 = inst3.numberOfDigits();
        assertEquals(1L, result3);
        IntegerImpl inst4 = new IntegerImpl(BigInteger.TEN);
        long result4 = inst4.numberOfDigits();
        assertEquals(2L, result4);
        IntegerImpl inst5 = new IntegerImpl(BigInteger.ONE);
        long result5 = inst5.numberOfDigits();
        assertEquals(1L, result5);
        
        IntegerImpl inst6 = new IntegerImpl("123456789012345678901234567890");
        long result6 = inst6.numberOfDigits();
        assertEquals(30L, result6);
    }

    /**
     * Test of digitAt method, of class IntegerImpl.
     */
    @Test
    public void testDigitAt() {
        System.out.println("digitAt");
        long position = 0L;
        IntegerImpl instance = new IntegerImpl("8675309");
        int result = instance.digitAt(position);
        assertEquals(9, result);
        
        position = 3L;
        result = instance.digitAt(position);
        assertEquals(5, result);
        
        position = 6L;
        result = instance.digitAt(position);
        assertEquals(8, result);
        
        // test exceeding the number of digits
        position = 10L;
        try {
            result = instance.digitAt(position);
            // should not reach the following statement
            fail("Expected exception to be thrown, but none was");
        } catch (Exception e) {
            e.printStackTrace();
            assertTrue(e instanceof IndexOutOfBoundsException);
        }
    }

    /**
     * Test of pow method, of class IntegerImpl.
     */
    @Test
    public void testPow() {
        System.out.println("pow");
        IntegerType exponent = new IntegerImpl("3");
        IntegerImpl instance = new IntegerImpl("2");
        Numeric expResult = new IntegerImpl(BigInteger.valueOf(8L));
        Numeric result = instance.pow(exponent);
        assertEquals(expResult, result);
        assertTrue(result instanceof IntegerType);
        
        // also test negative exponents
        exponent = new IntegerImpl("-2");
        expResult = new RationalImpl("1/4");
        result = instance.pow(exponent);
        assertEquals(expResult, result);
        assertTrue(result instanceof RationalType);
    }

    /**
     * Test of isCoercibleTo method, of class IntegerImpl.
     */
    @Test
    public void testIsCoercibleTo() {
        System.out.println("isCoercibleTo");
        Class<? extends Numeric> numtype = RealType.class;
        IntegerImpl instance = new IntegerImpl("-42");
        boolean expResult = true;
        boolean result = instance.isCoercibleTo(numtype);
        assertEquals(expResult, result);
    }

    /**
     * Test of coerceTo method, of class IntegerImpl.
     */
    @Test
    public void testCoerceTo() {
        System.out.println("coerceTo");
        Class<? extends Numeric> numtype = RealType.class;
        IntegerImpl instance = new IntegerImpl("-42");
        try {
            Numeric result = instance.coerceTo(numtype);
            assertTrue(result instanceof RealType);
            assertTrue(result.isExact());
        } catch (CoercionException ex) {
            ex.printStackTrace();
            fail("Type coercion from IntegerType to RealType should not have failed!");
        }
    }

    /**
     * Test of add method, of class IntegerImpl.
     */
    @Test
    public void testAdd() {
        System.out.println("add");
        Numeric addend = new IntegerImpl("1");
        IntegerImpl instance = new IntegerImpl(BigInteger.ZERO);
        Numeric expResult = new IntegerImpl(BigInteger.TEN);
        Numeric result = instance;
        for (int k = 0; k < 10; k++) result = result.add(addend);
        assertEquals(expResult, result);
    }

    /**
     * Test of subtract method, of class IntegerImpl.
     */
    @Test
    public void testSubtract() {
        System.out.println("subtract");
        Numeric subtrahend = new IntegerImpl("1");
        IntegerImpl instance = new IntegerImpl(BigInteger.TEN);
        Numeric expResult = new IntegerImpl(BigInteger.ZERO);
        Numeric result = instance;
        for (int k = 0; k < 10; k++) result = result.subtract(subtrahend);
        assertEquals(expResult, result);
    }

    /**
     * Test of multiply method, of class IntegerImpl.
     */
    @Test
    public void testMultiply() {
        System.out.println("multiply");
        Numeric multiplier = new IntegerImpl(BigInteger.valueOf(2L));
        IntegerImpl instance = new IntegerImpl(BigInteger.ONE);
        Numeric expResult = new IntegerImpl("16");
        Numeric result = instance;
        for (int k = 0; k < 4; k++) result = result.multiply(multiplier);
        assertEquals(expResult, result);
    }

    /**
     * Test of divide method, of class IntegerImpl.
     */
    @Test
    public void testDivide() {
        System.out.println("divide");
        Numeric divisor = new IntegerImpl(BigInteger.valueOf(2L));
        IntegerImpl instance = new IntegerImpl("16");
        Numeric expResult = new IntegerImpl(BigInteger.ONE);
        Numeric result = instance;
        for (int k = 0; k < 4; k++) result = result.divide(divisor);
        assertEquals(expResult, result);
    }

    /**
     * Test of inverse method, of class IntegerImpl.
     */
    @Test
    public void testInverse() {
        System.out.println("inverse");
        IntegerImpl instance = new IntegerImpl("5");
        Numeric expResult = new RationalImpl("1/5");
        Numeric result = instance.inverse();
        assertEquals(expResult, result);
    }

    /**
     * Test of sqrt method, of class IntegerImpl.
     */
    @Test
    public void testSqrt() {
        System.out.println("sqrt");
        IntegerImpl instance = new IntegerImpl(BigInteger.valueOf(8L));
        IntegerType expResult = new IntegerImpl(BigInteger.valueOf(2L), false);
        IntegerType result = instance.sqrt();
        assertEquals(expResult, result);
        assertFalse(result.isExact());
        
        instance = new IntegerImpl("9");
        expResult = new IntegerImpl(BigInteger.valueOf(3L));
        result = instance.sqrt();
        assertEquals(expResult, result);
        assertTrue(result.isExact());
    }

    /**
     * Test of equals method, of class IntegerImpl.
     */
    @Test
    public void testEquals() {
        System.out.println("equals");
        Object other = new IntegerImpl(BigInteger.ZERO);
        IntegerImpl instance = new IntegerImpl("0");
        boolean expResult = true;
        boolean result = instance.equals(other);
        assertEquals(expResult, result);
    }

    /**
     * Test of compareTo method, of class IntegerImpl.
     */
    @Test
    public void testCompareTo() {
        System.out.println("compareTo");
        IntegerType o = new IntegerImpl("8679");
        IntegerImpl instance = new IntegerImpl("6243");
        int result = instance.compareTo(o);
        assertTrue(result < 0);
    }

    /**
     * Test of sign method, of class IntegerImpl.
     */
    @Test
    public void testSign() {
        System.out.println("sign");
        IntegerImpl instance = new IntegerImpl("-77");
        Sign expResult = Sign.NEGATIVE;
        Sign result = instance.sign();
        assertEquals(expResult, result);

        instance = new IntegerImpl("8796");
        expResult = Sign.POSITIVE;
        result = instance.sign();
        assertEquals(expResult, result);
        
        instance = new IntegerImpl("0");
        expResult = Sign.ZERO;
        result = instance.sign();
        assertEquals(expResult, result);
    }
//
//    @Test
//    public void testStream() {
//        String expResult = "5877432";
//        IntegerImpl instance = new IntegerImpl(expResult);
////        Stream<Character> stream = instance.stream(10);
//        String result = instance.stream(10).collect(StringBuilder::new,
//                (x, y) -> x.insert(0, y), StringBuilder::append).toString();
//        assertEquals(expResult, result);
//    }
}
