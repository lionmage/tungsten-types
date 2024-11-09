/*
 * The MIT License
 *
 * Copyright © 2024 Robert Poole <Tarquin.AZ@gmail.com>.
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

package tungsten.types.matrix.impl;

import org.junit.jupiter.api.Test;
import tungsten.types.Matrix;
import tungsten.types.Numeric;
import tungsten.types.Set;
import tungsten.types.numerics.RealType;
import tungsten.types.numerics.impl.RealImpl;
import tungsten.types.util.MathUtils;

import java.math.BigDecimal;
import java.math.MathContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class JordanMatrixTest {
    RealType zero = new RealImpl(BigDecimal.ZERO, MathContext.DECIMAL128);
    RealType one = new RealImpl(BigDecimal.ONE, MathContext.DECIMAL128);
    RealType five = new RealImpl("5.0", MathContext.DECIMAL128);
    RealType three = new RealImpl("3.0", MathContext.DECIMAL128);
    RealType two = new RealImpl("2", MathContext.DECIMAL128);
    JordanMatrix<RealType> jordan1;

    public JordanMatrixTest() {
        RealType[] lambdaValues = {five, three, two, two};
        long[] nValues = {3L, 2L, 1L, 3L};

        jordan1 = new JordanMatrix<>(lambdaValues, nValues);
    }

    @Test
    public void testJordanIndices() {
        assertEquals(9L, jordan1.columns());
        assertEquals(9L, jordan1.rows());
        assertEquals(five, jordan1.valueAt(0L, 0L));
        assertEquals(five, jordan1.valueAt(2L, 2L));
        assertEquals(one, jordan1.valueAt(0L, 1L));
        assertEquals(zero, jordan1.valueAt(1L, 3L));
        assertEquals(three, jordan1.valueAt(3L, 3L));
        assertEquals(one, jordan1.valueAt(3L, 4L));
        assertEquals(zero, jordan1.valueAt(4L, 3L));
        assertEquals(two, jordan1.valueAt(5L, 5L));
        // since this submatrix is a singleton, there is no superdiagonal
        assertEquals(zero, jordan1.valueAt(5L, 6L));
        // and check an entry way off the diagonal
        assertEquals(zero, jordan1.valueAt(8L, 0L));
        assertEquals(zero, jordan1.valueAt(0L, 8L));
    }

    @Test
    public void eigenvalues() {
        Set<RealType> values = Set.of(five, three, two);
        assertEquals(values, jordan1.eigenvalues());
    }

    @Test
    public void inverses() {
        Matrix<? extends Numeric> inverse = jordan1.inverse();
        Matrix<RealType> reInv = new BasicMatrix<>(inverse).upconvert(RealType.class);
        Matrix<RealType> reIdent = new BasicMatrix<>(new IdentityMatrix(jordan1.rows(), MathContext.DECIMAL128))
                .upconvert(RealType.class);
        RealType epsilon = new RealImpl("0.000001", MathContext.DECIMAL128);
        assertTrue(MathUtils.areEqualToWithin(reIdent, jordan1.multiply(reInv), epsilon));
    }
}
