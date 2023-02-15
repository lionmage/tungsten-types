/*
 * The MIT License
 *
 * Copyright © 2018 Robert Poole <Tarquin.AZ@gmail.com>.
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
package tungsten.types.vector.impl;

import tungsten.types.Numeric;
import tungsten.types.Vector;
import tungsten.types.exceptions.CoercionException;
import tungsten.types.numerics.ComplexType;
import tungsten.types.numerics.RealType;
import tungsten.types.numerics.Sign;
import tungsten.types.numerics.impl.ComplexRectImpl;
import tungsten.types.numerics.impl.ExactZero;
import tungsten.types.numerics.impl.RealImpl;
import tungsten.types.util.MathUtils;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 *
 * @author Robert Poole <a href="mailto:Tarquin.AZ@gmail.com">Tarquin.AZ@gmail.com</a>
 */
public class ComplexVector implements Vector<ComplexType> {
    private final List<ComplexType> elements;
    private MathContext mctx = MathContext.UNLIMITED;
    
    /**
     * Creates a new empty instance of {@link ComplexVector} with room for
     * {@code initialCapacity} elements.
     * @param initialCapacity the desired initial capacity for this vector
     */
    public ComplexVector(long initialCapacity) {
        if (initialCapacity > (long) Integer.MAX_VALUE) {
            throw new IllegalArgumentException("This implementation of Vector cannot store " + initialCapacity + " elements.");
        }
        this.elements = new ArrayList<>((int) initialCapacity);
    }
    
    public ComplexVector(List<ComplexType> elements) {
        this.elements = elements;
    }
    
    public ComplexVector(ComplexType[] cplxArray, MathContext mctx) {
        this.mctx = mctx;
        this.elements = Arrays.stream(cplxArray).sequential().collect(Collectors.toList());
    }
    
    /**
     * A copy constructor that takes a vector of reals and generates
     * a vector of complex values.
     * @param source a vector of {@link RealType} elements
     */
    public ComplexVector(Vector<RealType> source) {
        this.elements = new ArrayList<>((int) source.length());
        for (long idx = 0L; idx < source.length(); idx++) {
            try {
                this.setElementAt((ComplexType) source.elementAt(idx).coerceTo(ComplexType.class), idx);
            } catch (CoercionException ex) {
                Logger.getLogger(ComplexVector.class.getName()).log(Level.SEVERE, "Error converting real to complex vector at index " + idx, ex);
                throw new IllegalStateException("Cannot create complex vector from real vector; failed coercion at element " + idx);
            }
        }
    }
    
    public void setMathContext(MathContext mctx) {
        if (mctx == null) {
            throw new IllegalArgumentException("MathContext must not be null");
        }
        this.mctx = mctx;
//        for (ComplexType element : elements) {
//            OptionalOperations.setMathContext(element, mctx);
//        }
    }

    @Override
    public long length() {
        return elements.size();
    }

    @Override
    public ComplexType elementAt(long position) {
        if (position > (long) Integer.MAX_VALUE) {
            throw new IndexOutOfBoundsException("Index exceeds what this vector implementation supports");
        }
        return elements.get((int) position);
    }

    @Override
    public void setElementAt(ComplexType element, long position) {
        if (position > (long) Integer.MAX_VALUE) {
            throw new IndexOutOfBoundsException("Index exceeds what this vector implementation supports");
        }
        elements.set((int) position, element);
    }

    @Override
    public void append(ComplexType element) {
        elements.add(element);
    }

    @Override
    public Vector<ComplexType> add(Vector<ComplexType> addend) {
        if (this.length() != addend.length()) {
            throw new ArithmeticException("Cannot add vectors of different length");
        }
        ComplexVector result = new ComplexVector(new ArrayList<>(elements.size()));
        for (long idx = 0L; idx < length(); idx++) {
            ComplexType sum = (ComplexType) this.elementAt(idx).add(addend.elementAt(idx));
            result.setElementAt(sum, idx);
        }
        result.setMathContext(mctx);
        return result;
    }

    @Override
    public Vector<ComplexType> subtract(Vector<ComplexType> subtrahend) {
        if (this.length() != subtrahend.length()) {
            throw new ArithmeticException("Cannot add vectors of different length");
        }
        ComplexVector result = new ComplexVector(new ArrayList<>(elements.size()));
        for (long idx = 0L; idx < length(); idx++) {
            ComplexType difference = (ComplexType) this.elementAt(idx).subtract(subtrahend.elementAt(idx));
            result.setElementAt(difference, idx);
        }
        result.setMathContext(mctx);
        return result;
    }

    @Override
    public Vector<ComplexType> negate() {
        List<ComplexType> list = elements.stream().map(ComplexType::negate).collect(Collectors.toList());
        final ComplexVector result = new ComplexVector(list);
        result.setMathContext(mctx);
        return result;
    }

    /**
     * Similar to {@link #negate() }, but converts each element into its
     * complex conjugate instead of its negative.
     * @return a new vector of complex conjugates of this vector's elements
     */
    public Vector<ComplexType> conjugate() {
        List<ComplexType> list = elements.stream().map(ComplexType::conjugate).collect(Collectors.toList());
        final ComplexVector result = new ComplexVector(list);
        result.setMathContext(mctx);
        return result;
    }

    @Override
    public Vector<ComplexType> scale(ComplexType factor) {
        List<ComplexType> list = elements.stream().map(x -> (ComplexType) x.multiply(factor)).collect(Collectors.toList());
        final ComplexVector result = new ComplexVector(list);
        result.setMathContext(mctx);
        return result;
    }

