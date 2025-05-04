/*
 * The MIT License
 *
 * Copyright © 2025 Robert Poole <Tarquin.AZ@gmail.com>.
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
 *
 */

package tungsten.types.functions.curvefit.strategies;

import tungsten.types.Matrix;
import tungsten.types.annotations.StrategySupports;
import tungsten.types.functions.NumericFunction;
import tungsten.types.functions.curvefit.CurveFittingStrategy;
import tungsten.types.functions.curvefit.CurveType;
import tungsten.types.functions.curvefit.RegressionHelper;
import tungsten.types.functions.impl.Polynomial;
import tungsten.types.functions.support.Coordinates;
import tungsten.types.functions.support.Coordinates2D;
import tungsten.types.numerics.RealType;
import tungsten.types.vector.ColumnVector;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A weighted least-squares strategy for fitting a set of data to a quadratic (parabolic) function.
 * @since 0.6
 * @author Robert Poole, <a href="mailto:Tarquin.AZ@gmail.com">Gmail</a>
 */
@StrategySupports(name = "weighted parabolic fit", type = CurveType.CURVE_2D)
public class WeightedParabolicFitStrategy implements CurveFittingStrategy {
    @Override
    public NumericFunction<RealType, RealType> fitToCoordinates(List<? extends Coordinates> dataPoints) {
        if (dataPoints == null || dataPoints.get(0).arity() != 1L) {
            throw new IllegalArgumentException("Incorrect dimension for data");
        }
        List<Coordinates2D> C = toSupportedCoordinates(dataPoints);
        Matrix<RealType> X = RegressionHelper.designMatrixFor(C, 2);  // quadratic
        ColumnVector<RealType> Y = RegressionHelper.observedValuesFor(C);
        Matrix<RealType> W = RegressionHelper.weightMatrixFor(C);
        Matrix<RealType> beta = RegressionHelper.weightedPseudoInverse(X, W).multiply(Y);
        if (beta.columns() != 1L || beta.rows() != 3L) {
            Logger.getLogger(WeightedParabolicFitStrategy.class.getName()).log(Level.WARNING,
                    "Expected a 3\u00D71 result, but received {0}\u00D7{1} instead.",
                    new Object[] {beta.rows(), beta.columns()});
        }
        return new Polynomial<>("x",
                beta.valueAt(0L, 0L), beta.valueAt(1L, 0L), beta.valueAt(2L, 0L));
    }

    private List<Coordinates2D> toSupportedCoordinates(List<? extends Coordinates> coords) {
        if (coords.stream().allMatch(Coordinates2D.class::isInstance)) return (List<Coordinates2D>) coords;
        List<Coordinates2D> result = new ArrayList<>();
        for (Coordinates C : coords) {
            Coordinates2D converted = new Coordinates2D(C.getOrdinate(0), C.getValue());
            converted.setSigma(C.getSigma());  // preserve the error
            result.add(converted);
        }
        return result;
    }

    @Override
    public CurveType supportedType() {
        return CurveType.CURVE_2D;
    }

    @Override
    public String name() {
        return "Weighted linear regression to a parabolic fit for 2D data";
    }
}
