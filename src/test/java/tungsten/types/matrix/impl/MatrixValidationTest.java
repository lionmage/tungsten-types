package tungsten.types.matrix.impl;

import org.junit.jupiter.api.Test;
import tungsten.types.Matrix;
import tungsten.types.Numeric;
import tungsten.types.numerics.IntegerType;
import tungsten.types.numerics.RealType;
import tungsten.types.numerics.impl.IntegerImpl;
import tungsten.types.numerics.impl.RealImpl;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class MatrixValidationTest {
    RealType zero = new RealImpl(BigDecimal.ZERO, MathContext.DECIMAL128);
    RealType one = new RealImpl(BigDecimal.ONE, MathContext.DECIMAL128);
    IntegerType two = new IntegerImpl(BigInteger.valueOf(2L), true);
    Matrix<RealType> antidiagonal;

    public MatrixValidationTest() {
        RealType[][] src = new RealType[][] {{zero, one}, {one.negate(), zero}};
        antidiagonal = new BasicMatrix<>(src);
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
}
