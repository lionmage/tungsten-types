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
import tungsten.types.numerics.RealType;
import tungsten.types.numerics.impl.ExactZero;
import tungsten.types.numerics.impl.RealImpl;
import tungsten.types.numerics.impl.Zero;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * An implementation of the zero vector 0&#x20d7;, which has the value of 0
 * for all of its elements.
 *
 * @author Robert Poole <a href="mailto:Tarquin.AZ@gmail.com">Tarquin.AZ@gmail.com</a>
 */
public class ZeroVector implements Vector<Numeric> {
    private final long length;
    private final Numeric zero;
    private final MathContext mctx;
    
    private ZeroVector(long length, MathContext mctx) {
        this.length = length;
        this.mctx = mctx;
        this.zero = ExactZero.getInstance(mctx);
    }
    
    private static final Lock instanceLock = new ReentrantLock();
    private static final Map<Long, ZeroVector> instanceMap = new HashMap<>();
    
    public static ZeroVector getInstance(long length, MathContext ctx) {
        instanceLock.lock();
        try {
            final Long key = computeKey(length, ctx);
            ZeroVector instance = instanceMap.get(key);
            if (instance == null) {
                instance = new ZeroVector(length, ctx);
                instanceMap.put(key, instance);
            }
            return instance;
        } finally {
            instanceLock.unlock();
        }
    }
    
    private static Long computeKey(long length, MathContext ctx) {
        return Long.valueOf(length * 31L + (long) ctx.hashCode());
    }
    
    /**
     * Convenience factory method that returns a {@link ZeroVector} with
     * the same length as the supplied vector.
     * @param vector the vector whose length must be matched
     * @return a zero vector
     */
    public static ZeroVector getInstance(Vector<? extends Numeric> vector) {
        return getInstance(vector.length(), vector.getMathContext());
    }

    @Override
    public long length() {
        return length;
    }

    @Override
    public Numeric elementAt(long position) {
        if (position >= 0L && position < length) {
            return zero;
        }
        throw new IndexOutOfBoundsException("Specified index " + position + " is out of range");
    }

    @Override
    public void setElementAt(Numeric element, long position) {
        throw new UnsupportedOperationException("Zero vector is immutable");
    }

    @Override
    public void append(Numeric element) {
        throw new UnsupportedOperationException("Zero vector is immutable");
    }

    @Override
    public Vector<Numeric> add(Vector<Numeric> addend) {
        return addend;
    }

    @Override
    public Vector<Numeric> subtract(Vector<Numeric> subtrahend) {
        return subtrahend.negate();
    }

    @Override
    public Vector<Numeric> negate() {
        return this;
    }

    @Override
    public Vector<Numeric> scale(Numeric factor) {
        return this;
    }

    @Override
    public RealType magnitude() {
        // explicitly avoiding the use of coerceTo() here
        return new RealImpl(BigDecimal.ZERO, mctx);
    }

    @Override
    public Numeric dotProduct(Vector<Numeric> other) {
        return zero;
    }

    @Override
    public Vector<Numeric> crossProduct(Vector<Numeric> other) {
        return this;
    }

    @Override
    public Vector<Numeric> normalize() {
        throw new UnsupportedOperationException("Zero vector cannot be normalized");
    }

    @Override
    public MathContext getMathContext() {
        return mctx;
    }

    @Override
    public Class<Numeric> getElementType() {
        return Numeric.class;
    }

    @Override
    public RealType computeAngle(Vector<Numeric> other) {
        throw new ArithmeticException("Zero vector cannot form an angle with any vector");
    }

    public static boolean isZeroVector(Vector<? extends Numeric> vector) {
        if (vector instanceof ZeroVector) return true;
        for (long k = 0L; k < vector.length(); k++) {
            if (!Zero.isZero(vector.elementAt(k))) return false;
        }
        // all elements have been matched
        return true;
    }
    
    @Override
    public boolean equals(Object o) {
        if (o instanceof Vector) {
            Vector<? extends Numeric> that = (Vector<? extends Numeric>) o;
            if (that.length() != this.length()) return false;
            return isZeroVector(that);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(zero, length, mctx);
    }

    @Override
    public String toString() {
        return "0\u20D7";  // U+20D7 = over-arrow combining character
    }
}
