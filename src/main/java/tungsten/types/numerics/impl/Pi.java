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
package tungsten.types.numerics.impl;

import tungsten.types.Numeric;
import tungsten.types.Range;
import tungsten.types.Set;
import tungsten.types.annotations.Constant;
import tungsten.types.exceptions.CoercionException;
import tungsten.types.functions.ArgMap;
import tungsten.types.functions.ArgVector;
import tungsten.types.functions.NumericFunction;
import tungsten.types.functions.UnaryFunction;
import tungsten.types.functions.impl.Sum;
import tungsten.types.numerics.*;
import tungsten.types.util.MathUtils;
import tungsten.types.util.OptionalOperations;
import tungsten.types.util.RangeUtils;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import java.util.stream.LongStream;
import java.util.stream.Stream;

/**
 * This class provides a representation of the mathematical constant pi (&pi;).
 * The class is not publicly instantiable; it provides a factory method
 * that will give you back an instance of itself for a given {@link MathContext},
 * and keeps a cache of instances that have been generated so that the value
 * of pi only needs to be calculated once for a given precision and
 * {@link RoundingMode}.
 * 
 * Internally, this class uses the BBP formula for deriving Pi to an
 * arbitrary precision.
 * 
 * TODO: It would be nice to refactor this class such that we obtain greater
 * reuse of prior calculations and store less redundant data, but this will
 * require refactoring the BBP formula itself to generate hexadecimal digits
 * and convert those to {@link BigDecimal} objects only when necessary.  Then
 * we would only need to compute new terms for higher precision representations,
 * not recompute already generated terms.  The problem with attempting to do
 * this directly in {@link BigDecimal} objects is that intermediate results
 * must be computed with rounding, and restarting computations from those
 * intermediate results quickly results in deviation from the correct values.
 *
 * @author tarquin
 * @see <a href="https://en.wikipedia.org/wiki/Bailey%E2%80%93Borwein%E2%80%93Plouffe_formula">the Wikipedia article on BBP</a>
 */
@Constant(name = "pi", representation = "\uD835\uDF0B")
public class Pi implements RealType {
    private BigDecimal value;
    private final MathContext mctx;
    
    private Pi(MathContext mctx) {
        this.mctx = mctx;
        calculate();
    }
    
    private static final Lock instanceLock = new ReentrantLock();
    private static final Map<MathContext, Pi> instanceMap = new HashMap<>();
    
    /**
     * Factory method for obtaining an instance of &pi; at a given precision.
     * @param mctx provides the desired precision and {@link RoundingMode} used for internal calculations
     * @return an instance of &pi; to the specified precision
     */
    public static Pi getInstance(MathContext mctx) {
        instanceLock.lock();
        try {
            Pi instance = instanceMap.get(mctx);
            if (instance == null) {
                instance = new Pi(mctx);
                instanceMap.put(mctx, instance);
            }
            return instance;
        } finally {
            instanceLock.unlock();
        }
    }

    @Override
    public boolean isIrrational() {
        return true;
    }

    @Override
    public RealType magnitude() {
        final RealImpl proxy = new RealImpl(value, false);
        proxy.setIrrational(true);
        proxy.setMathContext(mctx);
        return proxy;
    }

