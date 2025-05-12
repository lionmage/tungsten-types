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
import tungsten.types.functions.UnaryFunction;
import tungsten.types.functions.curvefit.CurveFittingStrategy;
import tungsten.types.functions.curvefit.CurveType;
import tungsten.types.functions.impl.Const;
import tungsten.types.functions.impl.Exp;
import tungsten.types.functions.impl.Product;
import tungsten.types.functions.impl.Reflexive;
import tungsten.types.functions.support.Coordinates;
import tungsten.types.functions.support.Coordinates2D;
import tungsten.types.matrix.impl.ColumnarMatrix;
import tungsten.types.numerics.RealType;
import tungsten.types.numerics.Sign;
import tungsten.types.numerics.impl.Euler;
import tungsten.types.util.MathUtils;
import tungsten.types.vector.ColumnVector;
import tungsten.types.vector.impl.ArrayColumnVector;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * A strategy for fitting a set of data points to an exponential curve
 * of the form y&nbsp;=&nbsp;Ae<sup>Bx</sup>.
 * @author Robert Poole, <a href="mailto:tarquin@alum.mit.edu">MIT alumni e-mail</a>
 *   or <a href="mailto:Tarquin.AZ@gmail.com">Gmail</a>
 * @see <a href="https://mathworld.wolfram.com/LeastSquaresFittingExponential.html">An article at Wolfram Mathworld</a>
 * @see <a href="https://math.stackexchange.com/questions/350754/fitting-exponential-curve-to-data">This Math StackExchange article</a>
 */
@StrategySupports(name = "exponential fit", type = CurveType.CURVE_2D)
public class ExponentialFit implements CurveFittingStrategy {
    @Override
    public NumericFunction<RealType, RealType> fitToCoordinates(List<? extends Coordinates> dataPoints) {
        if (dataPoints.parallelStream().map(Coordinates::getValue).map(RealType::sign).anyMatch(s -> s == Sign.NEGATIVE)) {
            throw new ArithmeticException("Negative values are unsupported");
        }
        List<Coordinates2D> C = toSupportedCoordinates(dataPoints);
        // to solve, take the logarithm of both sides
        // ln(y) = ln(A) + Bx
        // the code below assumes A = exp(a) and B = b
        RealType ysum = C.parallelStream().map(Coordinates2D::getY)
                .reduce((y1, y2) -> (RealType) y1.add(y2)).orElseThrow();
        RealType xysum = C.parallelStream().map(c -> (RealType) c.getX().multiply(c.getY()))
                .reduce((xy1, xy2) -> (RealType) xy1.add(xy2)).orElseThrow();
        RealType xxysum = C.parallelStream().map(c -> (RealType) c.getX().multiply(c.getX()).multiply(c.getY()))
                .reduce((xxy1, xxy2) -> (RealType) xxy1.add(xxy2)).orElseThrow();
        Matrix<RealType> X = designMatrixFrom(ysum, xysum, xxysum);
        RealType ylny = C.parallelStream().map(Coordinates2D::getY)
                .map(y -> (RealType) y.multiply(MathUtils.ln(y)))
                .reduce((lny1, lny2) -> (RealType) lny1.add(lny2)).orElseThrow();
        RealType xylny = C.parallelStream()
                .map(c -> (RealType) c.getX().multiply(c.getY()).multiply(MathUtils.ln(c.getY())))
                .reduce((lny1, lny2) -> (RealType) lny1.add(lny2)).orElseThrow();
        ColumnVector<RealType> Y = new ArrayColumnVector<>(ylny, xylny);
        Matrix<RealType> ab = ((Matrix<RealType>) X.inverse()).multiply(Y);
        if (ab.columns() != 1L || ab.rows() != 2L) {
            Logger.getLogger(ExponentialFit.class.getName()).log(Level.WARNING,
                    "Expected a 2\u00D71 matrix, but received {0}\u00D7{1} instead.",
                    new Object[] { ab.rows(), ab.columns() });
        }
        return generateExponential(ab);
    }

    private Matrix<RealType> designMatrixFrom(RealType ysum, RealType xysum, RealType xxysum) {
        ColumnVector<RealType> col0 = new ArrayColumnVector<>(ysum, xysum);
        ColumnVector<RealType> col1 = new ArrayColumnVector<>(xysum, xxysum);
        return new ColumnarMatrix<>(List.of(col0, col1));
    }

    private UnaryFunction<RealType, RealType> generateExponential(Matrix<RealType> ab) {
        RealType a = ab.valueAt(0L, 0L);
        RealType b = ab.valueAt(1L, 0L);
        final Euler e = Euler.getInstance(a.getMathContext());
        Const<RealType, RealType> A = Const.getInstance(e.exp(a));
        Const<RealType, RealType> B = Const.getInstance(b);
        Logger.getLogger(ExponentialFit.class.getName()).log(Level.INFO,
                "Fitting to exponential curve with A={0}, B={1}.",
                new Object[] { A, B });

        Product<RealType, RealType> innerProd = new Product<>(B, new Reflexive<>(RealType.class));
        Exp exp = new Exp(innerProd);
        return new Product<>(A, exp);
    }

    private List<Coordinates2D> toSupportedCoordinates(List<? extends Coordinates> coords) {
        if (coords.stream().allMatch(Coordinates2D.class::isInstance)) return (List<Coordinates2D>) coords;
        return coords.stream().map(C -> new Coordinates2D(C.getOrdinate(0), C.getValue()))
                .collect(Collectors.toList());
    }

    @Override
    public CurveType supportedType() {
        return CurveType.CURVE_2D;
    }

    @Override
    public String name() {
        return "Exponential fit using least squares";
    }
}
