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

package tungsten.types.functions.curvefit;

import tungsten.types.Matrix;
import tungsten.types.functions.support.Coordinates2D;
import tungsten.types.matrix.impl.ColumnarMatrix;
import tungsten.types.matrix.impl.DiagonalMatrix;
import tungsten.types.numerics.IntegerType;
import tungsten.types.numerics.RealType;
import tungsten.types.numerics.impl.IntegerImpl;
import tungsten.types.util.MathUtils;
import tungsten.types.vector.ColumnVector;
import tungsten.types.vector.impl.ArrayColumnVector;

import java.math.BigInteger;
import java.util.List;

public class RegressionHelper {
    public static Matrix<RealType> designMatrixFor(List<Coordinates2D> data, int order) {
        if (order < 1) throw new IllegalArgumentException("Order of model must be at least 1 (linear)");
        ColumnarMatrix<RealType> X = new ColumnarMatrix<>();

        for (long k = 0L; k <= (long) order; k++) {
            final IntegerType exponent = new IntegerImpl(BigInteger.valueOf(k));
            RealType[] column = data.stream().map(Coordinates2D::getX)
                    .map(x -> MathUtils.computeIntegerExponent(x, exponent))
                    .toArray(RealType[]::new);
            X.append(new ArrayColumnVector<>(column));
        }

        return X;
    }

    public static ColumnVector<RealType> observedValuesFor(List<Coordinates2D> data) {
        RealType[] yvec = data.stream().map(Coordinates2D::getY).toArray(RealType[]::new);
        return new ArrayColumnVector<>(yvec);
    }

    public static DiagonalMatrix<RealType> weightMatrixFor(List<Coordinates2D> data) {
        RealType[] diag = data.stream().map(Coordinates2D::getSigma)
                .map(x -> MathUtils.computeIntegerExponent(x, -2L))
                .toArray(RealType[]::new);
        return new DiagonalMatrix<>(diag);
    }
}
