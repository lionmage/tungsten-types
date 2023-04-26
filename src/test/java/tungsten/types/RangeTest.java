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
package tungsten.types;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tungsten.types.numerics.IntegerType;
import tungsten.types.numerics.impl.IntegerImpl;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 *
 * @author Robert Poole <Tarquin.AZ@gmail.com>
 */
public class RangeTest {
    private Range intrange;
    private Range halfOpen;
    
    public RangeTest() {
    }
    
    @BeforeEach
    public void setUp() {
        intrange = new Range(new IntegerImpl("3"), new IntegerImpl("7"), Range.BoundType.INCLUSIVE);
        // This range has an open (exclusive) lower bound, and a closed (inclusive) upper bound
        halfOpen = new Range(new IntegerImpl("0"), Range.BoundType.EXCLUSIVE, new IntegerImpl("10"), Range.BoundType.INCLUSIVE);
    }
    
    @AfterEach
    public void tearDown() {
    }

    /**
     * Test of contains method, of class Range.
     */
    @Test
    public void testContains() {
        System.out.println("contains");
        String[] rawvalues = {"0", "1", "3", "5", "7", "10"};
        List<IntegerImpl> values = Arrays.stream(rawvalues).sequential().map(IntegerImpl::new).collect(Collectors.toList());
        final Range instance = intrange;
        Boolean[] rawresults = {false, false, true, true, true, false};
        List<Boolean> expResults = Arrays.stream(rawresults).collect(Collectors.toList());
        List<Boolean> results = values.stream().map(x -> instance.contains(x)).collect(Collectors.toList());
        assertEquals(expResults, results);
        
        final Range instance2 = halfOpen;
        Boolean[] rawresults2 = {false, true, true, true, true, true};
        expResults = Arrays.stream(rawresults2).collect(Collectors.toList());
        results = values.stream().map(x -> instance2.contains(x)).collect(Collectors.toList());
        assertEquals(expResults, results);
    }

    /**
     * Test of toString method, of class Range.
     */
    @Test
    public void testToString() {
        System.out.println("toString");
        Range instance = intrange;
        String expResult = "[3, 7]";
        String result = instance.toString();
        assertEquals(expResult, result);
        instance = halfOpen;
        expResult = "(0, 10]";
        result = instance.toString();
        assertEquals(expResult, result);
    }

    /**
     * Test of isBelow method, of class Range.
     */
    @Test
    public void testIsBelow() {
        System.out.println("isBelow");
        IntegerType val = new IntegerImpl("1");
        Range instance = intrange;
        boolean expResult = true;
        boolean result = instance.isBelow(val);
        assertEquals(expResult, result);
    }

    /**
     * Test of isAbove method, of class Range.
     */
    @Test
    public void testIsAbove() {
        System.out.println("isAbove");
        IntegerType val = new IntegerImpl("11");
        Range instance = intrange;
        boolean expResult = true;
        boolean result = instance.isAbove(val);
        assertEquals(expResult, result);
    }
    
}
