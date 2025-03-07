package tungsten.types.matrix.impl;

import org.junit.jupiter.api.Test;
import tungsten.types.Matrix;
import tungsten.types.Numeric;
import tungsten.types.numerics.IntegerType;
import tungsten.types.numerics.RationalType;
import tungsten.types.numerics.RealType;
import tungsten.types.numerics.impl.IntegerImpl;
import tungsten.types.numerics.impl.One;
import tungsten.types.numerics.impl.Pi;
import tungsten.types.numerics.impl.RealImpl;
import tungsten.types.util.MathUtils;
import tungsten.types.util.ingest.matrix.MatrixParser;
import tungsten.types.vector.RowVector;
import tungsten.types.vector.impl.ArrayRowVector;
import tungsten.types.vector.impl.IntVector;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static tungsten.types.util.UnicodeTextEffects.formatMatrixForDisplay;
import static tungsten.types.util.UnicodeTextEffects.vectorNameForDisplay;

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
    Matrix<RealType> test1, test2;

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
        // load the test matrices from text files
        MatrixParser<RealType> parser = new MatrixParser<>(MathContext.DECIMAL32, RealType.class);
        test1 = parser.read(getClass().getClassLoader().getResourceAsStream("test1.matrix"));
        test2 = parser.read(getClass().getClassLoader().getResource("test2.matrix"));
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
        System.out.println("Using Wilson matrix:\n" + formatMatrixForDisplay(W, (String) null, (String) null));
        IntegerType det = W.determinant();
        assertTrue(One.isUnity(det), "The determinant of Wilson's matrix should be 1");
        // first row
        RowVector<IntegerType> testRow = new ArrayRowVector<>(new IntegerImpl("68"),
                new IntegerImpl("-41"), new IntegerImpl("-17"), new IntegerImpl("10"));
        // third row
        RowVector<IntegerType> testRow2 = new ArrayRowVector<>(new IntegerImpl("-17"),
                new IntegerImpl("10"), new IntegerImpl("5"), new IntegerImpl("-3"));
        Matrix<? extends Numeric> Winv = W.inverse();
        System.out.println("Wilson inverse is:\n" + formatMatrixForDisplay(Winv, (String) null, (String) null));
        assertEquals(testRow, Winv.getRow(0L));
        assertEquals(testRow2, Winv.getRow(2L));
        // check the factorization
        assertEquals(W, Z.transpose().multiply(Z));
    }

    @Test
    public void matrixExponential() {
        System.out.println("Testing exp() for matrices");
        Pi pi = Pi.getInstance(MathContext.DECIMAL128);
        RealType[][] sample = {
                {zero, pi.negate()},
                {pi, zero}
        };

        Matrix<RealType> P = new BasicMatrix<>(sample);

        System.out.println("Initial matrix:");
        System.out.println(formatMatrixForDisplay(P, (String) null, (String) null));

        Matrix<? extends Numeric> R = MathUtils.exp(P);

        System.out.println("exp of matrix:");
        System.out.println(formatMatrixForDisplay(R, (String) null, (String) null));

        // ensure that P was not altered in any way
        Matrix<RealType> copP = new BasicMatrix<>(sample);
        assertEquals(copP, P, "Initial matrix argument should not be altered");

        Matrix<? extends Numeric> expValue = new IdentityMatrix(2L, MathContext.DECIMAL128).scale(one.negate());
        RealType epsilon = new RealImpl("0.00000001", MathContext.DECIMAL128);

        assertTrue(MathUtils.areEqualToWithin((Matrix<RealType>) expValue, (Matrix<RealType>) R, epsilon),
                "Matrix elements must be within \uD835\uDF00 of their expected value");
    }

    @Test
    public void cauchyMatrix() {
        System.out.println("Checking Cauchy matrix implementation");
        IntVector a = new IntVector(1L, 2L, 3L);
        a.setMathContext(MathContext.DECIMAL64);
        IntVector b = new IntVector(1L, 5L, 6L);
        b.setMathContext(MathContext.DECIMAL64);

        System.out.println(vectorNameForDisplay("a") + " = " + a);
        System.out.println(vectorNameForDisplay("b") + " = " + b);

        CauchyMatrix<RationalType, IntegerType> cm = new CauchyMatrix<>(a, b, RationalType.class);

        System.out.println("Cauchy Matrix:\n" + formatMatrixForDisplay(cm, (String) null, (String) null));

        BasicMatrix<RationalType> cm2 = new BasicMatrix<>(cm);

        RationalType det1 = cm.determinant();
        RationalType det2 = cm2.determinant();

        System.out.println("cm determinant is " + det1);
        assertEquals(det2, det1, "Cauchy determinant should equal regular matrix determinant");
//        System.out.println "cm2 determinant is " + det2

        Matrix<? extends Numeric> inv1 = cm.inverse();
        Matrix<? extends Numeric> inv2 = cm2.inverse();

        System.out.println("cm inverse is:\n" + formatMatrixForDisplay(inv1, (String) null, (String) null));
//        System.out.println "cm2 inverse is:\n" + formatMatrixForDisplay(inv2, null, null)
        assertEquals(inv2, inv1, "Cauchy inverse should equal regular matrix inverse");
    }

    @Test
    public void canWeMultiply() {
        System.out.println("Checking implementation of Strassen's algorithm using persisted matrices");
        long start = System.currentTimeMillis();
        Matrix<RealType> result1 = test1.multiply(test2);
        long end = System.currentTimeMillis();
        System.out.println("test multiply 1 took " + (end - start) + " ms");

        start = System.currentTimeMillis();
        Matrix<RealType> result2 = MathUtils.efficientMatrixMultiply(test1, test2);
        end = System.currentTimeMillis();
        System.out.println("test multiply 2 took " + (end - start) + " ms");

        System.out.println("\nResult 1:");
        System.out.println(formatMatrixForDisplay(result1, null, (String) null));
        System.out.println("\nResult 2:");
        System.out.println(formatMatrixForDisplay(result2, null, (String) null));

        RealType epsilon = new RealImpl("0.001", MathContext.DECIMAL32);
        assertTrue(MathUtils.areEqualToWithin(result1, result2, epsilon),
                "Matrix multiplication results must be within \uD835\uDF00 of each other");
    }

    @Test
    public void matrixSquareRoot() {
        DiagonalMatrix<RealType> diagMtx = new DiagonalMatrix<>(new RealImpl("4", MathContext.DECIMAL64),
                new RealImpl("9", MathContext.DECIMAL64));
        System.out.println(formatMatrixForDisplay(diagMtx, null, (String) null));
        Matrix<? extends Numeric> root1 = MathUtils.sqrt(diagMtx);
        System.out.println();
        System.out.println(formatMatrixForDisplay(root1, null, (String) null));

        RealType reTwo = new RealImpl("2", MathContext.DECIMAL64);
        assertEquals(reTwo, root1.valueAt(0L, 0L));
        RealType reThree = new RealImpl("3", MathContext.DECIMAL64);
        assertEquals(reThree, root1.valueAt(1L, 1L));

        RealType[][] seed = new RealType[][]{{new RealImpl("61", MathContext.DECIMAL64), new RealImpl("66", MathContext.DECIMAL64)},
                {new RealImpl("22", MathContext.DECIMAL64), new RealImpl("28", MathContext.DECIMAL64)}};
        Matrix<RealType> testMtx1 = new BasicMatrix<>(seed);

        System.out.println();
        System.out.println(formatMatrixForDisplay(testMtx1, null, (String) null));

        Matrix<? extends Numeric> root2 = MathUtils.sqrt(testMtx1);
        System.out.println();
        System.out.println(formatMatrixForDisplay(root2, null, (String) null));

        RealType epsilon = new RealImpl("0.0001", MathContext.DECIMAL64);
        RealType[][] rootSeed = new RealType[][] {
                {new RealImpl("7", MathContext.DECIMAL64), new RealImpl("6", MathContext.DECIMAL64)},
                {reTwo, new RealImpl("4", MathContext.DECIMAL64)}
        };
        Matrix<RealType> expected = new BasicMatrix<>(rootSeed);
        assertTrue(MathUtils.areEqualToWithin(expected, (Matrix<RealType>) root2, epsilon));
    }
}
