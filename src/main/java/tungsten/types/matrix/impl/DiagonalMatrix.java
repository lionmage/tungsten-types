/*
 * The MIT License
 *
 * Copyright © 2019 Robert Poole <Tarquin.AZ@gmail.com>.
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
package tungsten.types.matrix.impl;

import tungsten.types.Matrix;
import tungsten.types.Numeric;
import tungsten.types.Vector;
import tungsten.types.exceptions.CoercionException;
import tungsten.types.numerics.ComplexType;
import tungsten.types.numerics.IntegerType;
import tungsten.types.numerics.NumericHierarchy;
import tungsten.types.numerics.RealType;
import tungsten.types.numerics.impl.Euler;
import tungsten.types.numerics.impl.ExactZero;
import tungsten.types.numerics.impl.One;
import tungsten.types.numerics.impl.Zero;
import tungsten.types.util.ClassTools;
import tungsten.types.util.MathUtils;
import tungsten.types.util.OptionalOperations;
import tungsten.types.vector.impl.ComplexVector;
import tungsten.types.vector.impl.RealVector;

import java.lang.reflect.Array;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * A compact representation of a diagonal matrix.
 *
 * @author Robert Poole <a href="mailto:Tarquin.AZ@gmail.com">Tarquin.AZ@gmail.com</a>
 * @param <T> the numeric type of the elements of this matrix
 */
public class DiagonalMatrix<T extends Numeric> implements Matrix<T>  {
    final private T[] elements;
    
    @SafeVarargs
    public DiagonalMatrix(T... elements) {
        this.elements = Arrays.copyOf(elements, elements.length);
    }

