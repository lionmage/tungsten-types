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
import tungsten.types.numerics.RealType;
import tungsten.types.numerics.impl.IntegerImpl;
import tungsten.types.numerics.impl.RealImpl;

import java.math.MathContext;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 *
 * @author Robert Poole, <a href="mailto:Tarquin.AZ@gmail.com">Gmail</a>
 */
public class RangeTest {
    private Range<IntegerType> intrange;
    private Range<IntegerType> halfOpen;
    
    public RangeTest() {
    }
    
    @BeforeEach
    public void setUp() {
        intrange = new Range<>(new IntegerImpl("3"), new IntegerImpl("7"), Range.BoundType.INCLUSIVE);
        // This range has an open (exclusive) lower bound, and a closed (inclusive) upper bound
        halfOpen = new Range<>(new IntegerImpl("0"), Range.BoundType.EXCLUSIVE, new IntegerImpl("10"), Range.BoundType.INCLUSIVE);
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
        final Range<IntegerType> instance = intrange;
        Boolean[] rawresults = {false, false, true, true, true, false};
        List<Boolean> expResults = Arrays.stream(rawresults).collect(Collectors.toList());
        List<Boolean> results = values.stream().map(instance::contains).collect(Collectors.toList());
        assertEquals(expResults, results);
        
        final Range<IntegerType> instance2 = halfOpen;
        Boolean[] rawresults2 = {false, true, true, true, true, true};
        expResults = Arrays.stream(rawresults2).collect(Collectors.toList());
        results = values.stream().map(instance2::contains).collect(Collectors.toList());
        assertEquals(expResults, results);
    }

    /**
     * Test of toString method, of class Range.
     */
    @Test
    public void testToString() {
        System.out.println("toString");
        Range<IntegerType> instance = intrange;
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
        Range<IntegerType> instance = intrange;
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
        Range<IntegerType> instance = intrange;
        boolean expResult = true;
        boolean result = instance.isAbove(val);
        assertEquals(expResult, result);
    }

    @Test
    public void iteration() {
        System.out.println("iteration over various bounds");
        RealType low = new RealImpl("0.0", MathContext.DECIMAL32);
        RealType high = new RealImpl("10.0", MathContext.DECIMAL32);
        RealType step1 = new RealImpl("1.0", MathContext.DECIMAL32);
        SteppedRange range1 = new SteppedRange(low, high, Range.BoundType.EXCLUSIVE, step1);
        System.out.println("Range 1 = " + range1);
        int count = 0;
        for (RealType val : range1) count++;
        assertEquals(10, count);

        SteppedRange range2 = new SteppedRange(low, high, Range.BoundType.INCLUSIVE, step1);
        count = 0;
        for (RealType val : range2) count++;
        assertEquals(11, count);

        RealType step2 = new RealImpl("0.5", MathContext.DECIMAL32);
        SteppedRange range3 = new SteppedRange(low, high, Range.BoundType.INCLUSIVE, step2);
        count = 0;
        for (RealType val : range3) count++;
        assertEquals(21, count);

        // testing the Spliterator implementation
        assertEquals(21L, range3.parallelStream().count());
    }
}
