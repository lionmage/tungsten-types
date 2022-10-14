package tungsten.types.numerics.impl;

import tungsten.types.Numeric;
import tungsten.types.numerics.RealType;
import tungsten.types.numerics.Sign;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class ExactZero extends Zero {
    private ExactZero(MathContext mctx) {
        super(mctx);
    }

    private static final Map<MathContext, ExactZero> instanceMap = new HashMap<>();
    private static final Lock instanceLock = new ReentrantLock();

    public static Numeric getInstance(MathContext mctx) {
        instanceLock.lock();
        try {
            ExactZero instance = instanceMap.get(mctx);
            if (instance == null) {
                instance = new ExactZero(mctx);
                instanceMap.put(mctx, instance);
            }
            return instance;
        } finally {
            instanceLock.unlock();
        }
    }

    @Override
    protected RealType obtainRealZero() {
        return new RealImpl(BigDecimal.ZERO, mctx, true); // explicit exactness
    }

    @Override
    public boolean isExact() {
        return true;
    }

    @Override
    public Sign sign() {
        return Sign.ZERO;
    }

    @Override
    public int compareTo(Numeric o) {
        if (o instanceof Zero) {
            final Zero that = (Zero) o;
            switch (that.sign()) {
                case ZERO:
                    return 0;
                case POSITIVE:
                    return -1;
                case NEGATIVE:
                    return 1;
            }
        }
        return super.compareTo(o);
    }
}