    @Override
    public RealType negate() {
        final RealImpl negvalue = new RealImpl(value.negate(), false);
        negvalue.setIrrational(true);
        negvalue.setMathContext(mctx);
        return negvalue;
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
    public boolean isExact() {
        return false;
    }

    @Override
    public boolean isCoercibleTo(Class<? extends Numeric> numtype) {
        if (numtype == Numeric.class) return true;
        NumericHierarchy htype = NumericHierarchy.forNumericType(numtype);
        // can be coerced to real or complex
        return htype.compareTo(NumericHierarchy.REAL) >= 0;
    }

    @Override
    public Numeric coerceTo(Class<? extends Numeric> numtype) throws CoercionException {
        if (numtype == Numeric.class) return this;
        NumericHierarchy htype = NumericHierarchy.forNumericType(numtype);
        switch (htype) {
            case REAL:
                return this;  // it's already a real
            case COMPLEX:
                return new ComplexRectImpl(this, (RealType) ExactZero.getInstance(mctx).coerceTo(RealType.class));
            default:
                throw new CoercionException("Pi can only be coerced to real or complex",
                        this.getClass(), numtype);
        }
    }

    @Override
    public Numeric add(Numeric addend) {
        if (addend instanceof Pi) {
            // to avoid a stack overflow
            RealImpl real = new RealImpl(value.add(((RealType) addend).asBigDecimal(), mctx), false);
            real.setIrrational(true);
            real.setMathContext(mctx);
            return real;
        }
        final Numeric result = addend.add(this);
//        OptionalOperations.setMathContext(result, mctx);
        return result;
    }

    @Override
    public Numeric subtract(Numeric subtrahend) {
        final Numeric result = subtrahend.negate().add(this);
//        OptionalOperations.setMathContext(result, mctx);
        return result;
    }

    @Override
    public Numeric multiply(Numeric multiplier) {
        if (multiplier instanceof Pi) {
            // to avoid stack overflow
            RealImpl real = new RealImpl(value.multiply(((RealType) multiplier).asBigDecimal(), mctx), false);
            real.setIrrational(true);
            real.setMathContext(mctx);
            return real;
        }
        final Numeric result = multiplier.multiply(this);
//        OptionalOperations.setMathContext(result, mctx);
        return result;
    }

    @Override
    public Numeric divide(Numeric divisor) {
        final Numeric result = divisor.inverse().multiply(this);
//        OptionalOperations.setMathContext(result, mctx);
        return result;
    }

    @Override
    public Numeric inverse() {
        return this.magnitude().inverse();
    }

    @Override
    public Numeric sqrt() {
        return this.magnitude().sqrt();
    }

    @Override
    public Set<ComplexType> nthRoots(IntegerType n) {
        return this.magnitude().nthRoots(n);
    }
    
    public long numberOfDigits() {
        return (long) mctx.getPrecision();
    }

    private final Range<RealType> INNER_RANGE = new Range<>(new RealImpl(BigDecimal.ZERO, MathContext.UNLIMITED), Range.BoundType.INCLUSIVE,
            RealInfinity.getInstance(Sign.POSITIVE, MathContext.UNLIMITED), Range.BoundType.EXCLUSIVE);
    private static final IntegerType SIXTEEN = new IntegerImpl(BigInteger.valueOf(16L));
    private static BigInteger longestCalculation = BigInteger.ZERO;
    private static long highestDigit = 0L;
    private static final ReentrantLock stateLock = new ReentrantLock();

    private void calculate() {
        long n = mctx.getPrecision() + 2;
        if (n > highestDigit) {
            stateLock.lock();  // the lock ensures only one constructor mutates these common values at a time
            try {
                for (long k = highestDigit; k <= n; k++) {
                    RationalType sum = computeSpigotSum(k);
                    // discard the non-fraction part
                    RationalType fraction = new RationalImpl(sum.modulus(),
                            sum.denominator(), mctx);
                    IntegerType digit = MathUtils.trunc(fraction.multiply(SIXTEEN));
//                    assert HEX_DIGIT_RANGE.contains(digit);
                    longestCalculation = longestCalculation.shiftLeft(4).or(digit.asBigInteger());
                }
                highestDigit = n;
            } finally {
                stateLock.unlock();
            }
        }
        // check how many digits we actually have and scale so that there's only 1 digit to the left of the decimal point
        IntegerType calculatedState = new IntegerImpl(longestCalculation);
        this.value = new BigDecimal(longestCalculation, (int) (calculatedState.numberOfDigits() - 1L), mctx);
    }

//    private static final Range<IntegerType> HEX_DIGIT_RANGE = new Range<>(new IntegerImpl(BigInteger.ZERO),
//            new IntegerImpl(BigInteger.valueOf(15L)), Range.BoundType.INCLUSIVE);

    private NumericFunction<IntegerType, RationalType> computeSpigotTermFunction(long koffset) {
        final IntegerType offset = new IntegerImpl(BigInteger.valueOf(koffset));

        return new NumericFunction<>() {
            @Override
            public RationalType apply(ArgVector<IntegerType> arguments) {
                IntegerType k = arguments.forVariableName("k");
                IntegerType n = arguments.forVariableName("n");
                IntegerType kplusOffset = (IntegerType) k.add(offset);
                IntegerType exp = (IntegerType) n.subtract(k);
                // modular exponentiation makes this very efficient to compute
                IntegerType numerator = SIXTEEN.powMod(exp, kplusOffset);
                return new RationalImpl(numerator, kplusOffset, mctx);
            }

            @Override
            public long arity() {
                return 2L;
            }

            @Override
            public String[] expectedArguments() {
                return new String[]{"k", "n"};
            }

            @Override
            public Range<RealType> inputRange(String argName) {
                return INNER_RANGE;
            }
        };
    }

    private RationalType computeSpigotSum(long n) {
        NumericFunction<IntegerType, RationalType> subterm1 = computeSpigotTermFunction(1L);
        NumericFunction<IntegerType, RationalType> subterm4 = computeSpigotTermFunction(4L);
        NumericFunction<IntegerType, RationalType> subterm5 = computeSpigotTermFunction(5L);
        NumericFunction<IntegerType, RationalType> subterm6 = computeSpigotTermFunction(6L);
        final IntegerType FOUR = new IntegerImpl(BigInteger.valueOf(4L));
        final IntegerType NEGTWO = new IntegerImpl(BigInteger.valueOf(-2L));

        ArgMap<IntegerType> argMap = new ArgMap<>(Collections.singletonMap("n", new IntegerImpl(BigInteger.valueOf(n))));
        ArgVector<IntegerType> args = new ArgVector<>(new String[]{"k", "n"},
                argMap, new IntegerImpl(BigInteger.ZERO));
        UnaryFunction<IntegerType, RationalType> aggregate = new UnaryFunction<IntegerType, RationalType>("k") {
            @Override
            public RationalType apply(ArgVector<IntegerType> arguments) {
                IntegerType k = arguments.forVariableName("k");
                args.setElementAt(k, args.indexForLabel("k"));
                Numeric result = subterm1.apply(args).multiply(FOUR)
                        .add(subterm4.apply(args).multiply(NEGTWO))
                        .subtract(subterm5.apply(args)).subtract(subterm6.apply(args));
                try {
                    return (RationalType) result.coerceTo(RationalType.class);
                } catch (CoercionException e) {
                    throw new ArithmeticException("Problem computing spigot term for " + args);
                }
            }

            @Override
            public Range<RealType> inputRange(String argName) {
                return INNER_RANGE;
            }
        };
        return LongStream.rangeClosed(0L, n).mapToObj(BigInteger::valueOf).map(IntegerImpl::new)
                .map(aggregate::apply).reduce(Pi::safeReduce).orElseThrow();
    }

    protected static RationalType safeReduce(RationalType A, RationalType B) {
        Numeric sum = A.add(B);
        try {
            return (RationalType) sum.coerceTo(RationalType.class);
        } catch (CoercionException e) {
            throw new IllegalStateException(e);
        }
    }
    
    /*
    Computes the value of pi using the BBP formula.
    */
//    private void calculate() {
//        BigDecimal value = BigDecimal.ZERO;
//        // compute a few extra digits so we can round off later
//        MathContext compctx = new MathContext(mctx.getPrecision() + 4, mctx.getRoundingMode());
//        for (int k = 0; k < mctx.getPrecision() - 1; k++) {
//            value = value.add(computeKthTerm(k, compctx), compctx);
//        }
//        this.value = value.round(mctx);
//    }
    
    private static final BigDecimal TWO  = BigDecimal.valueOf(2L);
    private static final BigDecimal FOUR = BigDecimal.valueOf(4L);
    private static final BigDecimal FIVE = BigDecimal.valueOf(5L);
    private static final BigDecimal SIX  = BigDecimal.valueOf(6L);
    private static final BigDecimal EIGHT = BigDecimal.valueOf(8L);
    private static final BigDecimal SIXTEEN_DEC = BigDecimal.valueOf(16L);
    
    private BigDecimal computeKthTerm(int k, MathContext ctx) {
        BigDecimal kval = BigDecimal.valueOf((long) k);
        BigDecimal scale = BigDecimal.ONE.divide(SIXTEEN_DEC.pow(k, ctx), ctx);
        BigDecimal interm1 = FOUR.divide(EIGHT.multiply(kval, ctx).add(BigDecimal.ONE, ctx), ctx);
        BigDecimal interm2 = TWO.divide(EIGHT.multiply(kval, ctx).add(FOUR, ctx), ctx);
        BigDecimal interm3 = BigDecimal.ONE.divide(EIGHT.multiply(kval, ctx).add(FIVE, ctx), ctx);
        BigDecimal interm4 = BigDecimal.ONE.divide(EIGHT.multiply(kval, ctx).add(SIX, ctx), ctx);
        return interm1.subtract(interm2, ctx).subtract(interm3, ctx).subtract(interm4, ctx).multiply(scale, ctx);
    }
    
    @Override
    public boolean equals(Object o) {
        if (o instanceof Pi) {
            Pi that = (Pi) o;
            if (this.mctx.getRoundingMode() != that.mctx.getRoundingMode()) return false;
            return this.numberOfDigits() == that.numberOfDigits();
        }
        return false;
    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 73 * hash + Objects.hashCode(this.value);
        hash = 73 * hash + Objects.hashCode(this.mctx);
        return hash;
    }

    @Override
    public int compareTo(RealType o) {
        return value.compareTo(o.asBigDecimal());
    }
    
    @Override
    public String toString() {
        // returns the mathematical small italic pi symbol with precision in digits
        return "\uD835\uDF0B[" + numberOfDigits() + "]";
    }

    @Override
    public MathContext getMathContext() {
        return mctx;
    }

    @Override
    public IntegerType floor() {
        return new IntegerImpl(BigInteger.valueOf(3L));
    }

    @Override
    public IntegerType ceil() {
        return new IntegerImpl(BigInteger.valueOf(4L));
    }
}
