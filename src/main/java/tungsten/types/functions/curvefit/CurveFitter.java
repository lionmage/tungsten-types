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

import tungsten.types.Numeric;
import tungsten.types.exceptions.CoercionException;
import tungsten.types.functions.support.Coordinates;
import tungsten.types.functions.support.Coordinates2D;
import tungsten.types.functions.support.Coordinates3D;
import tungsten.types.numerics.RealType;
import tungsten.types.numerics.impl.IntegerImpl;
import tungsten.types.numerics.impl.RealImpl;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * This is the main entry point for users wishing to do curve fitting.
 * The class contains everything you need to look up and apply curve
 * fitting strategies.
 * @author Robert Poole, <a href="mailto:Tarquin.AZ@gmail.com">Gmail</a>
 *  or <a href="mailto:tarquin@alumni.mit.edu">MIT alumni e-mail</a>
 */
public class CurveFitter {
    final CurveType characteristic;
    List<? extends Coordinates> coordinates;
    final ServiceLoader<CurveFittingStrategy> loader;

    public CurveFitter(List<? extends Coordinates> coordinates, boolean skipIntegrityCheck) {
        if (coordinates == null) throw new IllegalArgumentException("Coordinates must be non-empty");
        this.coordinates = coordinates;
        if (coordinates.parallelStream().allMatch(Coordinates2D.class::isInstance)) {
            characteristic = CurveType.CURVE_2D;
        } else if (coordinates.parallelStream().allMatch(Coordinates3D.class::isInstance)) {
            characteristic = CurveType.CURVE_3D;
        } else {
            characteristic = CurveType.MULTI;
        }
        if (!skipIntegrityCheck && !checkIntegrity()) {
            throw new IllegalArgumentException("Coordinates failed integrity check");
        }
        // initialize the service loader
        try {
            loader = ServiceLoader.load(CurveFittingStrategy.class);
        } catch (ServiceConfigurationError fatal) {
            Logger.getLogger(CurveFitter.class.getName()).log(Level.SEVERE,
                    "While attempting to instantiate a ServiceLoader<CurveFittingStrategy>", fatal);
            throw new IllegalStateException(fatal);
        }
    }

    public CurveFitter(List<? extends Coordinates> coordinates) {
        this(coordinates, false);
    }

    private boolean checkIntegrity() {
        if (coordinates.size() < 2) return false;
        if (characteristic == CurveType.CURVE_2D) {
            // check that there are no duplicate X values
            TreeSet<RealType> values = new TreeSet<>();
            return coordinates.parallelStream().map(Coordinates2D.class::cast)
                    .map(Coordinates2D::getX).allMatch(values::add);
        }
        return true;
    }

    public void sortDataBy(int ordinate) {
        List<? extends Coordinates> result = coordinates.stream()
                .sorted(Comparator.comparing((Coordinates x) -> x.getOrdinate(ordinate)))
                .collect(Collectors.toList());
        assert result.size() == coordinates.size();
        Logger.getLogger(CurveFitter.class.getName()).log(Level.FINE,
                "Sorted {0} coordinates by ordinate @{1}",
                new Object[] {coordinates.size(), ordinate});
        coordinates = result;
    }

    public void sortInX() {
        sortDataBy(0);
    }

    public void sortInY() {
        if (characteristic != CurveType.CURVE_2D) {
            sortDataBy(1);
        } else {
            throw new IllegalStateException("2D curves cannot be sorted in Y");
        }
    }

    public RealType minOrdinateValue(int ordinate) {
        return coordinates.parallelStream().map(x -> x.getOrdinate(ordinate)).min(RealType::compareTo).orElseThrow();
    }

    public RealType maxOrdinateValue(int ordinate) {
        return coordinates.parallelStream().map(x -> x.getOrdinate(ordinate)).max(RealType::compareTo).orElseThrow();
    }

    /**
     * Return the {@link Coordinates} of the lowest-valued datum.
     * @return the coordinates with the lowest value
     */
    public Coordinates minValueAt() {
        return coordinates.parallelStream().min(Comparator.comparing(Coordinates::getValue)).orElseThrow();
    }

