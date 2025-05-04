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
import tungsten.types.functions.impl.ConstantTerm;
import tungsten.types.functions.impl.PolyTerm;
import tungsten.types.functions.impl.Polynomial;
import tungsten.types.functions.support.Coordinates;
import tungsten.types.numerics.RealType;
import tungsten.types.vector.ColumnVector;

import java.util.List;

@StrategySupports(name = "multidimensional fit", type = CurveType.MULTI)
public class MultiDimensionalStrategy implements CurveFittingStrategy {
    @Override
    public NumericFunction<RealType, RealType> fitToCoordinates(List<? extends Coordinates> dataPoints) {
        final List<Coordinates> data = (List<Coordinates>) dataPoints;
        Matrix<RealType> X = RegressionHelper.designMatrixForMulti(data);
        ColumnVector<RealType> Y = RegressionHelper.observedValuesForMulti(data);
        Matrix<RealType> beta = RegressionHelper.realPseudoInverse(X).multiply(Y);
        Polynomial<RealType, RealType> result = new Polynomial<>(new ConstantTerm<>(beta.valueAt(0L, 0L)));
        for (long k = 1L; k < beta.rows(); k++) {
            String varName = "x" + k;
            result.add(new PolyTerm<>(varName, beta.valueAt(k, 0L), 1L));
        }
        return result;
    }

    @Override
    public CurveType supportedType() {
        return CurveType.MULTI;
    }

    @Override
    public String name() {
        return "Linear regression for multidimensional data";
    }
}
