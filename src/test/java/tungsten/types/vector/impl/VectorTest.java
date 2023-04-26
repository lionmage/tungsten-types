package tungsten.types.vector.impl;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tungsten.types.Vector;
import tungsten.types.numerics.RealType;
import tungsten.types.numerics.impl.RealImpl;
import tungsten.types.util.AngularDegrees;
import tungsten.types.util.MathUtils;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class VectorTest {
    RealType a = new RealImpl("3.0", MathContext.DECIMAL128);
    RealType b = new RealImpl("4.0", MathContext.DECIMAL128);
    RealType c = new RealImpl("5.0", MathContext.DECIMAL128);
    RealType zero = new RealImpl(BigDecimal.ZERO, MathContext.DECIMAL128);
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
        // the computed angle should agree within 5 or 6 decimal places
        MathContext common = new MathContext(5);
        assertEquals(MathUtils.round(degrees.asRadians(), common),
                MathUtils.round(sum.computeAngle(leg1), common));
    }
}
