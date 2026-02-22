package tungsten.types.vector.impl;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tungsten.types.Axis;
import tungsten.types.Matrix;
import tungsten.types.Vector;
import tungsten.types.numerics.RealType;
import tungsten.types.numerics.Sign;
import tungsten.types.numerics.impl.Pi;
import tungsten.types.numerics.impl.RealImpl;
import tungsten.types.numerics.impl.Zero;
import tungsten.types.util.AngularDegrees;
import tungsten.types.util.MathUtils;
import tungsten.types.vector.ColumnVector;
import tungsten.types.vector.RowVector;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class VectorTest {
    RealType a = new RealImpl("3.0", MathContext.DECIMAL128);
    RealType b = new RealImpl("4.0", MathContext.DECIMAL128);
    RealType c = new RealImpl("5.0", MathContext.DECIMAL128);
    RealType zero = new RealImpl(BigDecimal.ZERO, MathContext.DECIMAL128);
    RealType two  = new RealImpl("2.0", MathContext.DECIMAL128);
    RealVector diagonal_I;

    public VectorTest() {
    }

    @BeforeEach
    public void setUp() {
        diagonal_I = new RealVector(List.of(a, b));
    }

    @AfterEach
    public void tearDown() {
    }

    @Test
    public void checkAddition() {
        RealVector leg1 = new RealVector(List.of(a, zero));
        RealVector leg2 = new RealVector(List.of(zero, b));
        Vector<RealType> sum = leg1.add(leg2);
        assertEquals(c, sum.magnitude());
        assertEquals(diagonal_I, sum);
        AngularDegrees degrees = new AngularDegrees("53.13°");
        // the computed angle should agree within 4 or 5 decimal places
        MathContext common = new MathContext(4);
//        System.out.println("Expected angle in radians: " + degrees.asRadians());
//        System.out.println("Computed angle between " + sum + " and " + leg1 + " is " + sum.computeAngle(leg1));
        assertEquals(MathUtils.round(degrees.asRadians(), common),
                MathUtils.round(sum.computeAngle(leg1), common));
    }

    @Test
    public void checkQuadrantsAxes() {
        RealVector diagonal_II = new RealVector(List.of(a.negate(), b));
        Vector<RealType> sum = diagonal_I.add(diagonal_II);
        assertTrue(Zero.isZero(sum.elementAt(0L)));
        assertEquals(b.multiply(two), sum.elementAt(1L));
        assertTrue(MathUtils.isAlignedWith(sum, Axis.Y_AXIS));

        Vector<RealType> diagonal_III = diagonal_I.negate();
        assertEquals(2L, diagonal_III.length());
        for (long k = 0; k < diagonal_III.length(); k++) {
            assertSame(Sign.NEGATIVE, diagonal_III.elementAt(k).sign());
        }
        Vector<RealType> diff = diagonal_I.add(diagonal_III);
        assertTrue(ZeroVector.isZeroVector(diff), "Adding a vector to its negation should = 0\u20D7");
    }

    @Test
    public void testRotation() {
        RealType angle = (RealType) Pi.getInstance(MathContext.DECIMAL128).multiply(a).divide(two);
        System.out.println("Using rotation angle " + angle);
        Matrix<RealType> matrix = MathUtils.get2DMatrixOfRotation(angle);
        RowVector<RealType> src = new ArrayRowVector<>(diagonal_I);
        Matrix<RealType> result = src.multiply(matrix); // normally, left-multiplication is non-standard
        assertEquals(1L, result.columns());
        assertEquals(2L, result.rows());
        ColumnVector<RealType> col = result.getColumn(0L);
        System.out.println("Column vector result: " + col);

        Matrix<RealType> result2 = matrix.multiply(src.transpose());  // now do the same in standard column form
        ColumnVector<RealType> col2 = result2.getColumn(0L);
        System.out.println("Column vector result 2: " + col2);
        assertEquals(MathUtils.round(diagonal_I.elementAt(1L), MathContext.DECIMAL64),
                MathUtils.round(col2.elementAt(0L), MathContext.DECIMAL64));
        assertEquals(MathUtils.round(diagonal_I.elementAt(0L).negate(), MathContext.DECIMAL64),
                MathUtils.round(col2.elementAt(1), MathContext.DECIMAL64));
        assertEquals(MathUtils.round(col.negate(), MathContext.DECIMAL64),
                MathUtils.round(col2, MathContext.DECIMAL64),
                "Result of left-multiplication is negative of right-multiplication.");
    }
}
