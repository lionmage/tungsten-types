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
import tungsten.types.numerics.impl.ExactZero;
import tungsten.types.numerics.impl.RealImpl;
import tungsten.types.util.MathUtils;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Implementation of a real-valued vector.
 *
 * @author Robert Poole <a href="mailto:Tarquin.AZ@gmail.com">Tarquin.AZ@gmail.com</a>
 */
public class RealVector implements Vector<RealType> {
    private final List<RealType> elements;
    private MathContext mctx = MathContext.UNLIMITED;

    /**
     * Creates a new empty instance of {@link RealVector} with room for
     * {@code initialCapacity} elements.
     * @param initialCapacity the desired initial capacity for this vector
     */
    public RealVector(long initialCapacity) {
        if (initialCapacity > (long) Integer.MAX_VALUE) {
            throw new IllegalArgumentException("This implementation of Vector cannot store " + initialCapacity + " elements");
        }
        this.elements = new ArrayList<>((int) initialCapacity);
    }
    
    /**
     * Creates a new instance of {@link RealVector} initialized with
     * a {@link List} of elements. Note that this constructor does not
     * copy the {@link List}.
     * @param elements the list of elements
     */
    public RealVector(List<RealType> elements) {
        this.elements = elements;
        setMathContext(MathUtils.inferMathContext(elements));
    }
    
    /**
     * Copy constructor.
     * @param source the vector from which to copy
     */
    public RealVector(RealVector source) {
        this.mctx = source.getMathContext();
        this.elements = new ArrayList<>(source.elements);
    }

    public RealVector(RealType[] realArray, MathContext mctx) {
        this.mctx = mctx;
        this.elements = Arrays.stream(realArray).sequential().collect(Collectors.toList());
    }
    
    public void setMathContext(MathContext mctx) {
        if (mctx == null) {
            throw new IllegalArgumentException("MathContext must not be null");
        }
        this.mctx = mctx;
    }

    @Override
    public Class<RealType> getElementType() {
        return RealType.class;
    }

    @Override
    public long length() {
        return elements.size();
    }

    @Override
    public RealType elementAt(long position) {
        if (position > (long) Integer.MAX_VALUE) {
            throw new IndexOutOfBoundsException(INDEX_TOO_HIGH);
        }
        return elements.get((int) position);
    }

    @Override
    public void setElementAt(RealType element, long position) {
        if (position > (long) Integer.MAX_VALUE) {
            throw new IndexOutOfBoundsException(INDEX_TOO_HIGH);
        }
        if (position > (long) elements.size()) {
            final RealType zero = new RealImpl(BigDecimal.ZERO, mctx);
            int count = (int) position - elements.size();
            elements.addAll(Collections.nCopies(count, zero));
            assert position == (long) elements.size();
        }
        if (position == (long) elements.size()) elements.add(element);
        else elements.set((int) position, element);
    }

    @Override
    public Vector<RealType> add(Vector<RealType> addend) {
        if (this.length() != addend.length()) {
            throw new ArithmeticException("Cannot add vectors of different length");
        }
        RealVector result = new RealVector(this.length());
        for (long idx = 0L; idx < length(); idx++) {
            RealType sum = (RealType) this.elementAt(idx).add(addend.elementAt(idx));
            result.setElementAt(sum, idx);
        }
        result.setMathContext(mctx);
        return result;
    }

    @Override
    public Vector<RealType> subtract(Vector<RealType> subtrahend) {
        if (this.length() != subtrahend.length()) {
            throw new ArithmeticException("Cannot subtract vectors of different length");
        }
        RealVector result = new RealVector(this.length());
        for (long idx = 0L; idx < length(); idx++) {
            RealType difference = (RealType) this.elementAt(idx).subtract(subtrahend.elementAt(idx));
            result.setElementAt(difference, idx);
        }
        result.setMathContext(mctx);
        return result;
    }

    @Override
    public Vector<RealType> negate() {
        RealVector result = new RealVector(length());
        for (long idx = 0L; idx < length(); idx++) {
            result.setElementAt(this.elementAt(idx).negate(), idx);
        }
        return result;
    }

