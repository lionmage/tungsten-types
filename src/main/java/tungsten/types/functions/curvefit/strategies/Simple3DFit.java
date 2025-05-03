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
import tungsten.types.functions.Term;
import tungsten.types.functions.curvefit.CurveFittingStrategy;
import tungsten.types.functions.curvefit.CurveType;
import tungsten.types.functions.curvefit.RegressionHelper;
import tungsten.types.functions.impl.ConstantTerm;
import tungsten.types.functions.impl.PolyTerm;
import tungsten.types.functions.impl.Polynomial;
import tungsten.types.functions.support.Coordinates;
import tungsten.types.functions.support.Coordinates3D;
import tungsten.types.numerics.RealType;
import tungsten.types.vector.ColumnVector;

import java.util.List;
import java.util.stream.Collectors;

/**
 * A strategy for fitting 3D data to a polynomial of the form
 * A + Bx + Cy + Dxy =&nbsp;z
 * @since 0.6
 * @author Robert Poole, <a href="mailto:Tarquin.AZ@gmail.com">Gmail</a>
 */
@StrategySupports(name = "simple 3D fit", type = CurveType.CURVE_3D)
public class Simple3DFit implements CurveFittingStrategy {
    @Override
    public NumericFunction<RealType, RealType> fitToCoordinates(List<? extends Coordinates> dataPoints) {
        if (dataPoints == null || dataPoints.get(0).arity() != 1L) {
            throw new IllegalArgumentException("Incorrect dimension for data");
        }
        List<Coordinates3D> coords = toSupportedCoordinates(dataPoints);
        Matrix<RealType> X = RegressionHelper.designMatrixFor3D(coords);  // A + Bx + Cy + Dxy
        ColumnVector<RealType> Y = RegressionHelper.observedValuesFor3D(coords);
        Matrix<RealType> beta = RegressionHelper.realPseudoInverse(X).multiply(Y);
        Term<RealType, RealType> A = new ConstantTerm<>(beta.valueAt(0, 0));
        Term<RealType, RealType> B = new PolyTerm<>("x", beta.valueAt(1, 0), 1L);
        Term<RealType, RealType> C = new PolyTerm<>("y", beta.valueAt(2, 0), 1L);
        Term<RealType, RealType> D = new PolyTerm<>(beta.valueAt(3, 0),
                List.of("x", "y"), List.of(1L, 1L));
        return new Polynomial<>(A, B, C, D);
    }

    private List<Coordinates3D> toSupportedCoordinates(List<? extends Coordinates> coords) {
        if (coords.stream().allMatch(Coordinates3D.class::isInstance)) return (List<Coordinates3D>) coords;
        return coords.stream().map(C -> new Coordinates3D(C.getOrdinate(0), C.getOrdinate(1), C.getValue()))
                .collect(Collectors.toList());
    }

    @Override
    public CurveType supportedType() {
        return CurveType.CURVE_3D;
    }

    @Override
    public String name() {
        return "Linear regression to a simple surface for 3D data";
    }
}
