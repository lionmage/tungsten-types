package tungsten.types.numerics.impl;

import tungsten.types.Numeric;
import tungsten.types.Set;
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
public class PointAtInfinity implements ComplexType {
    private static final PointAtInfinity instance = new PointAtInfinity();

    private PointAtInfinity() {
        // we just need a private default constructor
    }

    public static ComplexType getInstance() {
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
        if (subtrahend instanceof PointAtInfinity) {
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
        if (divisor instanceof PointAtInfinity) throw new ArithmeticException("∞/∞ is undefined");
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
                return new RealImpl(BigDecimal.ONE, MathContext.UNLIMITED);
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
        throw new UnsupportedOperationException("Re(∞) is undefined");
    }

    @Override
    public RealType imaginary() {
        throw new UnsupportedOperationException("Im(∞) is undefined");
    }

    @Override
    public RealType argument() {
        throw new UnsupportedOperationException("arg(∞) is undefined");
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
        return obj instanceof PointAtInfinity;
    }

    @Override
    public String toString() {
        return "\u221E";
    }
}
