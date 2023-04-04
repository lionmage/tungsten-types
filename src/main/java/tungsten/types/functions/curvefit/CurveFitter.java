package tungsten.types.functions.curvefit;

import tungsten.types.functions.support.Coordinates;
import tungsten.types.functions.support.Coordinates2D;
import tungsten.types.functions.support.Coordinates3D;
import tungsten.types.numerics.RealType;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class CurveFitter {
    CurveType characteristic;
    List<? extends Coordinates> coordinates;

    public CurveFitter(List<? extends Coordinates> coordinates) {
        this.coordinates = coordinates;
        if (coordinates.parallelStream().allMatch(Coordinates2D.class::isInstance)) {
            characteristic = CurveType.CURVE_2D;
        } else if (coordinates.parallelStream().allMatch(Coordinates3D.class::isInstance)) {
            characteristic = CurveType.CURVE_3D;
        } else {
            characteristic = CurveType.MULTI;
        }
        if (!checkIntegrity()) {
            throw new IllegalArgumentException("Coordinates failed integrity check");
        }
    }

    private boolean checkIntegrity() {
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

    /**
     * Sort coordinates by multiple constraints, in a given order.
     * @param ordinates the ordinate index values, in the order in which the ordinates
     *                  are to be sorted
     */
    public void sortByMultipleConstraintsInOrder(Integer... ordinates) {
        if (ordinates == null || ordinates.length == 0) throw new IllegalArgumentException("ordinates must not be empty");
        Comparator<Coordinates> cmp = Comparator.comparing((Coordinates x) -> x.getOrdinate(ordinates[0]));
        for (int k = 1; k < ordinates.length; k++) {
            int finalK = k;
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
}
