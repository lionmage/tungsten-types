package tungsten.types.functions.curvefit.strategies;
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

import tungsten.types.Range;
import tungsten.types.functions.UnaryFunction;
import tungsten.types.functions.curvefit.CurveFittingStrategy;
import tungsten.types.functions.curvefit.CurveType;
import tungsten.types.functions.impl.CubicSpline2D;
import tungsten.types.functions.impl.RealPiecewiseFunction;
import tungsten.types.functions.support.Coordinates;
import tungsten.types.functions.support.Coordinates2D;
import tungsten.types.numerics.RealType;
import tungsten.types.numerics.impl.RealImpl;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.List;
import java.util.stream.Collectors;

public class CubicSplineStrategy implements CurveFittingStrategy {
    @Override
    public UnaryFunction<RealType, RealType> fitToCoordinates(List<? extends Coordinates> dataPoints) {
        if (dataPoints == null || dataPoints.get(0).arity() != 1L) {
            throw new IllegalArgumentException("Incorrect dimension for data");
        }
        final MathContext ctx = dataPoints.get(0).getValue().getMathContext();
        final RealType THREE = new RealImpl(BigDecimal.valueOf(3L), ctx);
        final RealType TWO   = new RealImpl(BigDecimal.valueOf(2L), ctx);
        List<Coordinates2D> C = toSupportedCoordinates(dataPoints);
        RealType[] a = new RealType[dataPoints.size()];
        for (int i = 0; i < a.length; i++) a[i] = C.get(i).getY();
        RealType[] b = new RealType[dataPoints.size() - 1];
        RealType[] d = new RealType[dataPoints.size() - 1];
        RealType[] h = new RealType[dataPoints.size() - 1];
        for (int i = 0; i < h.length; i++) h[i] = (RealType) C.get(i + 1).getX().subtract(C.get(i).getX());
        RealType[] alpha = new RealType[dataPoints.size() - 1];
        for (int i = 1; i < alpha.length; i++) {
            alpha[i] = (RealType) THREE.divide(h[i]).multiply(a[i + 1].subtract(a[i]))
                    .subtract(THREE.divide(h[i - 1]).multiply(a[i].subtract(a[i - 1])));
        }
        RealType[] c = new RealType[dataPoints.size()];
        RealType[] l = new RealType[dataPoints.size()];
        RealType[] mu = new RealType[dataPoints.size()];
        RealType[] z  = new RealType[dataPoints.size()];
        l[0] = new RealImpl(BigDecimal.ONE, ctx);
        mu[0] = new RealImpl(BigDecimal.ZERO, ctx);
        z[0] = mu[0];
        for (int i = 1; i < dataPoints.size() - 1; i++) {
            l[i] = (RealType) TWO.multiply(C.get(i + 1).getX().subtract(C.get(i - 1).getX()))
                    .subtract(h[i - 1].multiply(mu[i - 1]));
            mu[i] = (RealType) h[i].divide(l[i]);
            z[i] = (RealType) alpha[i].subtract(h[i - 1].multiply(z[i - 1])).divide(l[i]);
        }
        l[l.length - 1] = new RealImpl(BigDecimal.ONE, ctx);
        mu[mu.length - 1] = new RealImpl(BigDecimal.ZERO, ctx);
        z[z.length - 1] = mu[mu.length - 1];
        for (int j = dataPoints.size() - 2; j >= 0; j--) {
            c[j] = (RealType) z[j].subtract(mu[j].multiply(c[j + 1]));
            b[j] = (RealType) a[j + 1].subtract(a[j]).divide(h[j])
                    .subtract(h[j].multiply(c[j + 1].add(TWO.multiply(c[j]))).divide(THREE));
            d[j] = (RealType) c[j + 1].subtract(c[j]).divide(THREE.multiply(h[j]));
        }
        RealType epsilon = new RealImpl(BigDecimal.TEN.pow(1 - ctx.getPrecision(), ctx)
                .divide(BigDecimal.valueOf(2L), ctx), ctx);
        RealPiecewiseFunction result = new RealPiecewiseFunction(epsilon);
        for (int i = 0; i < dataPoints.size() - 2; i++) {
            // final range for series is inclusive at terminus
            Range<RealType> pieceRange = new Range<>(C.get(i).getX(), Range.BoundType.INCLUSIVE,
                    C.get(i + 1).getX(), i == dataPoints.size() - 3 ? Range.BoundType.EXCLUSIVE : Range.BoundType.INCLUSIVE);
            result.addFunctionForRange(pieceRange, new CubicSpline2D(a[i], b[i], c[i], d[i], pieceRange));
        }
        if (!result.checkAggregateBounds()) {
            throw new IllegalStateException("Constructed piecewise function has inconsistent bounds");
        }
        return result;
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
        return "Cubic Splines for 2D Data";
    }
}
