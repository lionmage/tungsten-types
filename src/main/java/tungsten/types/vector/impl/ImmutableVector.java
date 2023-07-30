package tungsten.types.vector.impl;
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

import tungsten.types.Numeric;
import tungsten.types.Vector;
import tungsten.types.numerics.RealType;

import java.math.MathContext;

/**
 * A simple wrapper class for vectors which provides a read-only view
 * of the wrapped vector.  Any methods that produce a vector as a result
 * also wrap this result in an {@link ImmutableVector}.
 * @param <T> any {@link Numeric} (sub)type
 * @author Robert Poole <a href="mailto:tarquin@alum.mit.edu">MIT alumni e-mail</a>
 *   or <a href="mailto:Tarquin.AZ@gmail.com">Gmail</a>
 */
public class ImmutableVector<T extends Numeric> implements Vector<T> {
    private final Vector<T> wrapped;

    /**
     * A constructor that takes a {@link Vector} of elements
     * that are of type {@link T} or are a subtype of {@link T}.
     * This allows the construction of a read-only view of
     * {@code source} which presents elements of {@code source}
     * as their supertype.
     * @param source a vector instance that is not immutable
     */
    public ImmutableVector(Vector<? extends T> source) {
        if (source instanceof ImmutableVector) {
            throw new IllegalArgumentException("Wrapping an ImmutableVector inside another ImmutableVector is not allowed");
        }
        wrapped = (Vector<T>) source;
    }

    @Override
    public long length() {
        return wrapped.length();
    }

    @Override
    public T elementAt(long position) {
        return wrapped.elementAt(position);
    }

    private static final String IMMUTABLE_TEXT = "Immutable vector instance";

    @Override
    public void setElementAt(T element, long position) {
        throw new UnsupportedOperationException(IMMUTABLE_TEXT);
    }

    @Override
    public void append(T element) {
        throw new UnsupportedOperationException(IMMUTABLE_TEXT);
    }

    @Override
    public Vector<T> add(Vector<T> addend) {
        return new ImmutableVector<>(wrapped.add(addend));
    }

    @Override
    public Vector<T> subtract(Vector<T> subtrahend) {
        return new ImmutableVector<>(wrapped.subtract(subtrahend));
    }

    @Override
    public Vector<T> negate() {
        return new ImmutableVector<>(wrapped.negate());
    }

    @Override
    public Vector<T> scale(T factor) {
        return new ImmutableVector<>(wrapped.scale(factor));
    }

    @Override
    public T dotProduct(Vector<T> other) {
        return wrapped.dotProduct(other);
    }

    @Override
    public Vector<T> crossProduct(Vector<T> other) {
        return new ImmutableVector<>(wrapped.crossProduct(other));
    }

    @Override
    public Vector<T> normalize() {
        return new ImmutableVector<>(wrapped.normalize());
    }

    @Override
    public RealType computeAngle(Vector<T> other) {
        return wrapped.computeAngle(other);
    }

    @Override
    public MathContext getMathContext() {
        return wrapped.getMathContext();
    }
}
