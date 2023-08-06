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
import tungsten.types.exceptions.CoercionException;
import tungsten.types.numerics.IntegerType;
import tungsten.types.numerics.RealType;
import tungsten.types.numerics.impl.IntegerImpl;
import tungsten.types.numerics.impl.One;
import tungsten.types.numerics.impl.RealImpl;
import tungsten.types.util.MathUtils;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A very simple implementation of a {@link Vector<IntegerType>}, intended
 * for use with e.g. {@link tungsten.types.matrix.impl.CauchyMatrix Cauchy matrices}
 * but suitable for any application where integer elements are acceptable.
 * @author Robert Poole <a href="mailto:tarquin@alum.mit.edu">MIT alumni e-mail</a> or
 *   <a href="mailto:Tarquin.AZ@gmail.com">Gmail</a>
 */
public class IntVector implements Vector<IntegerType> {
    private final BigInteger[] elements;
    private MathContext mctx = MathContext.UNLIMITED;

    protected IntVector(BigInteger[] elements) {
        this.elements = elements;
    }

    public IntVector(long size, MathContext ctx) {
        elements = new BigInteger[(int) size];
        Arrays.fill(elements, BigInteger.ZERO);
        this.mctx = ctx;
    }

    public IntVector(Long... elements) {
        this.elements = Arrays.stream(elements).map(BigInteger::valueOf).toArray(BigInteger[]::new);
    }

    public IntVector(List<IntegerType> elements) {
        MathContext ctx = MathUtils.inferMathContext(elements);
        this.elements = elements.stream().map(IntegerType::asBigInteger).toArray(BigInteger[]::new);
        this.mctx = ctx;
    }

    @Override
    public long length() {
        return elements.length;
    }

    @Override
    public IntegerType elementAt(long position) {
        if (position < 0L || position >= length()) {
            throw new IndexOutOfBoundsException("Index " + position + " is outside the range 0\u2013" + length());
        }
        return new IntegerImpl(elements[(int) position]) {
            @Override
            public MathContext getMathContext() {
                return mctx;
            }
        };
    }

    @Override
    public void setElementAt(IntegerType element, long position) {
        if (position < 0L || position >= length()) {
            throw new IndexOutOfBoundsException("Index " + position + " is outside the range 0\u2013" + length());
        }
        elements[(int) position] = element.asBigInteger();
    }

    @Override
    public void append(IntegerType element) {
        throw new UnsupportedOperationException("This vector implementation does not support appending elements");
    }

    @Override
    public Vector<IntegerType> add(Vector<IntegerType> addend) {
        return null;
    }

    @Override
    public Vector<IntegerType> subtract(Vector<IntegerType> subtrahend) {
        return null;
    }

    @Override
    public Vector<IntegerType> negate() {
        BigInteger[] negElements = Arrays.stream(elements).map(BigInteger::negate)
                .toArray(BigInteger[]::new);
        IntVector result = new IntVector(negElements);
        result.setMathContext(mctx);
        return result;
    }

    @Override
    public Vector<IntegerType> scale(IntegerType factor) {
        BigInteger[] scaled = Arrays.stream(elements).map(x -> x.multiply(factor.asBigInteger()))
                .toArray(BigInteger[]::new);
        IntVector result = new IntVector(scaled);
        result.setMathContext(mctx);
        return result;
    }

    @Override
    public RealType magnitude() {
        BigDecimal val = Arrays.stream(elements).map(x -> x.multiply(x)).reduce(BigInteger::add)
                .map(BigDecimal::new).map(x -> x.sqrt(mctx)).orElseThrow(() -> new ArithmeticException("Error while computing magnitude"));
        return new RealImpl(val, mctx);
    }

    @Override
    public Class<IntegerType> getElementType() {
        return IntegerType.class;
    }

    @Override
    public IntegerType dotProduct(Vector<IntegerType> other) {
        if (other.length() != this.length()) {
            throw new ArithmeticException("Dot product requires vectors of the same dimension");
        }
        BigInteger result = BigInteger.ZERO;
        for (long k = 0L; k < length(); k++) {
            result = result.add(elements[(int) k].multiply(other.elementAt(k).asBigInteger()));
        }
        return new IntegerImpl(result) {
            @Override
            public MathContext getMathContext() {
                return mctx;
            }
        };
    }

    @Override
    public Vector<IntegerType> crossProduct(Vector<IntegerType> other) {
        if (length() != 3L || other.length() != 3L) {
            throw new UnsupportedOperationException("Cross product is only supported for 3-dimensional vectors");
        }
        BigInteger bx = elements[0];
        BigInteger by = elements[1];
        BigInteger bz = elements[2];
        BigInteger cx = other.elementAt(0L).asBigInteger();
        BigInteger cy = other.elementAt(1L).asBigInteger();
        BigInteger cz = other.elementAt(2L).asBigInteger();
        BigInteger ax = by.multiply(cz).subtract(bz.multiply(cy));
        BigInteger ay = bz.multiply(cx).subtract(bx.multiply(cz));
        BigInteger az = bx.multiply(cy).subtract(by.multiply(cx));
        IntVector result = new IntVector(new BigInteger[] {ax, ay, az});
        result.setMathContext(mctx);
        return result;
    }

    @Override
    public Vector<IntegerType> normalize() {
        if (One.isUnity(this.magnitude())) return this;  // it's already a unit vector
        throw new UnsupportedOperationException("Cannot normalize this IntVector");
    }

    @Override
    public RealType computeAngle(Vector<IntegerType> other) {
        try {
            RealType cosine = (RealType) this.dotProduct(other).divide(this.magnitude().multiply(other.magnitude()))
                    .coerceTo(RealType.class);
            return (RealType) MathUtils.arccos(cosine).coerceTo(RealType.class);
        } catch (CoercionException e) {
            throw new ArithmeticException("Unable to compute angle between vectors");
        }
    }

    @Override
    public MathContext getMathContext() {
        return mctx;
    }

    public void setMathContext(MathContext mctx) {
        this.mctx = mctx;
    }

    public Stream<IntegerType> stream() {
        return Arrays.stream(elements).map(IntegerImpl::new);
    }

    @Override
    public int hashCode() {
        return 7 * Arrays.hashCode(elements) + 3 * mctx.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof Vector) {
            if (obj instanceof IntVector) {
                IntVector that = (IntVector) obj;
                return Arrays.equals(this.elements, that.elements);
            }
            Vector<? extends Numeric> other = (Vector<? extends Numeric>) obj;
            if (this.length() == other.length()) {
                // testing for numeric equality, which should work even across types
                for (long k = 0L; k < length(); k++) {
                    if (!this.elementAt(k).equals(other.elementAt(k))) return false;
                }
                return true;
            }
        }
        return false;
    }

    @Override
    public String toString() {
//        MATHEMATICAL LEFT ANGLE BRACKET (U+27E8, Ps): ⟨
//        MATHEMATICAL RIGHT ANGLE BRACKET (U+27E9, Pe): ⟩
        return Arrays.stream(elements).map(BigInteger::toString).collect(Collectors.joining(",\u2009", "\u27E8", "\u27E9"));
    }
}