    /**
     * Return the {@link Coordinates} of the highest-valued datum.
     * @return the coordinates with the highest value
     */
    public Coordinates maxValueAt() {
        return coordinates.parallelStream().max(Comparator.comparing(Coordinates::getValue)).orElseThrow();
    }

    /**
     * Sort coordinates by multiple constraints, in a given order.
     * @param ordinates the ordinate index values, in the order in which the ordinates
     *                  are to be sorted
     */
    public void sortByMultipleConstraintsInOrder(Integer... ordinates) {
        if (ordinates == null || ordinates.length == 0) throw new IllegalArgumentException("ordinates must not be empty");
        Comparator<Coordinates> cmp = Comparator.comparing((Coordinates x) -> x.getOrdinate(ordinates[0]));
        for (int k = 1; k < ordinates.length; k++) {
            final int finalK = k;
            cmp = cmp.thenComparing((Coordinates x) -> x.getOrdinate(ordinates[finalK]));
        }
        List<? extends Coordinates> result = coordinates.stream()
                .sorted(cmp)
                .collect(Collectors.toList());
        assert result.size() == coordinates.size();
        Logger.getLogger(CurveFitter.class.getName()).log(Level.FINE,
                "Sorted {0} coordinates by ordinate indices {1}",
                new Object[] {coordinates.size(), ordinates});
        coordinates = result;
    }

    /**
     * Takes a {@link List} of raw coordinate data, potentially multidimensional
     * and potentially containing multiple values for a given ordinate, and reduces
     * it to a 2-dimensional set of coordinates.  Multiple values associated with
     * a given ordinate are averaged, and the standard deviation is used as the
     * relative error.
     * @param raw      the raw coordinate data
     * @param ordinate the index of the ordinate to reduce by
     * @return a {@link List} of {@link Coordinates2D} representing the reduced data set
     */
    public static List<Coordinates2D> reduce(List<? extends Coordinates> raw, int ordinate) {
        TreeMap<RealType, List<RealType>> reductionMap = new TreeMap<>();
        raw.parallelStream().forEach(x -> {
            if (reductionMap.containsKey(x.getOrdinate(ordinate))) {
                reductionMap.get(x.getOrdinate(ordinate)).add(x.getValue());
            } else {
                reductionMap.put(x.getOrdinate(ordinate), arrayListOf(x.getValue()));
            }
        });
        // now reduce the original data to a set with averaged values and std dev as the error
        List<Coordinates2D> reduced = new ArrayList<>(reductionMap.size());
        reductionMap.forEach((x, yvals) -> {
            Coordinates2D coord;
            if (yvals.size() == 1) {
                coord = new Coordinates2D(x, yvals.get(0), new RealImpl(BigDecimal.ZERO));
            } else {
                List<RealType> meanAndStdev = computeMeanAndStdDev(yvals);
                coord = new Coordinates2D(x, meanAndStdev.get(0), meanAndStdev.get(1));
            }
            reduced.add(coord);
        });
        return reduced;
    }

    private static List<RealType> arrayListOf(RealType value) {
        List<RealType> list = new ArrayList<>();
        list.add(value);
        return list;
    }

    private static List<RealType> computeMeanAndStdDev(List<RealType> values) {
        final RealType zero = new RealImpl(BigDecimal.ZERO, values.get(0).getMathContext());
        IntegerImpl populationSize = new IntegerImpl(BigInteger.valueOf(values.size()));
        final Numeric mean = values.parallelStream().map(Numeric.class::cast).reduce(zero, Numeric::add)
                .divide(populationSize);
        Numeric variance = values.parallelStream().map(Numeric.class::cast).map(x -> x.subtract(mean))
                .map(x -> x.multiply(x)).reduce(zero, Numeric::add).divide(populationSize);
        try {
            return List.of((RealType) mean.coerceTo(RealType.class), (RealType) variance.sqrt().coerceTo(RealType.class));
        } catch (CoercionException e) {
            Logger.getLogger(CurveFitter.class.getName()).log(Level.SEVERE,
                    "Unable to coerce mean={0} and variance={1} to real values given a population of size {2}.",
                    new Object[] {mean, variance, populationSize});
            throw new IllegalStateException(e);
        }
    }
}
