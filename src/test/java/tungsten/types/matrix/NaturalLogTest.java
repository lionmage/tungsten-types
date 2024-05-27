package tungsten.types.matrix;

import org.junit.jupiter.api.Test;
import tungsten.types.Matrix;
import tungsten.types.Numeric;
import tungsten.types.matrix.impl.BasicMatrix;
import tungsten.types.numerics.RealType;
import tungsten.types.numerics.impl.RealImpl;
import tungsten.types.util.MathUtils;

import java.math.BigDecimal;
import java.math.MathContext;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static tungsten.types.util.UnicodeTextEffects.formatMatrixForDisplay;
import static tungsten.types.util.MathUtils.areEqualToWithin;

public class NaturalLogTest {
    RealType zero = new RealImpl(BigDecimal.ZERO, MathContext.DECIMAL32);
    RealType one  = new RealImpl(BigDecimal.ONE, MathContext.DECIMAL32);
    RealType two  = new RealImpl(BigDecimal.valueOf(2L), MathContext.DECIMAL32);
    RealType three = new RealImpl(BigDecimal.valueOf(3L), MathContext.DECIMAL32);
    RealType four  = new RealImpl(BigDecimal.valueOf(4L), MathContext.DECIMAL32);
    RealType five  = new RealImpl(BigDecimal.valueOf(5L), MathContext.DECIMAL32);
    RealType seven = new RealImpl(BigDecimal.valueOf(7L), MathContext.DECIMAL32);

    @Test
    public void test2x2Matrix() {
        RealType[][] seed = {{three, zero}, {five, seven}};
        Matrix<RealType> M = new BasicMatrix<>(seed);

        // expected result of ln(M)
        RealType ln3 = MathUtils.ln(three);
        RealType ln7 = MathUtils.ln(seven);
        RealType R10 = (RealType) five.negate().multiply(ln3).divide(four).add(five.multiply(ln7).divide(four));
        RealType[][] seed2 = {{ln3, zero}, {R10, ln7}};
        Matrix<RealType> expected = new BasicMatrix<>(seed2);

        System.out.println("Original matrix M:");
        System.out.println(formatMatrixForDisplay(M, null, (String) null));

        System.out.println();

        System.out.println("Expected result of ln(M):");
        System.out.println(formatMatrixForDisplay(expected, null, (String) null));

        System.out.println();

        Matrix<? extends Numeric> result = MathUtils.ln(M);
        System.out.println("Actual result of ln(M):");
        System.out.println(formatMatrixForDisplay(result, null, (String) null));

        // we know that result is really a real matrix, so casting here is OK
        // note that epsilon is a bit on the "large" side, because DECIMAL32
        // does not afford a very high precision for matrix operations
        RealType epsilon = new RealImpl("0.0002", MathContext.DECIMAL32);
        assertTrue(areEqualToWithin(expected, (Matrix<RealType>) result, epsilon));
    }
}
