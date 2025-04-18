package tungsten.types.functions.curvefit;
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

import tungsten.types.functions.NumericFunction;
import tungsten.types.functions.support.Coordinates;
import tungsten.types.numerics.RealType;

import java.util.List;

public interface CurveFittingStrategy {
    /**
     * Given a set of data points, generate a real-valued {@code NumericFunction}
     * that provides a closest fit according to this strategy.
     * @param dataPoints a list of {@code Coordinates} containing data
     * @return a function fitted to the input data
     */
    NumericFunction<RealType, RealType> fitToCoordinates(List<? extends Coordinates> dataPoints);

    /**
     * The type of data supported by this curve fitting strategy &mdash; i.e., whether this
     * strategy can handle data with a given number of dimensions.
     * @return the type of curve (2D, 3D, etc.) this strategy supports
     */
    CurveType supportedType();

    /**
     * Returns the name of the strategy. This name should be both human-readable and unique.
     * @return the name of this strategy
     */
    String name();
}
