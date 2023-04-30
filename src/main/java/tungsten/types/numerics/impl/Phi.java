package tungsten.types.numerics.impl;

import tungsten.types.Numeric;
import tungsten.types.Set;
import tungsten.types.annotations.Constant;
import tungsten.types.annotations.ConstantFactory;
import tungsten.types.exceptions.CoercionException;
import tungsten.types.numerics.*;
import tungsten.types.util.OptionalOperations;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

@Constant(name = "phi", representation = "\u03D5")
public class Phi implements RealType {
    private final MathContext mctx;
    private final BigDecimal value;
    private static final Map<MathContext, Phi> instanceMap = new ConcurrentHashMap<>();

    protected Phi(MathContext mctx) {
        this.mctx = mctx;
        BigDecimal two = BigDecimal.valueOf(2L);
        BigDecimal five = BigDecimal.valueOf(5L);
        this.value = BigDecimal.ONE.add(five.sqrt(mctx), mctx).divide(two, mctx);
    }

    @ConstantFactory(returnType = Phi.class)
    public static Phi getInstance(MathContext mctx) {
        return instanceMap.computeIfAbsent(mctx, Phi::new);
    }

    @Override
    public boolean isIrrational() {
        return true;
    }

    @Override
    public RealType magnitude() {
        return this;
    }

    @Override
    public boolean isExact() {
        return false;
    }

    @Override
    public boolean isCoercibleTo(Class<? extends Numeric> numtype) {
        NumericHierarchy h = NumericHierarchy.forNumericType(numtype);
        return h.compareTo(NumericHierarchy.REAL) >= 0;
    }

    @Override
    public Numeric coerceTo(Class<? extends Numeric> numtype) throws CoercionException {
        if (RealType.class.isAssignableFrom(numtype)) return this;
        else if (ComplexType.class.isAssignableFrom(numtype)) {
            return new ComplexPolarImpl(this);
        }
        throw new CoercionException("Phi cannot be coerced to " + numtype.getTypeName(), this.getClass(), numtype);
    }

    @Override
    public RealType negate() {
        return new RealImpl(value.negate(), mctx);
    }

    @Override
    public Numeric add(Numeric addend) {
        if (addend instanceof ComplexType) {
            return addend.add(this);
        } else if (Zero.isZero(addend)) {
            return this;
        }
        BigDecimal other = OptionalOperations.asBigDecimal(addend);
        return new RealImpl(value.add(other, mctx), mctx, false);
    }

    @Override
    public Numeric subtract(Numeric subtrahend) {
        if (subtrahend instanceof ComplexType) {
            return subtrahend.negate().add(this);
        } else if (Zero.isZero(subtrahend)) {
            return this;
        }
        BigDecimal other = OptionalOperations.asBigDecimal(subtrahend);
        return new RealImpl(value.subtract(other, mctx), mctx, false);
    }

    @Override
    public Numeric multiply(Numeric multiplier) {
        if (multiplier instanceof ComplexType) {
            return multiplier.multiply(this);
        } else if (One.isUnity(multiplier)) {
            return this;
        }
        BigDecimal other = OptionalOperations.asBigDecimal(multiplier);
        return new RealImpl(value.multiply(other, mctx), mctx, false);
    }

    @Override
    public Numeric divide(Numeric divisor) {
        if (divisor instanceof ComplexType) {
            return divisor.inverse().multiply(this);
        } else if (One.isUnity(divisor)) {
            return this;
        }
        BigDecimal other = OptionalOperations.asBigDecimal(divisor);
        return new RealImpl(value.divide(other, mctx), mctx, false);
    }

    @Override
    public Numeric inverse() {
        return new RealImpl(BigDecimal.ONE.divide(value, mctx), mctx, false) {
            @Override
            public boolean isIrrational() {
                return true;
            }

            @Override
            public Numeric inverse() {
                return Phi.this;
            }

            @Override
            public Numeric add(Numeric addend) {
                if (One.isUnity(addend)) {
                    // recurrence relationship: 1 + 1/ϕ = ϕ
                    return Phi.this;
                }
                return super.add(addend);
            }

            @Override
            public String toString() {
                return "1/\u03D5";
            }
        };
    }

    @Override
    public Numeric sqrt() {
        RealImpl result = new RealImpl(value.sqrt(mctx), mctx, false);
        result.setIrrational(true);
        return result;
    }

    @Override
    public MathContext getMathContext() {
        return mctx;
    }

    @Override
    public BigDecimal asBigDecimal() {
        return value;
    }

    @Override
    public Sign sign() {
        return Sign.POSITIVE;
    }

    @Override
    public IntegerType floor() {
        return new IntegerImpl(BigInteger.ONE);
    }

    @Override
    public IntegerType ceil() {
        return new IntegerImpl(BigInteger.valueOf(2L));
    }

    @Override
    public Set<ComplexType> nthRoots(IntegerType n) {
        return new RealImpl(value, mctx, false).nthRoots(n);
    }

    @Override
    public int compareTo(RealType realType) {
        return value.compareTo(realType.asBigDecimal());
    }

    @Override
    public String toString() {
        return "\u03D5";
    }

    @Override
    public int hashCode() {
        return Objects.hash(value, mctx);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof Phi) {
            return mctx.equals(((Phi) obj).getMathContext());
        }
        return false;
    }
}