    @Override
    public RealType dotProduct(Vector<RealType> other) {
        if (this.length() != other.length()) {
            throw new ArithmeticException("Cannot compute dot product for vectors of different lengths");
        }
        BigDecimal accum = BigDecimal.ZERO;
        for (long idx = 0L; idx < this.length(); idx++) {
            final BigDecimal lhs = this.elementAt(idx).asBigDecimal();
            final BigDecimal rhs = other.elementAt(idx).asBigDecimal();
            accum = accum.add(lhs.multiply(rhs, mctx), mctx);
        }
        return new RealImpl(accum, mctx);
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

    @Override
    public Vector<RealType> crossProduct(Vector<RealType> other) {
        if (this.length() != other.length()) {
            throw new ArithmeticException("Cannot compute cross product for vectors of different dimension");
        }
        RealVector result;
        if (this.length() == 3L) {
            //  A×B = (a2*b3 - a3*b2, a3*b1 - a1*b3, a1*b2 - a2*b1)
            result = new RealVector(3L);
            result.setElementAt((RealType) this.elementAt(1L).multiply(other.elementAt(2L).subtract(this.elementAt(2L).multiply(other.elementAt(1L)))), 0L);
            result.setElementAt((RealType) this.elementAt(2L).multiply(other.elementAt(0L)).subtract(this.elementAt(0L).multiply(other.elementAt(2L))), 1L);
            result.setElementAt((RealType) this.elementAt(0L).multiply(other.elementAt(1L)).subtract(this.elementAt(1L).multiply(other.elementAt(0L))), 2L);
        } else if (this.length() == 7L) {
            result = new RealVector(7L);
            // see: https://en.wikipedia.org/wiki/Seven-dimensional_cross_product
            for (int y = 0; y < 7; y++) {
                for (int x = 0; x < 7; x++) {
                    long index = cpTable[y][x].getIndex();
                    if (index < 0L) continue;
                    RealType coeff = cpTable[y][x].getCoeff();
                    try {
                        RealType accum = result.elementAt(index) == null ? (RealType) ExactZero.getInstance(mctx).coerceTo(RealType.class) : result.elementAt(index);
                        result.setElementAt((RealType) accum.add(this.elementAt(y).multiply(other.elementAt(x)).multiply(coeff)), index);
                    } catch (CoercionException ce) {
                        throw new IllegalStateException(ce); // we should not get here
                    }
                }
            }
        } else {
            throw new ArithmeticException("Cross product undefined for " + this.length() + " dimensions");
        }
        return result;
    }
    
    /**
     * Compute the angle &theta; between this vector and the given vector.
     * @param other the other vector
     * @return the angle &theta; between this and {@code other}
     */
    @Override
    public RealType computeAngle(Vector<RealType> other) {
        RealType cosine = (RealType) this.dotProduct(other).divide(this.magnitude().multiply(other.magnitude()));
        Numeric angle = MathUtils.arccos(cosine);
        if (angle instanceof RealType) {
            return (RealType) angle;
        } else if (angle instanceof ComplexType) {
            // if there's a complex result for real cosine values, there might just be some negligible
            // garbage in the imaginary part -- if so, it should be safe to return the real part
            Logger.getLogger(RealVector.class.getName()).log(Level.INFO,
                    "arccos computed a complex result {0} while computing the angle between {1} and {2}. Returning the real portion.",
                    new Object[] {angle, this, other});
            return ((ComplexType) angle).real();
        }
        throw new ArithmeticException("arccos computed a non-real result: " + angle);
    }

    @Override
    public void append(RealType element) {
        if (elements.isEmpty()) setMathContext(element.getMathContext());
        elements.add(element);
    }

    @Override
    public Vector<RealType> scale(RealType factor) {
        RealVector result = new RealVector(this.length());
        for (RealType element : elements) {
            result.append((RealType) element.multiply(factor));
        }
        result.setMathContext(mctx);
        return result;
    }

    @Override
    public Vector<RealType> normalize() {
        try {
            final RealType scalefactor = (RealType) this.magnitude().inverse().coerceTo(RealType.class);
            return this.scale(scalefactor);
        } catch (CoercionException ex) {
            Logger.getLogger(RealVector.class.getName()).log(Level.SEVERE, "Coercion of scale factor for normalization failed.", ex);
            throw new IllegalStateException("Could not coerce scale argument to real", ex);
        }
    }
    
    @Override
    public boolean equals(Object o) {
        if (o instanceof Vector) {
            Vector<? extends Numeric> that = (Vector<? extends Numeric>) o;
            if (this.length() != that.length()) return false;
            for (long idx = 0L; idx < this.length(); idx++) {
                if (!this.elementAt(idx).equals(that.elementAt(idx))) return false;
            }
            return true;
        }
        return false;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 37 * hash + Objects.hashCode(this.elements);
        hash = 37 * hash + Objects.hashCode(this.mctx);
        return hash;
    }

    @Override
    public MathContext getMathContext() {
        return mctx;
    }

    @Override
    public String toString() {
        final MathContext displayCtx = new MathContext(4);
        return elements.stream().map(x -> MathUtils.round(x, displayCtx)).map(Object::toString)
                .collect(Collectors.joining(",\u2009", "\u27E8", "\u27E9"));
    }
}
