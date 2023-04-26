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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tungsten.types.Numeric;
import tungsten.types.exceptions.CoercionException;
import tungsten.types.numerics.ComplexType;
import tungsten.types.numerics.IntegerType;
import tungsten.types.numerics.RationalType;
import tungsten.types.numerics.RealType;
import tungsten.types.util.MathUtils;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

import static org.junit.jupiter.api.Assertions.*;


/**
 *
 * @author tarquin
 */
public class PiTest {
    private static final String pi100 = "3.1415926535 8979323846 2643383279 5028841971 6939937510 5820974944 5923078164 0628620899 8628034825 3421170679";
    
    public PiTest() {
    }
    
    @BeforeEach
    public void setUp() {
    }
    
    @AfterEach
    public void tearDown() {
    }

    /**
     * Test of getInstance method, of class Pi.
     */
    @Test
    public void testGetInstance() {
        System.out.println("getInstance");
        MathContext mctx = new MathContext(8, RoundingMode.HALF_UP);
        BigDecimal expResult = new BigDecimal("3.1415927");
        Pi result = Pi.getInstance(mctx);
        System.out.println("Pi instance: " + result);
        String resultstr = result.asBigDecimal().toPlainString();
        System.out.println("Pi value = " + resultstr);
        assertEquals(0, result.asBigDecimal().compareTo(expResult));
        
        // now test for 100 digits
        mctx = new MathContext(100, RoundingMode.HALF_UP);
        final String piStr = pi100.replaceAll("\\s", "");  // strip whitespace
        System.out.println("pi100 string has " + (piStr.length() - 1) + " digits");
        expResult = new BigDecimal(piStr, mctx);
        result = Pi.getInstance(mctx);
        resultstr = result.asBigDecimal().toPlainString();
        String expResultStr = expResult.toPlainString();
        System.out.println("result string has " + (resultstr.length() - 1) + " digits");
        System.out.println("Testing for " + result);
        System.out.println("Expected ends with:\n" + expResultStr.substring(expResultStr.length() - 20));
        System.out.println("Actual ends with:\n" + resultstr.substring(resultstr.length() - 20));
        assertEquals(0, result.asBigDecimal().compareTo(expResult));
    }

    /**
     * Test of isCoercibleTo method, of class Pi.
     */
    @Test
    public void testIsCoercibleTo() {
        System.out.println("isCoercibleTo");
        Class<? extends Numeric> numtype = RealType.class;
        Pi instance = Pi.getInstance(MathContext.DECIMAL128);
        boolean expResult = true;
        boolean result = instance.isCoercibleTo(numtype);
        assertEquals(expResult, result);
        
        numtype = IntegerType.class;
        expResult = false;
        result = instance.isCoercibleTo(numtype);
        assertEquals(expResult, result);
        
        numtype = ComplexType.class;
        expResult = true;
        result = instance.isCoercibleTo(numtype);
        assertEquals(expResult, result);
    }

    /**
     * Test of coerceTo method, of class Pi.
     */
    @Test
    public void testCoerceTo() throws Exception {
        System.out.println("coerceTo");
        Class<? extends Numeric> numtype = RealType.class;
        Pi instance = Pi.getInstance(MathContext.DECIMAL64);
        Numeric result = instance.coerceTo(numtype);
        assertTrue(result instanceof RealType);
        
        numtype = ComplexType.class;
        result = instance.coerceTo(numtype);
        assertTrue(result instanceof ComplexType);
        
        numtype = RationalType.class;
        try {
            result = instance.coerceTo(numtype);
            fail("Should not be able to coerce Pi to a rational.");
        } catch (Exception e) {
            assertTrue(e instanceof CoercionException);
        }
    }

    /**
     * Test of add method, of class Pi.
     */
    @Test
    public void testAdd() {
        System.out.println("add");
        Pi instance = Pi.getInstance(MathContext.DECIMAL64);
        Numeric addend = instance;
        Numeric expResult = instance.multiply(new RealImpl(BigDecimal.valueOf(2L)));
        Numeric result = instance.add(addend);
        assertEquals(expResult, result);
    }

    /**
     * Test of subtract method, of class Pi.
     */
    @Test
    public void testSubtract() {
        System.out.println("subtract");
        Pi instance = Pi.getInstance(MathContext.DECIMAL64);
        Numeric subtrahend = instance.multiply(new RealImpl(BigDecimal.valueOf(2L)));
        Numeric expResult = instance.negate();
        Numeric result = instance.subtract(subtrahend);
        assertEquals(expResult, result);
    }

    /**
     * Test of multiply method, of class Pi.
     */
    @Test
    public void testMultiply() {
        System.out.println("multiply");
        Pi instance = Pi.getInstance(MathContext.DECIMAL64);
        RealType multiplier = (RealType) instance.inverse();
        RealType expResult = new RealImpl(BigDecimal.ONE, MathContext.DECIMAL64);
        RealType result = (RealType) instance.multiply(multiplier);
        assertTrue(MathUtils.areEqualToWithin(expResult, result, new RealImpl("0.0000000000000001")));
    }

    /**
     * Test of numberOfDigits method, of class Pi.
     */
    @Test
    public void testNumberOfDigits() {
        System.out.println("numberOfDigits");
        Pi instance = Pi.getInstance(MathContext.DECIMAL128);
        long expResult = 34L; // number of significant digits for DECIMAL128
        long result = instance.numberOfDigits();
        assertEquals(expResult, result);
    }

    /**
     * Test of compareTo method, of class Pi.
     */
    @Test
    public void testCompareTo() {
        System.out.println("compareTo");
        RealType three = new RealImpl(BigDecimal.valueOf(3L));
        RealType four = new RealImpl(BigDecimal.valueOf(4L));
        Pi instance = Pi.getInstance(MathContext.DECIMAL128);
        int result = instance.compareTo(three);
        assertTrue(result > 0);
        result = instance.compareTo(four);
        assertTrue(result < 0);
    }

}
