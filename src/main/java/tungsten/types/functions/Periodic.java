package tungsten.types.functions;
/*
 * The MIT License
 *
 * Copyright © 2022 Robert Poole <Tarquin.AZ@gmail.com>.
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

import tungsten.types.Range;
import tungsten.types.numerics.RealType;

/**
 * An interface to be applied to periodic functions.
 * Implementing this interface is sufficient to identify that a function is periodic,
 * eliminating the need to spend computational resources figuring out whether a
 * function is periodic.
 * <br/>
 * The obvious danger is in mis-applying this interface to a non-periodic function.
 * Doing so may lead to incorrect behavior.
 */
public interface Periodic {
    /**
     * Returns the principal range of this periodic function.
     *
     * @return the range over which this function is primarily defined
     */
    Range<RealType> principalRange();

    /**
     * Returns the length of the period of this function.
     *
     * @return the period, typically equivalent to the upper bound minus the lower bound of the principal range
     */
    RealType period();
}
