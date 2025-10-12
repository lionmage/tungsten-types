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
package tungsten.types.numerics.impl;

import tungsten.types.Numeric;
import tungsten.types.Set;
import tungsten.types.annotations.Constant;
import tungsten.types.annotations.ConstantFactory;
import tungsten.types.annotations.Polar;
import tungsten.types.exceptions.CoercionException;
import tungsten.types.numerics.ComplexType;
import tungsten.types.numerics.IntegerType;
import tungsten.types.numerics.RealType;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static tungsten.types.util.MathUtils.Im;
import static tungsten.types.util.MathUtils.Re;

/**
 * A representation of the imaginary unit &#x2148;, or the unit imaginary number.
 * Note that although this class has been marked with the {@link Polar @Polar interface},
 * it does not have any specific internal representation.  Nevertheless, it is
 * both safe and performant to treat instances of this object as {@code @Polar}
 * where such optimizations exist.
 *
 * @author Robert Poole, <a href="mailto:Tarquin.AZ+Tungsten@gmail.com">Tarquin.AZ@gmail.com</a>
 *   or <a href="mailto:tarquin@alum.mit.edu">MIT alumni e-mail</a>
 */
@Polar
@Constant(name = "imaginary-unit", representation = "\u2148")
public class ImaginaryUnit implements ComplexType {
    private final RealType TWO;

    private final MathContext mctx;
    
    private ImaginaryUnit(MathContext mctx) {
        this.mctx = mctx;
        this.TWO = new RealImpl(BigDecimal.valueOf(2L), mctx);
    }

    private static final Map<MathContext, ImaginaryUnit> instanceMap = new HashMap<>();
    private static final Lock instanceLock = new ReentrantLock();

    @ConstantFactory(returnType = ComplexType.class)
    public static ComplexType getInstance(MathContext mctx) {
        instanceLock.lock();
        try {
            ImaginaryUnit instance = instanceMap.get(mctx);
            if (instance == null) {
                instance = new ImaginaryUnit(mctx);
                instanceMap.put(mctx, instance);
            }
            return instance;
        } finally {
            instanceLock.unlock();
        }
    }

    /**
     * This will always give a result that satisfies
     * {@code One.isUnity(value) == true}.
     * @return a real value equal to 1
     */
    @Override
    public RealType magnitude() {
        return new RealImpl(BigDecimal.ONE, mctx);
    }

    @Override
    public ComplexType negate() {
        return new ComplexRectImpl(real().negate(), imaginary().negate()) {
            @Override
            public RealType argument() {
                return ImaginaryUnit.this.argument().negate();
            }

            @Override
            public ComplexType negate() {
                return ImaginaryUnit.this;
            }

            @Override
            public Numeric multiply(Numeric multiplier) {
                if (multiplier instanceof ImaginaryUnit) {
                    // -i * i = 1
                    return One.getInstance(mctx);
                }
                return super.multiply(multiplier);
            }

            @Override
            public Numeric add(Numeric addend) {
                if (addend instanceof ImaginaryUnit) {
                    // -i is the additive inverse of i
                    return ExactZero.getInstance(mctx);
                }
                return super.add(addend);
            }

            @Override
            public String toString() {
                return "\u2212\u2148";
            }
        };
    }

    @Override
    public ComplexType conjugate() {
        return new ComplexRectImpl(real(), imaginary().negate());
    }

    @Override
    public RealType real() {
        return new RealImpl(BigDecimal.ZERO, mctx);
    }

    @Override
    public RealType imaginary() {
        return new RealImpl(BigDecimal.ONE, mctx);
    }

    /**
     * This will always return &pi;/2.
     * @return the real value equivalent to &pi;/2
     */
    @Override
    public RealType argument() {
        // since this value lies on the positive imaginary axis, the argument is pi/2
        return (RealType) Pi.getInstance(mctx).divide(TWO);
    }

    @Override
    public Set<ComplexType> nthRoots(IntegerType n) {
        ComplexPolarImpl polar = new ComplexPolarImpl(magnitude(), argument(), true);
        return polar.nthRoots(n);
    }

    @Override
    public boolean isExact() {
        return true;
    }

    @Override
    public boolean isCoercibleTo(Class<? extends Numeric> numtype) {
        if (numtype.isAnnotationPresent(Polar.class)) return true;  // special case
        return numtype.isAssignableFrom(ComplexType.class);
    }

    @Override
    public Numeric coerceTo(Class<? extends Numeric> numtype) throws CoercionException {
        if (numtype.isAnnotationPresent(Polar.class)) {
            // an exact representation of pi/2 is not possible, so marking this as not exact
            return new ComplexPolarImpl(magnitude(), argument(), false);
        }
        if (!isCoercibleTo(numtype)) {
            throw new CoercionException(String.format("%s can only be converted to ComplexType", this),
                    this.getClass(), numtype);
        }
        if (numtype == Numeric.class) return this;
        return new ComplexRectImpl(real(), imaginary());
    }

    @Override
    public Numeric add(Numeric addend) {
        if (Zero.isZero(addend)) return this;
        if (addend instanceof ImaginaryUnit) {
            return new ComplexRectImpl(real(), (RealType) imaginary().multiply(TWO));
        }
        if (addend.isCoercibleTo(RealType.class)) {
            try {
                RealType re = (RealType) addend.coerceTo(RealType.class);
                return new ComplexRectImpl(re, imaginary());
            } catch (CoercionException e) {
                throw new ArithmeticException("Addend should be coercible to real, but is not");
            }
        }
        return addend.add(this);
    }

    @Override
    public Numeric subtract(Numeric subtrahend) {
        if (Zero.isZero(subtrahend)) return this;
        if (subtrahend instanceof ImaginaryUnit) {
            return ExactZero.getInstance(mctx);
        }
        if (subtrahend.isCoercibleTo(RealType.class)) {
            try {
                RealType re = (RealType) subtrahend.negate().coerceTo(RealType.class);
                return new ComplexRectImpl(re, imaginary());
            } catch (CoercionException e) {
                throw new ArithmeticException("Subtrahend should be coercible to real, but is not");
            }
        }
        return subtrahend.negate().add(this);
    }

    @Override
    public Numeric multiply(Numeric multiplier) {
        if (Zero.isZero(multiplier)) return ExactZero.getInstance(mctx);
        if (One.isUnity(multiplier)) return this;
        if (multiplier instanceof ImaginaryUnit) {
            // i * i = -1
            return new RealImpl(BigDecimal.valueOf(-1L), mctx);
        }
        // multiplying by i is a rotation
        return new ComplexRectImpl(Im(multiplier).negate(), Re(multiplier));
    }

    @Override
    public Numeric divide(Numeric divisor) {
        if (divisor instanceof ImaginaryUnit) {
            return One.getInstance(mctx);
        }
        return divisor.inverse().multiply(this);
    }

    @Override
    public ComplexType inverse() {
        // 1/i = -i
        return this.negate();
    }

    @Override
    public Numeric sqrt() {
        // Note: this only gives the principal root
        final RealType component = (RealType) TWO.sqrt().inverse();
        return new ComplexRectImpl(component, component);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof ImaginaryUnit) return true;
        if (obj instanceof ComplexType) {
            final ComplexType that = (ComplexType) obj;
            return that.isExact() && Zero.isZero(that.real()) && One.isUnity(that.imaginary());
        }
        return false;
    }

    @Override
    public int hashCode() {
        return 5 + 11 * Objects.hashCode(mctx);
    }

    @Override
    public MathContext getMathContext() {
        return mctx;
    }
    
    @Override
    public String toString() {
        return "\u2148";
    }
}
