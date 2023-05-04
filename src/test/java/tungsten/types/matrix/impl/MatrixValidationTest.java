package tungsten.types.matrix.impl;

import org.junit.jupiter.api.Test;
import tungsten.types.Matrix;
import tungsten.types.Numeric;
import tungsten.types.numerics.IntegerType;
import tungsten.types.numerics.RealType;
import tungsten.types.numerics.impl.IntegerImpl;
import tungsten.types.numerics.impl.One;
import tungsten.types.numerics.impl.RealImpl;
import tungsten.types.vector.RowVector;
import tungsten.types.vector.impl.ArrayRowVector;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MatrixValidationTest {
    RealType zero = new RealImpl(BigDecimal.ZERO, MathContext.DECIMAL128);
    RealType one = new RealImpl(BigDecimal.ONE, MathContext.DECIMAL128);
    IntegerType two = new IntegerImpl(BigInteger.valueOf(2L), true);
    Matrix<RealType> antidiagonal;
    Matrix<IntegerType> W;  // Wilson matrix
    Matrix<IntegerType> Z;  // factorization of W
    // source: https://en.wikipedia.org/wiki/Wilson_matrix
    String[][] wilsonEntries = {
            {"5", "7", "6", "5"},
            {"7", "10", "8", "7"},
            {"6", "8", "10", "9"},
            {"5", "7", "9", "10"}
    };
    String[][] wilsonFactorEntries = {
            {"2", "3", "2", "2"},
            {"1", "1", "2", "1"},
            {"0", "0", "1", "2"},
            {"0", "0", "1", "1"}
    };

    public MatrixValidationTest() {
        RealType[][] src = new RealType[][] {{zero, one}, {one.negate(), zero}};
        antidiagonal = new BasicMatrix<>(src);
        IntegerType[][] iSrc = new IntegerType[4][4];
        IntegerType[][] iSrc2 = new IntegerType[4][4];
        for (int j = 0; j < wilsonEntries.length; j++) {  // row
            for (int k = 0; k < wilsonEntries[0].length; k++) { // column
                iSrc[j][k] = new IntegerImpl(wilsonEntries[j][k]);
                iSrc2[j][k] = new IntegerImpl(wilsonFactorEntries[j][k]);
            }
        }
        W = new BasicMatrix<>(iSrc);
        Z = new ColumnarMatrix<>(iSrc2);
    }

    @Test
    public void testSquareIdentity() {
        Matrix<? extends Numeric> expValue = new IdentityMatrix(2L, MathContext.DECIMAL128).scale(one.negate());
        Matrix<? extends Numeric> result = antidiagonal.multiply(antidiagonal);

        assertEquals(expValue, result);

        // now retest using matrix exponentiation
        result = antidiagonal.pow(two);
        assertEquals(expValue, result);
    }

    /**
     * Matrix tests related to the Wilson matrix, W.
     * @see <a href="https://en.wikipedia.org/wiki/Wilson_matrix">Wikipedia's article on the Wilson matrix</a>
     */
    @Test
    public void wilsonMatrix() {
        IntegerType det = W.determinant();
        assertTrue(One.isUnity(det), "The determinant of Wilson's matrix should be 1");
        // first row
        RowVector<IntegerType> testRow = new ArrayRowVector<>(new IntegerImpl("68"),
                new IntegerImpl("-41"), new IntegerImpl("-17"), new IntegerImpl("10"));
        // third row
        RowVector<IntegerType> testRow2 = new ArrayRowVector<>(new IntegerImpl("-17"),
                new IntegerImpl("10"), new IntegerImpl("5"), new IntegerImpl("-3"));
        Matrix<? extends Numeric> Winv = W.inverse();
        assertEquals(testRow, Winv.getRow(0L));
        assertEquals(testRow2, Winv.getRow(2L));
        // check the factorization
        assertEquals(W, Z.transpose().multiply(Z));
    }
}
