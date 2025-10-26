package tungsten.types.numerics.impl;
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

import tungsten.types.Numeric;
import tungsten.types.annotations.ConstantFactory;
import tungsten.types.numerics.RealType;
import tungsten.types.numerics.Sign;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * A representation of an exactly zero value which does not
 * belong to any specific subtype of {@link Numeric}.
 * As such, this is coercible to all {@code Numeric} subtypes.
 *
 * @author Robert Poole, <a href="mailto:Tarquin.AZ@gmail.com">Tarquin.AZ@gmail.com</a>
 */
public class ExactZero extends Zero {
    private ExactZero(MathContext mctx) {
        super(mctx);
    }

    private static final Map<MathContext, ExactZero> instanceMap = new HashMap<>();
    private static final Lock instanceLock = new ReentrantLock();

    @ConstantFactory
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
        if (o instanceof Zero that) {
            return switch (that.sign()) {
                case ZERO -> 0;
                case POSITIVE -> -1;
                case NEGATIVE -> 1;
            };
        }
        return super.compareTo(o);
    }
}
