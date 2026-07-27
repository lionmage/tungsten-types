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
import tungsten.types.functions.ArgMap;
import tungsten.types.functions.ArgVector;
import tungsten.types.functions.NumericFunction;
import tungsten.types.functions.support.Coordinates;
import tungsten.types.functions.support.Coordinates2D;
import tungsten.types.functions.support.Coordinates3D;
import tungsten.types.matrix.impl.ColumnarMatrix;
import tungsten.types.matrix.impl.DiagonalMatrix;
import tungsten.types.numerics.IntegerType;
import tungsten.types.numerics.RealType;
import tungsten.types.numerics.impl.IntegerImpl;
import tungsten.types.numerics.impl.RealImpl;
import tungsten.types.util.MathUtils;
import tungsten.types.vector.ColumnVector;
import tungsten.types.vector.impl.ArrayColumnVector;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A set of methods for assisting with doing a linear regression, polynomial regression,
 * or weighted least squares for fitting a curve to a set of data.
 * @author Robert Poole, <a href="mailto:tarquin@alum.mit.edu">MIT alumni e-mail</a>
 * @since 0.6
 */
public final class RegressionHelper {
    private RegressionHelper() {
        // no instantiation allowed
    }

    /**
     * Generate a design matrix for a set of X, Y data,
     * @param data  the {@link Coordinates2D} values in this data set
     * @param order the order of the polynomial we wish to model
     * @return a design matrix with <em>n</em> columns and <em>m</em> rows, where
     *   <em>n</em>&nbsp;=&nbsp;{@code order + 1} and <em>m</em>&nbsp;=&nbsp;{@code data.size()}
     */
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

    /**
     * Generate a design matrix from a set of X, Y, Z data.
     * This simple design matrix represents the expression
     * &beta;<sub>0</sub> + &beta;<sub>1</sub>x + &beta;<sub>2</sub>y + &beta;<sub>3</sub>xy =&nbsp;z
     * @param data the {@link Coordinates3D} values in this data set
     * @return a n&times;4 matrix, where n&nbsp;=&nbsp;{@code data.size()}
     */
    public static Matrix<RealType> designMatrixFor3D(List<Coordinates3D> data) {
        Stream<RealType> values = Stream.concat(data.stream().map(Coordinates3D::getX),
                data.stream().map(Coordinates3D::getY));
        final MathContext ctx = MathUtils.inferMathContext(values.collect(Collectors.toList()));
        ColumnarMatrix<RealType> X = new ColumnarMatrix<>();
        // columns are: offset (intercept), x, y, and xy
        RealType[] scratch = new RealType[data.size()];  // scratch buffer for the column vectors we create
        Arrays.fill(scratch, new RealImpl(BigDecimal.ONE, ctx));
        ColumnVector<RealType> offset = new ArrayColumnVector<>(scratch);
        for (int i = 0; i < data.size(); i++) scratch[i] = data.get(i).getX();
        ColumnVector<RealType> xvec = new ArrayColumnVector<>(scratch);
        for (int i = 0; i < data.size(); i++) scratch[i] = data.get(i).getY();
        ColumnVector<RealType> yvec = new ArrayColumnVector<>(scratch);
        for (int i = 0; i < data.size(); i++) {
            var x = data.get(i).getX();
            var y = data.get(i).getY();
            scratch[i] = (RealType) x.multiply(y);
        }
        ColumnVector<RealType> xyvec = new ArrayColumnVector<>(scratch);

        X.append(offset);
        X.append(xvec);
        X.append(yvec);
        X.append(xyvec);

        return X;
    }

    /**
     * Generate a design matrix from a set of multidimensional data.
     * The resulting design matrix is linear in each of the independent variables.
     * @param data the {@link Coordinates} values in this data set of arbitrary arity
     * @return a matrix with n + 1 columns where n is the arity of the data and as many rows as there are observations
     */
    public static Matrix<RealType> designMatrixForMulti(List<Coordinates> data) {
        final MathContext ctx = data.get(0).getOrdinate(0).getMathContext();
        ColumnarMatrix<RealType> X = new ColumnarMatrix<>();
        X.append(new ArrayColumnVector<>(Collections.nCopies(data.size(), new RealImpl(BigDecimal.ONE, ctx))));
        for (int k = 0; k < data.get(0).arity(); k++) {
            RealType[] scratch = new RealType[data.size()];
            for (int j = 0; j < data.size(); j++) {
                scratch[j] = data.get(j).getOrdinate(k);
            }
            X.append(new ArrayColumnVector<>(scratch));
        }
        return X;
    }

    /**
     * Given a set of X, Y data, obtain a column vector of dependent variables.
     * @param data a set of 2D data
     * @return the column vector representing the Y values
     */
    public static ColumnVector<RealType> observedValuesFor(List<Coordinates2D> data) {
        RealType[] yvec = data.stream().map(Coordinates2D::getY).toArray(RealType[]::new);
        return new ArrayColumnVector<>(yvec);
    }

