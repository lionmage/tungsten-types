package tungsten.types.numerics.impl;

import tungsten.types.Numeric;
import tungsten.annotations.Constant;
import tungsten.types.numerics.RealType;
import tungsten.types.numerics.Sign;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * A representation of zero for situations where zero is being asymptotically
 * approached from a positive value.
 *
 * @author Robert Poole <a href="mailto:Tarquin.AZ@gmail.com">Tarquin.AZ@gmail.com</a>
 */
@Constant(name = "+zero", representation = "+0")
public class PosZero extends Zero {
    private PosZero(MathContext mctx) {
        super(mctx);
    }

    private static final Map<MathContext, PosZero> instanceMap = new HashMap<>();
    private static final Lock instanceLock = new ReentrantLock();

    public static Numeric getInstance(MathContext mctx) {
        instanceLock.lock();
        try {
            PosZero instance = instanceMap.get(mctx);
            if (instance == null) {
                instance = new PosZero(mctx);
                instanceMap.put(mctx, instance);
            }
            return instance;
        } finally {
            instanceLock.unlock();
        }
    }

    @Override
    protected RealType obtainRealZero() {
        return new RealImpl(BigDecimal.ZERO, mctx, false);
    }

    @Override
    public Numeric inverse() {
//        throw new ArithmeticException("Cannot divide by zero");
        return PosInfinity.getInstance(mctx);
    }

    @Override
    public boolean isExact() {
        return false;
    }

    @Override
    public Sign sign() {
        return Sign.POSITIVE;
    }

    @Override
    public String toString() { return "+0"; }

    @Override
    public int compareTo(Numeric o) {
        if (o instanceof PosZero) return 0;
        // Positive 0 is greater than all other zeros
        if (o instanceof Zero) return 1;
        return super.compareTo(o);
    }
}