    @Override
    public ComplexType magnitude() {
        Numeric result = this.dotProduct(this).sqrt();
        if (result instanceof ComplexType) {
            ComplexType cplx = (ComplexType) result;
            assert(cplx.real().asBigDecimal().compareTo(BigDecimal.ZERO) >= 0);
            assert(cplx.imaginary().sign() == Sign.ZERO);
            return cplx;
        } else if (result instanceof RealType) {
            RealType real = (RealType) result;
            assert(real.sign() == Sign.POSITIVE || real.sign() == Sign.ZERO);
            RealType zero = new RealImpl(BigDecimal.ZERO, mctx, true);
            return new ComplexRectImpl(real, zero);
        } else {
            try {
                return (ComplexType) result.coerceTo(ComplexType.class);
            } catch (CoercionException ex) {
                Logger.getLogger(ComplexVector.class.getName()).log(Level.SEVERE, "Could not coerce magnitude to complex type.", ex);
                throw new IllegalStateException("Failed coercion while computing magnitude.", ex);
            }
        }
    }

    @Override
    public ComplexType dotProduct(Vector<ComplexType> other) {
        if (this.length() != other.length()) {
            throw new ArithmeticException("Cannot compute dot product for vectors of different length");
        }
        final RealType zero;
        try {
            zero = (RealType) ExactZero.getInstance(mctx).coerceTo(RealType.class);
        } catch (CoercionException ex) {
            // we should never get here
            throw new IllegalStateException(ex);
        }
        ComplexType accum = new ComplexRectImpl(zero, zero, true);
        for (long idx = 0L; idx < this.length(); idx++) {
            accum = (ComplexType) accum.add(this.elementAt(idx).multiply(other.elementAt(idx).conjugate()));
        }
        return accum;
    }

    private static TableElement telt(int idx, int coeff) {
        return new TableElement(idx, coeff);
    }
    
    // anything with an index of -1 will be skipped over
    private static final TableElement[][] cpTable =
    {
        {telt(-1, 0), telt(2, 1), telt(1, -1), telt(4, 1), telt(3, -1), telt(6, -1), telt(5, 1)},
        {telt(2, -1), telt(-1, 0), telt(0, 1), telt(5, 1), telt(6, 1), telt(3, -1), telt(4, -1)},
        {telt(1, 1), telt(0, -1), telt(-1, 0), telt(6, 1), telt(5, -1), telt(4, 1), telt(3, -1)},
        {telt(4, -1), telt(5, -1), telt(6, -1), telt(-1, 0), telt(0, 1), telt(1, 1), telt(2, 1)},
        {telt(3, 1), telt(6, -1), telt(5, 1), telt(0, -1), telt(-1, 0), telt(2, -1), telt(1, 1)},
        {telt(6, 1), telt(3, 1), telt(4, -1), telt(1, -1), telt(2, 1), telt(-1, 0), telt(0, -1)},
        {telt(5, -1), telt(4, 1), telt(3, 1), telt(2, -1), telt(1, -1), telt(0, 1), telt(-1, 0)}
    };
    
    private static final RealType ReZERO = new RealImpl(BigDecimal.ZERO);
    private static final ComplexType ZERO = new ComplexRectImpl(ReZERO, ReZERO);

    @Override
    public Vector<ComplexType> crossProduct(Vector<ComplexType> other) {
        if (this.length() != other.length()) {
            throw new ArithmeticException("Cannot compute cross product for vectors of different dimension.");
        }
        ComplexVector result = null;
        if (this.length() == 3L || this.length() == 7L) {
            result = new ComplexVector(this.length());
            int maxidx = (int) this.length();
            // see: https://en.wikipedia.org/wiki/Seven-dimensional_cross_product
            for (int y = 0; y < maxidx; y++) {
                for (int x = 0; x < maxidx; x++) {
                    long index = cpTable[y][x].getIndex();
                    if (index < 0L) continue;
                    ComplexType coeff = cpTable[y][x].getCplxCoeff();
                    ComplexType accum = result.elementAt(index) == null ? ZERO : result.elementAt(index);
                    result.setElementAt((ComplexType) accum.add(this.elementAt(y).multiply(other.elementAt(x)).multiply(coeff)), index);
                }
            }
        } else {
            throw new ArithmeticException("Cross product undefined for " + this.length() + " dimensions.");
        }
        return result;
    }

    /**
     * Compute the angle &theta; between this vector and the given vector.
     * @param other the other vector
     * @return the angle &theta; between this and {@code other}
     */
    @Override
    public RealType computeAngle(Vector<ComplexType> other) {
        try {
            ComplexType cosine = (ComplexType) this.dotProduct(other)
                    .divide(this.magnitude().multiply(other.magnitude()))
                    .coerceTo(ComplexType.class);
            Numeric result = MathUtils.arccos(cosine);
            if (result.isCoercibleTo(RealType.class)) {
                return (RealType) result.coerceTo(RealType.class);
            } else {
                Logger.getLogger(ComplexVector.class.getName()).log(Level.WARNING, "arccos() returned a non-real value: " + result + "; returning real portion");
                return ((ComplexType) result).real();
            }
        } catch (CoercionException ex) {
            Logger.getLogger(ComplexVector.class.getName()).log(Level.SEVERE, "Could not coerce vector magnitude to real.", ex);
        }
        throw new ArithmeticException("Unable to compute angle between two complex vectors");
    }

    @Override
    public Vector<ComplexType> normalize() {
        try {
            final ComplexType scalefactor = (ComplexType) this.magnitude().inverse().coerceTo(ComplexType.class);
            return this.scale(scalefactor);
        } catch (CoercionException ex) {
            Logger.getLogger(RealVector.class.getName()).log(Level.SEVERE, "Coercion failed for computed scale", ex);
        }
        throw new ArithmeticException("Could not compute the normal of this vector.");
    }

    @Override
    public MathContext getMathContext() {
        return mctx;
    }
}
