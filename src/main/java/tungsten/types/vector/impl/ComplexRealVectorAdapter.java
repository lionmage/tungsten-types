/*
 * The MIT License
 *
 * Copyright © 2018 Robert Poole.
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

import tungsten.types.Vector;
import tungsten.types.numerics.ComplexType;
import tungsten.types.numerics.RealType;
import tungsten.types.numerics.impl.ComplexRectImpl;
import tungsten.types.numerics.impl.RealImpl;
import tungsten.types.numerics.impl.Zero;

import java.math.BigDecimal;
import java.math.MathContext;

/**
 * This class adapts any real vector class to its complex equivalent.
 * Note that {@link ComplexVector} has a copy constructor that takes a
 * {@code Vector<RealType>} as an argument, but this class acts as a
 * lightweight wrapper that only generates new instances of {@code Vector<ComplexType>}
 * when necessary.
 *
 * @author Robert Poole, <a href="mailto:tarquin@alum.mit.edu">MIT alumni e-mail</a>
 *  or <a href="mailto:Tarquin.AZ@gmail.com">Gmail</a>
 */
public class ComplexRealVectorAdapter implements Vector<ComplexType> {
    private final Vector<RealType> realVector;
    private static final RealType ZERO = new RealImpl(BigDecimal.ZERO);
    
    public ComplexRealVectorAdapter(Vector<RealType> realvect) {
        realVector = realvect;
    }

    @Override
    public long length() {
        return realVector.length();
    }

    @Override
    public ComplexType elementAt(long position) {
        final RealType element = realVector.elementAt(position);
        return new ComplexRectImpl(element, ZERO, element.isExact());
    }

    @Override
    public void setElementAt(ComplexType element, long position) {
        if (element.imaginary().equals(ZERO)) {
            realVector.setElementAt(element.real(), position);
        }
        throw new UnsupportedOperationException("Adapter does not support coercion of underlying Vector<RealType>");
    }

    @Override
    public void append(ComplexType element) {
        if (element.imaginary().equals(ZERO)) {
            realVector.append(element.real());
        }
        throw new UnsupportedOperationException("Adapter does not support coercion of underlying Vector<RealType>");
    }

    @Override
    public Vector<ComplexType> add(Vector<ComplexType> addend) {
        if (! (addend instanceof ComplexRealVectorAdapter)) {
            // as long as the addend isn't itself a wrapper, this is
            // more efficient (avoids an extra object creation)
            return addend.add(this);
        }
        return new ComplexVector(realVector).add(addend);
    }

    @Override
    public Vector<ComplexType> subtract(Vector<ComplexType> subtrahend) {
        return new ComplexVector(realVector).subtract(subtrahend);
    }

    @Override
    public Vector<ComplexType> negate() {
        // cheaper to negate the real vector first and then return the wrapper
        return new ComplexRealVectorAdapter(realVector.negate());
    }

    @Override
    public Vector<ComplexType> scale(ComplexType factor) {
        if (Zero.isZero(factor.imaginary())) {
            return new ComplexRealVectorAdapter(realVector.scale(factor.real()));
        }
        return new ComplexVector(realVector).scale(factor);
    }

    @Override
    public ComplexType magnitude() {
        return new ComplexRectImpl(realVector.magnitude(), ZERO);
    }

    @Override
    public ComplexType dotProduct(Vector<ComplexType> other) {
        // the dot product is not commutative, so we must preserve order
        return new ComplexVector(realVector).dotProduct(other);
    }

    @Override
    public Vector<ComplexType> crossProduct(Vector<ComplexType> other) {
        return new ComplexVector(realVector).crossProduct(other);
    }

    @Override
    public Vector<ComplexType> normalize() {
        return new ComplexRealVectorAdapter(realVector.normalize());
    }

    @Override
    public RealType computeAngle(Vector<ComplexType> other) {
        // the dot product is not commutative, so we must preserve the order
        return new ComplexVector(realVector).computeAngle(other);
    }

    @Override
    public MathContext getMathContext() {
        return realVector.getMathContext();
    }
}