    /**
     * Given a set of X, Y, Z data, obtain a column vector of dependent variables.
     * @param data a set of 3D data
     * @return the column vector representing the Z values
     */
    public static ColumnVector<RealType> observedValuesFor3D(List<Coordinates3D> data) {
        RealType[] zvec = data.stream().map(Coordinates3D::getZ).toArray(RealType[]::new);
        return new ArrayColumnVector<>(zvec);
    }

    /**
     * Given a set of multidimensional data, obtain a column vector of dependent variables.
     * @param data a set of multivariate data
     * @return the column vector representing the dependent variable values
     */
    public static ColumnVector<RealType> observedValuesForMulti(List<Coordinates> data) {
        RealType[] values = data.stream().map(Coordinates::getValue).toArray(RealType[]::new);
        return new ArrayColumnVector<>(values);
    }

    /**
     * Given a set of X, Y data with standard deviation values, construct a
     * weight matrix for use in weighted linear regression.  The weights
     * are the inverses of the variances.
     * @param data a set of 2D data
     * @return a diagonal matrix containing the weights
     */
    public static DiagonalMatrix<RealType> weightMatrixFor(List<Coordinates2D> data) {
        RealType[] diag = data.stream().map(Coordinates2D::getSigma)
                .map(x -> MathUtils.computeIntegerExponent(x, -2L))
                .toArray(RealType[]::new);
        return new DiagonalMatrix<>(diag);
    }

    /**
     * Similar to {@link MathUtils#pseudoInverse(Matrix)} but specifically designed to operate on
     * real-valued matrices, thus avoiding penalties for operating with complex matrices and
     * downconverting them.
     * @param design the design matrix to compute the pseudoinverse of, often denoted <strong>X</strong>
     * @return the pseudoinverse of {@code design}, calculated as (X<sup>T</sup>X)<sup>-1</sup>X<sup>T</sup>
     */
    public static Matrix<RealType> realPseudoInverse(Matrix<RealType> design) {
        final Matrix<RealType> transpose = design.transpose();
        Matrix<RealType> intermediate = (Matrix<RealType>) transpose.multiply(design).inverse();
        return intermediate.multiply(transpose);
    }

    /**
     * Compute a pseudoinverse with a weight matrix whose diagonal elements are 1/&sigma;<sup>2</sup>,
     * where &sigma; is the standard deviation of the dependent variable for its corresponding datum.
     * @param design  the design matrix to compute the pseudoinverse of, typically denoted <strong>X</strong>
     * @param weights the weight matrix, typically denoted <strong>W</strong>, with diagonal entries
     *                that are the inverses of variances
     * @return the pseudoinverse of {@code design} taking {@code weights} into account
     */
    public static Matrix<RealType> weightedPseudoInverse(Matrix<RealType> design, Matrix<RealType> weights) {
        final Matrix<RealType> transpose = design.transpose();
        Matrix<RealType> intermediate = (Matrix<RealType>) transpose.multiply(weights).multiply(design).inverse();
        return intermediate.multiply(transpose).multiply(weights);
    }

    /**
     * Obtain a subset of data samples.  One common use case for this is to take
     * a smaller set of data points to do some type of curve fitting or regression.
     * The data for the subset is chosen pseudo-randomly.
     * @param source the source data to subsample
     * @param size   the number of data samples to include in the subset
     * @return a reduced list of data points taken from {@code source}
     */
    public static List<Coordinates> subsample(List<Coordinates> source, int size) {
        if (size < 1) {
            throw new IllegalArgumentException("size must be a positive integer");
        }
        if (source.size() < size) {
            throw new IllegalArgumentException("source already has < " + size + " elements");
        }

        final Random rand = new Random();
        final TreeSet<Integer> indices = new TreeSet<>();
        while (indices.size() < size) {
            indices.add(rand.nextInt(source.size()));
        }
        return indices.stream().map(source::get).toList();
    }

    /**
     * Compute a deviation (&sigma; or error) value for a data set
     * given a fitted curve.  The resulting values (computed from the difference
     * between a datum's value and the function's evaluation at the datum's
     * ordinal values) are populated into each datum using {@link Coordinates#setSigma(RealType)}.
     * <br>One application of this is to use {@link #subsample(List, int)} to generate
     * a smaller set of data to then fit to a curve; this curve can be used to then
     * compute the deviations from the curve's predictions for the entire set of data.
     * @param data the {@code List} of data points, which is modified by this method
     * @param func the fitted curve for the data
     */
    public static void deviationFrom(List<Coordinates> data, NumericFunction<RealType, RealType> func) {
        String[] argNames = func.expectedArguments();
        for (Coordinates datum : data) {
            ArgMap<RealType> map = new ArgMap<>();
            for (int k = 0; k < argNames.length; k++) {
                map.put(argNames[k], datum.getOrdinate(k));
            }
            ArgVector<RealType> arg = new ArgVector<>(argNames, map);
            RealType expected = func.apply(arg);
            RealType actual = datum.getValue();
            datum.setSigma(actual.subtract(expected).magnitude());
        }
    }
}
