package tungsten.types.numerics.impl;
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
import tungsten.types.Set;
import tungsten.types.annotations.Constant;
import tungsten.types.annotations.ConstantFactory;
import tungsten.types.exceptions.CoercionException;
import tungsten.types.numerics.ComplexType;
import tungsten.types.numerics.IntegerType;
import tungsten.types.numerics.RealType;
import tungsten.types.numerics.Sign;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.List;

/**
 * A representation of complex infinity, a.k.a. the point at infinity,
 * denoted &infin;. Note that there is no positive or negative infinity
 * for the extended complex plane, &#x2102;&thinsp;&cup;&thinsp;{&infin;},
 * also denoted &#x2102;<sub>&infin;</sub>.
 * @see <a href="https://en.wikipedia.org/wiki/Riemann_sphere#Extended_complex_numbers">Extended Complex Numbers</a>
 * @see <a href="https://math.libretexts.org/Bookshelves/Analysis/Complex_Variables_with_Applications_(Orloff)/02%3A_Analytic_Functions/2.04%3A_The_Point_at_Infinity">
 *     an article on the Point at Infinity</a>
 * @see <a href="https://proofwiki.org/wiki/Definition:Complex_Point_at_Infinity">the definition at ProofWiki</a>
 */
@Constant(name = "cplx-infinity", representation="\u221E")
public class PointAtInfinity implements ComplexType {
    private static final PointAtInfinity instance = new PointAtInfinity();

    private PointAtInfinity() {
        // we just need a private default constructor
    }

    @ConstantFactory(noArgs = true, returnType = ComplexType.class)
    public static ComplexType getInstance() {
        if (!ComplexType.isExtendedEnabled()) {
            throw new ArithmeticException("Extended complex numbers \u2102\u222A{\u221E} are not enabled");
        }
        return instance;
    }

    @Override
    public boolean isExact() {
        return true;  // there is a single point at infinity
    }

    @Override
    public boolean isCoercibleTo(Class<? extends Numeric> numtype) {
        return ComplexType.class.isAssignableFrom(numtype);
    }

    @Override
    public Numeric coerceTo(Class<? extends Numeric> numtype) throws CoercionException {
        if (isCoercibleTo(numtype)) {
            return this;
        }
        throw new CoercionException("\u221E has limited coercibility", PointAtInfinity.class, numtype);
    }

    @Override
    public Numeric add(Numeric addend) {
        return this;
    }

    @Override
    public Numeric subtract(Numeric subtrahend) {
        if (this.equals(subtrahend)) {
            throw new ArithmeticException("∞ \u2212 ∞ is undefined");
        }
        return this;
    }

    @Override
    public Numeric multiply(Numeric multiplier) {
        if (Zero.isZero(multiplier)) {
            throw new ArithmeticException("0 \u22C5 ∞ is undefined");
        }
        return this;
    }

    @Override
    public Numeric divide(Numeric divisor) {
        if (this.equals(divisor)) throw new ArithmeticException("∞/∞ is undefined");
        return this;
    }

    @Override
    public Numeric sqrt() {
        return this;
    }

    @Override
    public MathContext getMathContext() {
        return MathContext.UNLIMITED;
    }

    @Override
    public RealType magnitude() {
        return RealInfinity.getInstance(Sign.POSITIVE, MathContext.UNLIMITED);
    }

    @Override
    public ComplexType negate() {
        throw new ArithmeticException("There is no negative for ∞");
    }

    @Override
    public ComplexType inverse() {
        // inverse of the point at infinity is zero
        return new ComplexType() {
            @Override
            public RealType magnitude() {
                return new RealImpl(BigDecimal.ZERO, MathContext.UNLIMITED);
            }

            @Override
            public ComplexType negate() {
                return this;
            }

            @Override
            public ComplexType inverse() {
                return PointAtInfinity.getInstance();
            }

            @Override
            public ComplexType conjugate() {
                return this;
            }

            @Override
            public RealType real() {
                return new RealImpl(BigDecimal.ZERO, MathContext.UNLIMITED);
            }

            @Override
            public RealType imaginary() {
                return new RealImpl(BigDecimal.ZERO, MathContext.UNLIMITED);
            }

            @Override
            public RealType argument() {
                return new RealImpl(BigDecimal.ZERO, MathContext.UNLIMITED);
            }

            @Override
            public Set<ComplexType> nthRoots(IntegerType n) {
                return Set.of(this);
            }

            @Override
            public boolean isExact() {
                return true;
            }

            @Override
            public boolean isCoercibleTo(Class<? extends Numeric> numtype) {
                final List<Class<? extends Numeric>> allowedTypes =
                        List.of(PosZero.class, ExactZero.class, RealType.class, ComplexType.class);
                return allowedTypes.stream().anyMatch(t -> t.isAssignableFrom(numtype));
            }

            @Override
            public Numeric coerceTo(Class<? extends Numeric> numtype) throws CoercionException {
                if (PosZero.class.isAssignableFrom(numtype)) {
                    return PosZero.getInstance(MathContext.UNLIMITED);
                } else if (ExactZero.class.isAssignableFrom(numtype)) {
                    return ExactZero.getInstance(MathContext.UNLIMITED);
                } else if (RealType.class.isAssignableFrom(numtype)) {
                    return new RealImpl(BigDecimal.ZERO, MathContext.UNLIMITED);
                } else if (ComplexType.class.isAssignableFrom(numtype)) {
                    return this;
                }
                throw new CoercionException("Cannot convert special complex zero to specified type",
                        this.getClass(), numtype);
            }

            @Override
            public Numeric add(Numeric addend) {
                return addend;
            }

            @Override
            public Numeric subtract(Numeric subtrahend) {
                return subtrahend.negate();
            }

            @Override
            public Numeric multiply(Numeric multiplier) {
                if (multiplier instanceof PointAtInfinity) {
                    throw new ArithmeticException("0 \u22C5 ∞ is undefined");
                }
                return this;
            }

            @Override
            public Numeric divide(Numeric divisor) {
                if (Zero.isZero(divisor)) throw new ArithmeticException("0/0 is undefined");
                return this;
            }

            @Override
            public Numeric sqrt() {
                return this;
            }

            @Override
            public MathContext getMathContext() {
                return MathContext.UNLIMITED;
            }

            @Override
            public int hashCode() {
                return 0;
            }

            @Override
            public boolean equals(Object obj) {
                if (obj instanceof Numeric) {
                    return Zero.isZero((Numeric) obj);
                }
                return false;
            }

            @Override
            public String toString() {
                return "0";
            }
        };
    }

    @Override
    public ComplexType conjugate() {
        return this;
    }

    @Override
    public RealType real() {
        throw new ArithmeticException("Re(∞) is undefined");
    }

    @Override
    public RealType imaginary() {
        throw new ArithmeticException("Im(∞) is undefined");
    }

    @Override
    public RealType argument() {
        throw new ArithmeticException("arg(∞) is undefined");
    }

    @Override
    public Set<ComplexType> nthRoots(IntegerType n) {
        return Set.of(this);
    }

    @Override
    public int hashCode() {
        return Integer.MAX_VALUE;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) return false;
        return obj instanceof PointAtInfinity;
    }

    @Override
    public String toString() {
        // the infinity subscript was a dirty hack, so this should render better
        return "\u221E \u2208 \u2102\u222A{\u221E}";
    }
}
