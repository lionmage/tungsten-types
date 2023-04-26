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
package tungsten.types.numerics;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tungsten.types.Numeric;
import tungsten.types.numerics.impl.IntegerImpl;

import static org.junit.jupiter.api.Assertions.*;


/**
 *
 * @author tarquin
 */
public class NumericHierarchyTest {
    
    public NumericHierarchyTest() {
    }
    
    @BeforeEach
    public void setUp() {
    }
    
    @AfterEach
    public void tearDown() {
    }

    /**
     * Test of values method, of class NumericHierarchy.
     */
    @Test
    public void testValues() {
        System.out.println("values");
        NumericHierarchy[] result = NumericHierarchy.values();
        assertEquals(4, result.length);
        assertEquals(result[0], NumericHierarchy.INTEGER);
        // and test ordering
        int comp = NumericHierarchy.REAL.compareTo(NumericHierarchy.RATIONAL);
        assertTrue(comp > 0); // REAL comes after RATIONAL
        comp = NumericHierarchy.INTEGER.compareTo(NumericHierarchy.COMPLEX);
        assertTrue(comp < 0); // INTEGER comes before COMPLEX
    }

    /**
     * Test of valueOf method, of class NumericHierarchy.
     */
    @Test
    public void testValueOf() {
        System.out.println("valueOf");
        String name = "REAL";
        NumericHierarchy result = NumericHierarchy.valueOf(name);
        assertEquals(NumericHierarchy.REAL, result);
        // also test nonsense name
        name = "FLOPPITY";
        try {
            result = NumericHierarchy.valueOf(name);
            fail("valueOf() should not return a result for bogus value");
        } catch (Exception e) {
            assertTrue(e instanceof IllegalArgumentException);
        }
    }

    /**
     * Test of getNumericType method, of class NumericHierarchy.
     */
    @Test
    public void testGetNumericType() {
        System.out.println("getNumericType");
        NumericHierarchy instance = NumericHierarchy.INTEGER;
        Class<? extends Numeric> expResult = IntegerType.class;
        Class<? extends Numeric> result = instance.getNumericType();
        assertEquals(expResult, result);
    }

    /**
     * Test of forNumericType method, of class NumericHierarchy.
     */
    @Test
    public void testForNumericType() {
        System.out.println("forNumericType");
        Class<? extends Numeric> clazz = IntegerType.class;
        NumericHierarchy expResult = NumericHierarchy.INTEGER;
        NumericHierarchy result = NumericHierarchy.forNumericType(clazz);
        assertEquals(expResult, result);
        // and test for actual class instances
        IntegerImpl testval = new IntegerImpl("887554");
        clazz = testval.getClass();
        result = NumericHierarchy.forNumericType(clazz);
        assertEquals(expResult, result, "Implementation class should give same results");
    }
    
}
