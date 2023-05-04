package tungsten.types.util;

import org.junit.jupiter.api.Test;
import tungsten.types.Numeric;
import tungsten.types.numerics.IntegerType;
import tungsten.types.numerics.RationalType;
import tungsten.types.numerics.impl.IntegerImpl;
import tungsten.types.numerics.impl.RationalImpl;

import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class MathUtilsTest {
    @Test
    public void checkGeneralizedBinomialCoefficient() {
        RationalType x = new RationalImpl("2/3");
        IntegerType  k = new IntegerImpl(BigInteger.valueOf(2L));
        RationalType expected = new RationalImpl("-1/9");

        Numeric result = MathUtils.generalizedBinomialCoefficient(x, k);
        assertEquals(expected, result);
    }
}
