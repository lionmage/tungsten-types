package tungsten.types;
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

import tungsten.types.numerics.RealType;

import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * A real-typed range that supports iteration.
 * @since 0.3
 * @author Robert Poole, <a href="mailto:tarquin@alum.mit.edu">MIT alumni e-mail</a>
 */
public class SteppedRange extends Range<RealType> implements Iterable<RealType> {
    private final RealType stepSize;

    public SteppedRange(RealType start, RealType end, BoundType endType, RealType stepSize) {
        super(start, BoundType.INCLUSIVE, end, endType);
        if (stepSize.compareTo((RealType) end.subtract(start)) > 0) {
            throw new IllegalArgumentException("stepSize > span from start to end");
        }
        this.stepSize = stepSize;
    }

    @Override
    public Iterator<RealType> iterator() {
        return new Iterator<>() {
            private RealType current = getLowerBound();
            private final RealType threshold = (RealType) getUpperBound().subtract(stepSize);

            @Override
            public boolean hasNext() {
                int comparison = current.compareTo(threshold);
                return isUpperClosed() ? comparison <= 0 : comparison < 0;
            }

            @Override
            public RealType next() {
                if (!hasNext()) {
                    throw new NoSuchElementException("At or beyond end of range");
                }
                RealType result = current;
                current = (RealType) current.add(stepSize);
                return result;
            }
        };
    }
}
