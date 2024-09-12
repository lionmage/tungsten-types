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

package tungsten.types.matrix;

import org.junit.jupiter.api.Test;
import tungsten.types.Matrix;
import tungsten.types.exceptions.CoercionException;
import tungsten.types.matrix.impl.BasicMatrix;
import tungsten.types.numerics.ComplexType;
import tungsten.types.numerics.RealType;
import tungsten.types.numerics.impl.ComplexRectImpl;
import tungsten.types.numerics.impl.Pi;
import tungsten.types.numerics.impl.RealImpl;
import tungsten.types.util.MathUtils;

import java.math.MathContext;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static tungsten.types.util.MathUtils.areEqualToWithin;
import static tungsten.types.util.UnicodeTextEffects.formatMatrixForDisplay;

public class TrigFunctionTest {
    final Pi pi = Pi.getInstance(MathContext.DECIMAL64);
    final RealType two = new RealImpl("2", MathContext.DECIMAL64);
    final RealType three = new RealImpl("3", MathContext.DECIMAL64);
    final RealType five = new RealImpl("5", MathContext.DECIMAL64);
    final RealType six = (RealType) two.multiply(three);

    final RealType piOver3 = (RealType) pi.divide(three);
    final RealType twoPiOver3 = (RealType) two.multiply(piOver3);

    final RealType piOver6 = (RealType) pi.divide(six);
    final RealType fivePiOver6 = (RealType) five.multiply(piOver6);

    @Test
    public void testMatrixSine() throws CoercionException {
        ComplexType[][] seed = {{new ComplexRectImpl(twoPiOver3), new ComplexRectImpl(piOver3)},
                {new ComplexRectImpl(piOver6), new ComplexRectImpl(fivePiOver6)}};
        final RealType epsilon = new RealImpl("0.00001", MathContext.DECIMAL64);

        // this is the starting matrix
        Matrix<ComplexType> A = new BasicMatrix<>(seed);
        System.out.println("Original matrix A:");
        System.out.println(formatMatrixForDisplay(A, (String) null, null));

        // this calculation is manually demonstrated in https://www.youtube.com/watch?v=kJt6Io_8hJQ
        Matrix<ComplexType> result = MathUtils.sin(A);
        System.out.println("\nsin(A):");
        System.out.println(formatMatrixForDisplay(result, (String) null, null));
        assertTrue(MathUtils.isRealMatrix(result, epsilon), "sin(A) result should be a real matrix");
        Matrix<RealType> cleaned = MathUtils.stripImaginary(result);
        System.out.println("\nsin(A) as a real-valued matrix:");
        System.out.println(formatMatrixForDisplay(cleaned, (String) null, null));

        RealType twoThirds = (RealType) two.divide(three);
        assertTrue(areEqualToWithin(twoThirds, cleaned.valueAt(0L, 0L), epsilon));
        assertTrue(areEqualToWithin(twoThirds.negate(), cleaned.valueAt(0L, 1L), epsilon));
        // without coercion, the following would give a rational value of 1/3
        RealType oneThird = (RealType) three.inverse().coerceTo(RealType.class);
        assertTrue(areEqualToWithin(oneThird.negate(), cleaned.valueAt(1L, 0L), epsilon));
        assertTrue(areEqualToWithin(oneThird, cleaned.valueAt(1L, 1L), epsilon));

        // this may differ from A if ln() has a different branch cut
        Matrix<ComplexType> A_asin = MathUtils.arcsin(result);
        System.out.println("Arcsin of sin(A):");
        System.out.println(formatMatrixForDisplay(A_asin, (String) null, null));
        assertTrue(areEqualToWithin(piOver3, A_asin.valueAt(0L, 0L).real(), epsilon));
        assertTrue(areEqualToWithin(piOver6, A_asin.valueAt(1L, 1L).real(), epsilon));
    }
}
