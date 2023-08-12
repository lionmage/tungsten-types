package tungsten.types.matrix.impl;
/*
 * The MIT License
 *
 * Copyright © 2023 Robert Poole <Tarquin.AZ@gmail.com>.
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
 */

import tungsten.types.Matrix;
import tungsten.types.Numeric;
import tungsten.types.Vector;
import tungsten.types.exceptions.CoercionException;
import tungsten.types.numerics.impl.One;
import tungsten.types.vector.impl.ImmutableVector;

import java.math.MathContext;
import java.util.HashSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.LongStream;

/**
 * A representation of a Cauchy matrix.  Given a Cauchy matrix <strong>C</strong>,
 * it holds that c<sub>ij</sub> = 1/(X<sub>i</sub> + Y<sub>j</sub>) for vectors
 * X&#x20D7; and Y&#x20D7;.
 * <br/>This matrix implementation has optimized methods for {@link #determinant()}
 * and {@link #inverse()}. Note that, for a given Cauchy matrix <strong>C</strong>,
 * the following hold true:
 * <ul>
 *     <li>{@code X.length() == C.rows()}</li>
 *     <li>{@code Y.length() == C.columns()}</li>
 *     <li>X<sub>i</sub> + Y<sub>j</sub> &ne; 0 for all i,&thinsp;j</li>
 * </ul>
 * @param <S> the type of the matrix elements
 * @param <E> the type of the elements of the supplied vectors
 * @author Robert Poole <a href="mailto:Tarquin.AZ@gmail.com">Gmail</a>
 * @see <a href="https://en.wikipedia.org/wiki/Cauchy_matrix">the Wikipedia article</a>
 * @see <a href="https://proofwiki.org/wiki/Inverse_of_Cauchy_Matrix">formula for the inverse</a>
 */
public class CauchyMatrix<S extends Numeric, E extends Numeric> extends ParametricMatrix<S> {
    private final Vector<E> X;
    private final Vector<E> Y;
    private final Class<S>  mtxEltType;

    public CauchyMatrix(Vector<E> X, Vector<E> Y, Class<S> elementType) {
        super(X.length(), Y.length(),
                (row, column) -> {
                    try {
                        return (S) X.elementAt(row).add(Y.elementAt(column)).inverse().coerceTo(elementType);
                    } catch (CoercionException ce) {
                        throw new IllegalStateException("While obtaining element " + row + ", " + column, ce);
                    }
                });
        this.X = X;
        this.Y = Y;
        this.mtxEltType = elementType;
        check();
    }

    private void check() {
        for (long i = 0L; i < X.length(); i++) {
            Numeric xneg = X.elementAt(i).negate();
            for (long j = 0L; j < Y.length(); j++) {
                if (Y.elementAt(j).equals(xneg)) {
                    throw new IllegalStateException("Y[" + j + "] = -X[" + i + "], which is not allowed");
                }
            }
        }
        // now check each vector for repeated entries
        if (hasDuplicates(X) || hasDuplicates(Y)) {
            throw new IllegalStateException("A vector argument has duplicate entries, which is not allowed");
        }
    }

    private boolean hasDuplicates(Vector<E> vec) {
        HashSet<E> uniques = new HashSet<>((int) vec.length());
        for (long k = 0L; k < vec.length(); k++) {
            uniques.add(vec.elementAt(k));
        }
        return vec.length() > (long) uniques.size();
    }

    @Override
    public S determinant() {
        if (X.length() != Y.length()) {
            throw new ArithmeticException("Determinant only applies to square matrices");
        }
        MathContext ctx = new MathContext(Math.min(X.getMathContext().getPrecision(), Y.getMathContext().getPrecision()));
        Numeric p1 = One.getInstance(ctx);
        for (long i = 1L; i < X.length(); i++) {
            for (long j = 0L; j < i - 1L; j++) {
                p1 = p1.multiply(X.elementAt(i).subtract(X.elementAt(j))
                        .multiply(Y.elementAt(j).subtract(Y.elementAt(i))));
            }
        }
        Numeric p2 = One.getInstance(ctx);
        for (long i = 0L; i < X.length(); i++) {
            for (long j = 0L; j < Y.length(); j++) {
                p2 = p2.multiply(X.elementAt(i).add(Y.elementAt(j)));
            }
        }
        try {
            return (S) p1.divide(p2).coerceTo(mtxEltType);
        } catch (CoercionException fatal) {
            final Logger logger = Logger.getLogger(CauchyMatrix.class.getName());
            logger.log(Level.SEVERE,"Unable to convert determinant to desired type.", fatal);
            logger.log(Level.FINE, "Determinant should be the ratio {0}/{1}.",
                    new Object[] { p1, p2 });
            throw new ArithmeticException("While computing the determinant of a Cauchy matrix: " + fatal.getMessage());
        }
    }

    @Override
    public Matrix<? extends Numeric> inverse() {
        if (X.length() != Y.length()) {
            throw new ArithmeticException("Inverse only applies to square matrices");
        }
        final MathContext ctx = new MathContext(Math.min(X.getMathContext().getPrecision(), Y.getMathContext().getPrecision()));
        return new ParametricMatrix<>(Y.length(), X.length(),
                (row, column) -> {
                    Numeric p1 = LongStream.range(0L, X.length())
                            .mapToObj(k -> X.elementAt(column).add(Y.elementAt(k)).multiply(X.elementAt(k).add(Y.elementAt(row))))
                            .reduce(One.getInstance(ctx), Numeric::multiply);
                    Numeric dscale = X.elementAt(column).add(Y.elementAt(row));
                    Numeric p2 = LongStream.range(0L, X.length()).filter(k -> k != column)
                            .mapToObj(k -> X.elementAt(column).subtract(X.elementAt(k)))
                            .reduce(One.getInstance(ctx), Numeric::multiply);
                    Numeric p3 = LongStream.range(0L, Y.length()).filter(k -> k != row)
                            .mapToObj(k -> Y.elementAt(row).subtract(Y.elementAt(k)))
                            .reduce(One.getInstance(ctx), Numeric::multiply);
                    return p1.divide(dscale.multiply(p2).multiply(p3));
                });
    }

    public Vector<E> getX() {
        return new ImmutableVector<>(X);
    }

    public Vector<E> getY() {
        return new ImmutableVector<>(Y);
    }
}
