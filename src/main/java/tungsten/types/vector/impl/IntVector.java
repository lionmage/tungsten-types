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
 * A very simple implementation of a {@code Vector<IntegerType>}, intended
 * for use with e.g. {@link tungsten.types.matrix.impl.CauchyMatrix Cauchy matrices}
 * but suitable for any application where integer elements are acceptable.
 * @author Robert Poole &mdash; <a href="mailto:tarquin@alum.mit.edu">MIT alumni e-mail</a> or
 *   <a href="mailto:Tarquin.AZ@gmail.com">Gmail</a>
 */
public class IntVector implements Vector<IntegerType> {
    private final BigInteger[] elements;
    private MathContext mctx = MathContext.UNLIMITED;

    /**
     * Construct an {@code IntVector} from an array of {@code BigInteger}s.
     * @param elements an array of {@code BigInteger} values
     */
    protected IntVector(BigInteger[] elements) {
        this.elements = elements;
    }

    /**
     * Initialize an IntVector with a given length and {@code MathContext}.
     * All elements are initialized to 0.
     * @param size the length of this vector
     * @param ctx  the {@code MathContext} for this vector
     */
    public IntVector(long size, MathContext ctx) {
        elements = new BigInteger[(int) size];
        Arrays.fill(elements, BigInteger.ZERO);
        this.mctx = ctx;
    }

    /**
     * Initialize an IntVector with a sequence of {@code long} values.
     * @param elements one or more {@code Long} values
     */
    public IntVector(Long... elements) {
        this.elements = Arrays.stream(elements).map(BigInteger::valueOf).toArray(BigInteger[]::new);
    }

    /**
     * Initialize an IntVector with a {@code List} of {@code IntegerType} values.
     * The {@code MathContext} is inferred from the supplied {@code List<IntegerType>}.
     * @param elements the elements of this vector, in order
     */
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
            throw new IndexOutOfBoundsException("Index " + position + " is outside the range 0\u2013" + (length() - 1L));
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
            throw new IndexOutOfBoundsException("Index " + position + " is outside the range 0\u2013" + (length() - 1L));
        }
        elements[(int) position] = element.asBigInteger();
        if (mctx.getPrecision() == 0 && element.getMathContext().getPrecision() > 0) {
            mctx = element.getMathContext();
        }
    }

    @Override
    public void append(IntegerType element) {
        throw new UnsupportedOperationException("This vector implementation does not support appending elements");
    }

    @Override
    public Vector<IntegerType> add(Vector<IntegerType> addend) {
        if (addend.length() != this.length()) {
            throw new ArithmeticException("Addend must have the same dimension as this vector");
        }
        BigInteger[] sum = new BigInteger[elements.length];
        for (int k = 0; k < sum.length; k++) {
            sum[k] = elements[k].add(addend.elementAt(k).asBigInteger());
        }
        final MathContext ctx = mctx.getPrecision() == 0 ? addend.getMathContext() : mctx;
        IntVector result = new IntVector(sum);
        result.setMathContext(ctx);
        return result;
    }

    @Override
    public Vector<IntegerType> subtract(Vector<IntegerType> subtrahend) {
        if (subtrahend.length() != this.length()) {
            throw new ArithmeticException("Subtrahend must have the same dimension as this vector");
        }
        BigInteger[] diff = new BigInteger[elements.length];
        for (int k = 0; k < diff.length; k++) {
            diff[k] = elements[k].subtract(subtrahend.elementAt(k).asBigInteger());
        }
        final MathContext ctx = mctx.getPrecision() == 0 ? subtrahend.getMathContext() : mctx;
        IntVector result = new IntVector(diff);
        result.setMathContext(ctx);
        return result;
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
        final MathContext ctx = mctx.getPrecision() == 0 ? factor.getMathContext() : mctx;
        IntVector result = new IntVector(scaled);
        result.setMathContext(ctx);
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
        // if it's already a unit vector, return self
        if (Arrays.stream(elements).map(BigInteger::abs).filter(BigInteger.ONE::equals).count() == 1L) return this;
        // the following is slower and involves computing a square root
//        if (One.isUnity(this.magnitude())) return this;
        throw new UnsupportedOperationException("Cannot normalize this IntVector");
    }

    @Override
    public RealType computeAngle(Vector<IntegerType> other) {
        try {
            RealType cosine = (RealType) this.dotProduct(other).divide(this.magnitude().multiply(other.magnitude()))
                    .coerceTo(RealType.class);
            return (RealType) MathUtils.arccos(cosine).coerceTo(RealType.class);
        } catch (CoercionException e) {
            throw new ArithmeticException("Unable to compute angle between vectors " + this + " and " + other);
        }
    }

    @Override
    public MathContext getMathContext() {
        return mctx;
    }

    /**
     * Set the {@code MathContext} for this vector.
     * @param mctx the {@code MathContext}
     */
    public void setMathContext(MathContext mctx) {
        this.mctx = mctx;
    }

    /**
     * Obtain a {@link Stream} of values contained by this vector, in index order.
     * @return a stream of {@code IntegerType} values
     */
    public Stream<IntegerType> stream() {
        return Arrays.stream(elements).map(IntegerImpl::new);
    }

    @Override
    public int hashCode() {
        return 7 * Arrays.hashCode(elements) + 3 * mctx.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof Vector<? extends Numeric> other) {
            if (obj instanceof IntVector that) {
                return Arrays.equals(this.elements, that.elements);
            }
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