    /**
     * Constructor which initializes a diagonal matrix with the
     * elements of a vector.  Note that this is equivalent to
     * the <em>diag</em> operator:<br/>
     * For vector <strong>a</strong> with elements a<sub>0</sub>, a<sub>1</sub>, &hellip;, a<sub>n - 1</sub>,
     * the diagonal matrix <strong>D</strong> may be denoted
     * <strong>D</strong>&nbsp;=&nbsp;diag(a<sub>0</sub>,&thinsp;&hellip;,&thinsp;a<sub>n - 1</sub>)
     * or <strong>D</strong>&nbsp;=&nbsp;diag(<strong>a</strong>).
     * @param source a vector containing the elements for this diagonal matrix
     */
    public DiagonalMatrix(Vector<T> source) {
        final Class<T> clazz = source.getElementType();
        int arrayLength = source.length() > (long) Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) source.length();
        if (arrayLength < source.length()) {
            Logger.getLogger(DiagonalMatrix.class.getName()).log(Level.WARNING,
                    "Source vector with {0} elements will not fit into a Java array; truncating.",
                    source.length());
        }
        elements = (T[]) Array.newInstance(clazz, arrayLength);
        for (int i = 0; i < arrayLength; i++) {
            elements[i] = source.elementAt(i);
        }
    }

    @Override
    public long columns() {
        return elements.length;
    }

    @Override
    public long rows() {
        return elements.length;
    }

    @Override
    public T valueAt(long row, long column) {
        if (row == column) {
            return elements[(int) row];
        }
        // we can get away with this because coerceTo() will look up the appropriate interface type
        final Class<T> clazz = (Class<T>) elements[0].getClass();
        try {
            return (T) ExactZero.getInstance(elements[0].getMathContext()).coerceTo(clazz);
        } catch (CoercionException ex) {
            Logger.getLogger(DiagonalMatrix.class.getName()).log(Level.SEVERE,
                    "Coercion of Zero to " + clazz.getTypeName() + " failed.", ex);
            throw new IllegalStateException(ex);
        }
    }

    @Override
    public T determinant() {
        return Arrays.stream(elements).reduce((a, b) -> (T) a.multiply(b)).orElseThrow();
    }

    @Override
    public T trace() {
        return Arrays.stream(elements).reduce((a, b) -> (T) a.add(b)).orElseThrow();
    }

    @Override
    public DiagonalMatrix<T> transpose() {
        return this; // diaginal matrices are their own transpose
    }

    @Override
    public Matrix<T> add(Matrix<T> addend) {
        if (addend.rows() != this.rows() || addend.columns() != this.columns()) {
            throw new ArithmeticException("Addend must match dimensions of this diagonal matrix");
        }
        
        BasicMatrix<T> result = new BasicMatrix<>(addend);
        final Class<T> clazz = (Class<T>) OptionalOperations.findCommonType(elements[0].getClass(), OptionalOperations.findTypeFor(addend));

        try {
            for (long idx = 0L; idx < this.rows(); idx++) {
                T element = elements[(int) idx];
                T sum = (T) addend.valueAt(idx, idx).add(element).coerceTo(clazz);
                result.setValueAt(sum, idx, idx);
            }
        } catch (CoercionException ce) {
            throw new IllegalStateException(ce);
        }
        return result;
    }

    @Override
    public Matrix<T> multiply(Matrix<T> multiplier) {
        if (this.columns() != multiplier.rows()) {
            throw new ArithmeticException("Multiplier rows must match the columns of this diagonal matrix");
        }
        if (multiplier instanceof DiagonalMatrix) {
            DiagonalMatrix<T> other = (DiagonalMatrix<T>) multiplier;
            final Class<T> clazz = (Class<T>) OptionalOperations.findCommonType(elements[0].getClass(), OptionalOperations.findTypeFor(other));
            T[] prod = (T[]) Array.newInstance(clazz, elements.length);
            for (int idx = 0; idx < elements.length; idx++) {
                prod[idx] = (T) elements[idx].multiply(other.elements[idx]);
            }
            return new DiagonalMatrix<>(prod);
        }
        BasicMatrix<T> result = new BasicMatrix<>();
        // scale the rows of the multiplier
        for (long idx = 0L; idx < this.rows(); idx++) {
            result.append(multiplier.getRow(idx).scale(elements[(int) idx]));
        }
        return result;
    }

    @Override
    public DiagonalMatrix<? extends Numeric> inverse() {
        if (Arrays.stream(elements).anyMatch(Zero::isZero)) {
            throw new ArithmeticException("Diagonal matrices with any 0 elements on the diagonal have no inverse");
        }
        Numeric[] result = Arrays.stream(elements).map(Numeric::inverse).toArray(Numeric[]::new);
        return new DiagonalMatrix<>(result);
    }
    
    @Override
    public Matrix<? extends Numeric> pow(Numeric n) {
        Numeric[] result;
        final Class<T> clazz = (Class<T>) elements[0].getClass();
        // the following code should work just fine for negative exponents, without calling inverse()
        if (RealType.class.isAssignableFrom(clazz)) {
            if (n instanceof ComplexType) {
                result = Arrays.stream(elements)
                        .map(element -> MathUtils.generalizedExponent((RealType) element, (ComplexType) n, element.getMathContext()))
                        .toArray(Numeric[]::new);
            } else {
                result = Arrays.stream(elements)
                        .map(element -> MathUtils.generalizedExponent((RealType) element, n, element.getMathContext()))
                        .toArray(Numeric[]::new);
            }
        } else if (ComplexType.class.isAssignableFrom(clazz)) {
            result = Arrays.stream(elements)
                    .map(element -> MathUtils.generalizedExponent((ComplexType) element, n, element.getMathContext()))
                    .toArray(Numeric[]::new);
        } else {
            if (!n.isCoercibleTo(IntegerType.class)) {
                throw new UnsupportedOperationException("Currently, non-integer exponents are not supported for non-real and non-complex matrix element types");
            }

            try {
                final IntegerType n_int = (IntegerType) n.coerceTo(IntegerType.class);
                result = Arrays.stream(elements)
                        .map(element -> MathUtils.computeIntegerExponent(element, n_int))
                        .toArray(Numeric[]::new);
            } catch (CoercionException e) {
                throw new IllegalStateException("Could not convert exponent " + n + " to integer");
            }
        }
        return new DiagonalMatrix<>(result);
    }

    public Matrix<? extends Numeric> exp() {
        final Euler e = Euler.getInstance(elements[0].getMathContext());

        Numeric[] result = Arrays.stream(elements)
                .map(element -> element instanceof ComplexType ? e.exp((ComplexType) element) : e.exp(limitedUpconvert(element)))
                .toArray(Numeric[]::new);
        return new DiagonalMatrix<>(result);
    }
    
    @Override
    public boolean isUpperTriangular() { return true; }
    
    @Override
    public boolean isLowerTriangular() { return true; }
    
    @Override
    public boolean isTriangular() { return true; }
    
    // this method strictly exists to promote lesser types in the hierarchy to real values
    private RealType limitedUpconvert(Numeric val) {
        if (val instanceof RealType)  return (RealType) val;
        try {
            return (RealType) val.coerceTo(RealType.class);
        } catch (CoercionException ex) {
            throw new IllegalStateException(ex);
        }
    }
    
    @Override
    public String toString() {
        // 202F = non-breaking narrow space
        return Arrays.stream(elements).map(Object::toString)
                .collect(Collectors.joining(", ", "diag(\u202F", "\u202F)"));
    }

    @Override
    public DiagonalMatrix<T> scale(T scaleFactor) {
        if (One.isUnity(scaleFactor)) return this;
        final Class<T> clazz = (Class<T>) ClassTools.getInterfaceTypeFor(elements[0].getClass());
        assert clazz.isAssignableFrom(scaleFactor.getClass());
        T[] scaled = Arrays.stream(elements).map(element -> element.multiply(scaleFactor))
                .map(clazz::cast).toArray(size -> (T[]) Array.newInstance(clazz, size));
        return new DiagonalMatrix<>(scaled);
    }

    /**
     * This is the <em>diag</em> operator, intended to convert a diagonal matrix into a vector.
     * It is the effective inverse of {@link DiagonalMatrix#DiagonalMatrix(Vector)}.<br/>
     * For a diagonal matrix <strong>D</strong> with diagonal elements d<sub>0</sub>, d<sub>1</sub>, &hellip;, d<sub>n - 1</sub>,
     * diag(<strong>D</strong>)&nbsp;=&nbsp;[d<sub>0</sub>,&thinsp;&hellip;,&thinsp;d<sub>n - 1</sub>]<sup>T</sup>
     *
     * @return a vector containing the diagonal elements of this matrix
     */
    public Vector<T> diag() {
        return new Vector<>() {
            @Override
            public long length() {
                return elements.length;
            }

            @Override
            public T elementAt(long position) {
                return elements[(int) position];
            }

            @Override
            public void setElementAt(T element, long position) {
                throw new UnsupportedOperationException("diag vector is a read-only view");
            }

            @Override
            public void append(T element) {
                throw new UnsupportedOperationException("diag vector is a read-only view");
            }

            @Override
            public Vector<T> add(Vector<T> addend) {
                return addend.add(this);
            }

            @Override
            public Vector<T> subtract(Vector<T> subtrahend) {
                return subtrahend.negate().add(this);
            }

            @Override
            public Vector<T> negate() {
                T negone = OptionalOperations.dynamicInstantiate(getElementType(), -1);
                return scale(negone);
            }

            @Override
            public Vector<T> scale(T factor) {
                return DiagonalMatrix.this.scale(factor).diag();
            }

            @Override
            public RealType magnitude() {
                try {
                    return (RealType) Arrays.stream(elements).map(x -> {
                                T r = x.magnitude();
                                return r.multiply(r);
                            }).reduce(Numeric::add).map(Numeric::sqrt)
                            .orElseThrow(() -> new ArithmeticException("Error computing magnitude of vector " + Arrays.toString(elements)))
                            .coerceTo(RealType.class);
                } catch (CoercionException e) {
                    // this should never happen
                    throw new IllegalStateException(e);
                }
            }

            @Override
            public T dotProduct(Vector<T> other) {
                if (length() != other.length()) {
                    throw new ArithmeticException("Vectors must be of the same length");
                }
                Numeric accum = ExactZero.getInstance(getMathContext());
                if (ComplexType.class.isAssignableFrom(other.getElementType())) {
                    List<T> copyOf = new ArrayList<>((int) other.length());
                    for (long k = 0L; k < other.length(); k++) copyOf.add(other.elementAt(k));
                    other = (Vector<T>) new ComplexVector((List<ComplexType>) copyOf).conjugate();
                }

                for (int i = 0; i < elements.length; i++) {
                    accum = accum.add(elements[i].multiply(other.elementAt(i)));
                }
                try {
                    return (T) accum.coerceTo(getElementType());
                } catch (CoercionException e) {
                    throw new IllegalStateException("Dot product computed as: " + accum, e);
                }
            }

            @Override
            public Vector<T> crossProduct(Vector<T> other) {
                NumericHierarchy h = NumericHierarchy.forNumericType(getElementType());
                switch (h) {
                    case REAL:
                        RealVector realVector = new RealVector((RealType[]) elements, getMathContext());
                        return (Vector<T>) realVector.crossProduct((Vector<RealType>) other);
                    case COMPLEX:
                        ComplexVector cplxVector = new ComplexVector((ComplexType[]) elements, getMathContext());
                        return (Vector<T>) cplxVector.crossProduct((Vector<ComplexType>) other);
                    default:
                        throw new ArithmeticException("Cross product is supported only for real and complex vectors");
                }
            }

            @Override
            public Vector<T> normalize() {
                try {
                    T scaleFactor = (T) magnitude().inverse().coerceTo(getElementType());
                    return DiagonalMatrix.this.scale(scaleFactor).diag();
                } catch (CoercionException e) {
                    throw new ArithmeticException("While computing inverse of magnitude " + magnitude() +
                            ": " + e.getMessage());
                }
            }

            @Override
            public RealType computeAngle(Vector<T> other) {
                Numeric cosine = this.dotProduct(other).divide(this.magnitude().multiply(other.magnitude()));
                Numeric angle = MathUtils.arccos(cosine);
                if (angle instanceof RealType) return (RealType) angle;
                else if (angle instanceof ComplexType) {
                    return ((ComplexType) angle).real();
                }
                throw new ArithmeticException("arccos() gave an unexpected result: " + angle);
            }

            @Override
            public MathContext getMathContext() {
                return MathUtils.inferMathContext(Arrays.asList(elements));  // was:  elements[0].getMathContext()
            }
        };
    }
    
    @Override
    public boolean equals(Object o) {
        if (o instanceof Matrix) {
            if (o instanceof DiagonalMatrix) {
                DiagonalMatrix<? extends Numeric> that = (DiagonalMatrix<? extends Numeric>) o;
                return Arrays.equals(this.elements, that.elements);
            }

            Matrix<? extends Numeric> that = (Matrix<? extends Numeric>) o;
            if (rows() != that.rows() || columns() != that.columns()) return false;
            // check to ensure the other matrix is both upper AND lower triangular
            if (that.isUpperTriangular() && that.isLowerTriangular()) {
                // now compare all elements on the diagonal
                for (long idx = 0L; idx < rows(); idx++) {
                    if (!that.valueAt(idx, idx).equals(elements[(int) idx])) return false;
                }
                return true;
            }
        }
        return false;
    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 97 * hash + Arrays.deepHashCode(this.elements);
        return hash;
    }
}
