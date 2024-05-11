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
package tungsten.types.util;

import tungsten.types.annotations.Constant;
import tungsten.types.annotations.Experimental;
import tungsten.types.Set;
import tungsten.types.Vector;
import tungsten.types.*;
import tungsten.types.annotations.Columnar;
import tungsten.types.annotations.Polar;
import tungsten.types.exceptions.CoercionException;
import tungsten.types.exceptions.ConvergenceException;
import tungsten.types.functions.UnaryFunction;
import tungsten.types.functions.impl.Exp;
import tungsten.types.functions.impl.NaturalLog;
import tungsten.types.functions.impl.Negate;
import tungsten.types.matrix.impl.*;
import tungsten.types.numerics.*;
import tungsten.types.numerics.impl.*;
import tungsten.types.set.impl.NumericSet;
import tungsten.types.vector.ColumnVector;
import tungsten.types.vector.RowVector;
import tungsten.types.vector.impl.*;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.math.RoundingMode;
import java.text.DecimalFormatSymbols;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import java.util.stream.StreamSupport;

import static tungsten.types.Range.BoundType;

/**
 * A utility class to hold commonly used functions and algorithms.
 *
 * @author Robert Poole, <a href="mailto:tarquin@alum.mit.edu">MIT alumni e-mail</a> or <a href="mailto:Tarquin.AZ@gmail.com">Gmail</a>
 */
public class MathUtils {
    public static final String THETA = "\u03B8";
    /**
     * The {@link String} representing the System property that
     * governs whether internally-provided Java math operations
     * are to be preferred during calculation.
     */
    public static final String PREFER_INBUILT = "tungsten.types.numerics.MathUtils.prefer.native";
    /**
     * The {@link String} representing the System property that
     * governs how many terms to compute for &#x1D6AA;(z) using
     * the Weierstrass method.  This value is multiplied by the requested
     * precision of the result to provide the total number of terms
     * (and hence, multiplicative iterations) to compute.<br>
     * The default value is 2048.
     */
    public static final String GAMMA_TERM_SCALE = "tungsten.types.numerics.MathUtils.Gamma.termScale";
    /**
     * The implementation of Weierstrass' method of computing &#x1D6AA;(z) is multi-threaded
     * for performance, with the complete series of N terms subdivided into blockSize groups
     * which are computed separately, then multiplied together in-order to produce a result.
     * The System property that governs this value is represented by this {@link String},
     * and the default value is 250.
     */
    public static final String GAMMA_BLOCK_SIZE = "tungsten.types.numerics.MathUtils.Gamma.blockSize";
    /**
     * The Gamma function &#x1D6AA;(z) can be approximated near zero much more quickly
     * than computing a series.  Any value |z|&nbsp;&lt;&nbsp;&epsilon; would be considered
     * close enough to 0 to use the approximation.  The System property that governs this
     * &epsilon; value is represented by this {@link String}, and the default value is 0.01.
     */
    public static final String GAMMA_NEIGHBORHOOD_OF_ZERO = "tungsten.types.numerics.MathUtils.Gamma.zeroNeighborhood";

    private static final Map<Long, BigInteger> factorialCache = new HashMap<>();

    private MathUtils() {
        // to prevent instantiation
    }

    /**
     * Checks configuration and returns {@code true} if we should
     * prefer built-in operations (i.e., those supplied by Java) over
     * implementations supplied by Tungsten.  Typically, built-in
     * operations execute faster at the expense of lower accuracy.
     * For example, {@link BigDecimal#pow(int, MathContext)} returns
     * a result that is accurate to within 2 ULPs, whereas
     * {@link #computeIntegerExponent(RealType, long, MathContext)}
     * may give more accurate results at the cost of slower performance.
     * @return true if the system is configured to prefer Java-supplied
     *   operations, false otherwise
     * @see #PREFER_INBUILT
     */
    public static boolean useBuiltInOperations() {
        String value = System.getProperty(PREFER_INBUILT, "true");
        return Boolean.parseBoolean(value);
    }

    /**
     * Compute n! &mdash; the factorial of integer value n.
     * Note that this implementation uses caching of previously
     * computed values both for short-circuit evaluation
     * and for computing new values.  Caching for values
     * of n&nbsp;&gt;&nbsp;{@link Long#MAX_VALUE} is not
     * guaranteed.
     * @param n a non-negative integer value
     * @return the value of n!
     */
    public static IntegerType factorial(IntegerType n) {
        if (n.sign() == Sign.NEGATIVE) throw new IllegalArgumentException("Factorial undefined for " + n);
        if (n.asBigInteger().equals(BigInteger.ZERO) || n.asBigInteger().equals(BigInteger.ONE)) {
            return new IntegerImpl(BigInteger.ONE) {
                @Override
                public MathContext getMathContext() {
                    return n.getMathContext(); // preserve MathContext
                }
            };
        } else if (getCacheFor(n) != null) {
            return new IntegerImpl(getCacheFor(n)) {
                @Override
                public MathContext getMathContext() {
                    return n.getMathContext();
                }
            };
        }
        
        Long m = findMaxKeyUnder(n);
        
        BigInteger accum = m != null ? factorialCache.get(m) : BigInteger.ONE;
        BigInteger intermediate = n.asBigInteger();
        BigInteger bailout = m != null ? BigInteger.valueOf(m + 1L) : BigInteger.TWO;
        while (intermediate.compareTo(bailout) >= 0) {
            accum = accum.multiply(intermediate);
            intermediate = intermediate.subtract(BigInteger.ONE);
        }
        cacheFact(n, accum);
        return new IntegerImpl(accum) {
            @Override
            public MathContext getMathContext() {
                return n.getMathContext();  // preserve MathContext
            }
        };
    }

    /**
     * If there's a cached factorial value, find the highest key that is less
     * than n.
     * @param n the upper bound of our search
     * @return the highest cache key given the search parameter, or {@code null}
     *   if no key is found
     */
    private static Long findMaxKeyUnder(IntegerType n) {
        try {
            final long ncmp = n.asBigInteger().longValueExact();
            return factorialCache.keySet().parallelStream().filter(x -> x < ncmp).max(Long::compareTo).orElse(null);
        } catch (ArithmeticException e) {
            Logger.getLogger(MathUtils.class.getName()).log(Level.FINER, "Attempt to find a max key < n outside Long range.", e);
            // return the biggest key we can find since the given upper bound is too large for the cache
            return factorialCache.keySet().parallelStream().max(Long::compareTo).orElse(null);
        }
    }
    
    private static final BigInteger MAX_LONG = BigInteger.valueOf(Long.MAX_VALUE);
    
    private static void cacheFact(BigInteger n, BigInteger value) {
        // these bounds should prevent an ArithmeticException from being thrown
        // if not, we want to fail fast to catch the problem
        if (n.compareTo(BigInteger.TWO) >= 0 && n.compareTo(MAX_LONG) < 0) {
            Long key = n.longValueExact();
            
            if (!factorialCache.containsKey(key)) factorialCache.put(key, value);
        }
    }
    
    private static void cacheFact(IntegerType n, BigInteger value) {
        cacheFact(n.asBigInteger(), value);
    }
    
    private static BigInteger getCacheFor(BigInteger n) {
        try {
            return factorialCache.get(n.longValueExact());
        } catch (ArithmeticException e) {
            Logger.getLogger(MathUtils.class.getName()).log(Level.FINER, "Attempt to access cache of factorial value for n outside Long range.", e);
            return null; // this is the same as if we had a regular cache miss
        }
    }
    
    private static BigInteger getCacheFor(IntegerType n) {
        return getCacheFor(n.asBigInteger());
    }

    /**
     * Compute the gamma function, &#x1D6AA;(z) for any value z.
     * Note that this is a generalization of factorial; for an
     * integer value z, &#x1D6AA;(z)&nbsp;=&nbsp;(z&thinsp;&minus;&thinsp;1)!
     * <br>Note also that this function converges very slowly for
     * non-integer and non-half-integer values. Currently, given z with {@link MathContext#DECIMAL128}
     * precision, we obtain about 6 digits of accuracy with the property {@link #GAMMA_TERM_SCALE} set to
     * its default value.  This may be revisited in the future.
     * <br>Since the implementation of Weierstrass' formula is written to be concurrent,
     * the user is encouraged to set {@link #GAMMA_TERM_SCALE} to be as large as tolerable,
     * and to adjust {@link #GAMMA_BLOCK_SIZE} accordingly.
     * <br>The <a href="https://en.wikipedia.org/wiki/Lanczos_approximation">Lanczos approximation</a>
     * was not chosen because it requires special handling (e.g., it only works for values &gt; 1&#x2044;2),
     * necessitating reflection around the imaginary axis using &#x1D6AA; identities.  Weierstrass
     * works for the entire complex plane, and concurrency allows us to compute many more terms in
     * a reasonable amount of time.  Lanczos also requires choosing two special values (g and n) which are not
     * entirely arbitrary, and for which no combination can be generated that supports precision beyond
     * a given point, making it an <a href="https://mrob.com/pub/ries/lanczos-gamma.html">unsuitable algorithm</a>
     * for arbitrary precision math.
     * @param z the argument to this function
     * @return the value of &#x1D6AA;(z)
     * @see #GAMMA_TERM_SCALE
     * @see #GAMMA_BLOCK_SIZE
     * @see <a href="https://mathworld.wolfram.com/GammaFunction.html">a comprehensive article at Wolfram MathWorld</a>
     * @see <a href="https://en.wikipedia.org/wiki/Gamma_function">the entry at Wikipedia</a>
     */
    public static Numeric gamma(Numeric z) {
        // special case for small values of z: Γ(z) ∼ 1/z − γ, z → 0
        // first, handle the special values of zero
        if (z instanceof Zero) {
            Zero input = (Zero) z;
            switch (input.sign()) {
                case ZERO:
                    throw new ArithmeticException("\uD835\uDEAA(n) is not analytic at 0");
                case POSITIVE:
                    return PosInfinity.getInstance(input.getMathContext());
                case NEGATIVE:
                    return NegInfinity.getInstance(input.getMathContext());
            }
        }
        // next, check if we're within some neighborhood of 0
        final Comparator<Numeric> comp = obtainGenericComparator();
        if (comp.compare(z.magnitude(), gammaNeighborhoodOfZero(z.getMathContext())) < 0 && !Zero.isZero(z)) {
            // compute an approximation and return it
            return gammaNearZero(z);
        }
        if (z.isCoercibleTo(IntegerType.class)) {
            try {
                IntegerType one = (IntegerType) One.getInstance(z.getMathContext()).coerceTo(IntegerType.class);
                IntegerType n = (IntegerType) z.coerceTo(IntegerType.class);
                IntegerType nMinus1 = (IntegerType) n.subtract(one);
                if (nMinus1.sign() != Sign.NEGATIVE) {
                    return factorial(nMinus1);
                } else {
                    // undefined for negative values and zero
                    throw new ArithmeticException("\uD835\uDEAA(n) is not analytic for integers n \u2264 0; n = " + n);
                }
            } catch (CoercionException ce) {
                throw new IllegalStateException(ce);
            }
        } else if (z instanceof RationalType) {
            RationalType zz = ((RationalType) z).reduce();
            // half-integer arguments have easy-to-compute values
            if (zz.denominator().asBigInteger().equals(BigInteger.TWO) && zz.numerator().isOdd()) {
                final RealType two = new RealImpl(decTWO, z.getMathContext());
                final RealType sqrtPi = (RealType) Pi.getInstance(z.getMathContext()).sqrt();
                if (zz.numerator().asBigInteger().equals(BigInteger.ONE)) return sqrtPi; // 𝚪(1/2) = √𝜋
                final RationalType onehalf = new RationalImpl(1L, 2L, z.getMathContext());
                try {
                    IntegerType m = (IntegerType) zz.subtract(onehalf).coerceTo(IntegerType.class);
                    Sign msign = m.sign();  // calculations need to be done without sign, so save it here
                    m = m.magnitude();  // and take the absolute value
                    IntegerType m2 = (IntegerType) new IntegerImpl(BigInteger.TWO).multiply(m);
                    RealType num = (RealType) factorial(m2).coerceTo(RealType.class);
                    RealType denom = (RealType) computeIntegerExponent(two, m2).multiply(factorial(m));
                    switch (msign) {
                        case POSITIVE:
                            // See https://proofwiki.org/wiki/Gamma_Function_of_Positive_Half-Integer
                            return num.multiply(sqrtPi).divide(denom);
                        case NEGATIVE:
                            // see https://proofwiki.org/wiki/Gamma_Function_of_Negative_Half-Integer
                            if (m.isOdd()) denom = denom.negate();
                            return denom.multiply(sqrtPi).divide(num);
                        case ZERO:
                            Logger.getLogger(MathUtils.class.getName()).log(Level.SEVERE,
                                    "This code should never have been reached.  Corner cases for \uD835\uDEAA(0) and \uD835\uDEAA({0}) " +
                                            "should have already handled this scenario while computing \uD835\uDEAA({1}).  Condition violated: m \u2260 0",
                                    new Object[] {onehalf, zz});
                            throw new IllegalStateException("\uD835\uDEAA(" + zz + ") [non-reduced z = " + z + "] failed with condition violated: m \u2260 0");
                    }
                } catch (CoercionException ce) {
                    throw new IllegalStateException("While computing \uD835\uDEAA(" + zz + ")", ce);
                }
            }
        }

        // use Weierstrass and compute a valid result for all reals and complex values (no half-plane reflection required)
        final long iterLimit = z.getMathContext().getPrecision() * Long.getLong(GAMMA_TERM_SCALE, 2048L) + 7L;
        final MathContext compCtx = new MathContext(z.getMathContext().getPrecision() * 2, z.getMathContext().getRoundingMode());
        final EulerMascheroni gamma = EulerMascheroni.getInstance(compCtx);
        final Euler e = Euler.getInstance(compCtx);
        Logger.getLogger(MathUtils.class.getName()).log(Level.INFO,
                "Computing \uD835\uDEAA({0}) for precision {1} with {2} iterations.",
                new Object[] { z, compCtx.getPrecision(), iterLimit });
        final long stepSize = Long.getLong(GAMMA_BLOCK_SIZE, 250L);
        Numeric exponent = gamma.multiply(z).negate();
        Numeric coeff = exponent instanceof ComplexType ? e.exp((ComplexType) exponent).divide(z) :
                e.exp((RealType) exponent).divide(z);  // exponent should be at least a real since gamma is a real
        ExecutorService executor = Executors.newCachedThreadPool();
        List<Future<Numeric>> segments = new LinkedList<>();
        for (long k = 1L; k < iterLimit; k += stepSize) {
            final long kk = k;
            Callable<Numeric> segment = () -> LongStream.range(kk, Math.min(kk + stepSize, iterLimit))
                    .mapToObj(n -> weierstrassTerm(n, z, compCtx)).reduce(Numeric::multiply).orElseThrow();
            segments.add(executor.submit(segment));
        }
        // read the block results out sequentially and take the product
        Numeric result = segments.stream().map(f -> {
            try {
                return f.get();
            } catch (InterruptedException | ExecutionException ex) {
                throw new IllegalStateException("Interrupted while computing \uD835\uDEAA(" + z + ")", ex);
            }
        }).reduce(coeff, Numeric::multiply);
        executor.shutdown();  // request a shutdown no matter what
        if (!executor.isTerminated()) {
            Logger.getLogger(MathUtils.class.getName()).warning("gamma() executor may not have terminated properly");
        }
        if (result instanceof ComplexType) {
            return round((ComplexType) result, z.getMathContext());
        } else {
            try {
                return round((RealType) result.coerceTo(RealType.class), z.getMathContext());
            } catch (CoercionException ex) {
                throw new IllegalStateException("Cannot convert " + result + " to a real value", ex);
            }
        }
    }

    private static Numeric weierstrassTerm(long n, Numeric z, MathContext ctx) {
        final Euler e = Euler.getInstance(ctx);
        final Numeric one = One.getInstance(ctx);
        final BigDecimal nn = BigDecimal.valueOf(n);

        Numeric zOverN = z instanceof ComplexType ? z.divide(new RealImpl(nn, ctx)) : // may need to invert the order for cplx case
                new RealImpl(OptionalOperations.asBigDecimal(z).divide(nn, ctx), ctx);
        Numeric lhs = one.add(zOverN).inverse();
        Numeric rhs = z instanceof ComplexType ? e.exp((ComplexType) zOverN) :
                e.exp((RealType) zOverN);
        return lhs.multiply(rhs);
    }

    /**
     * This computes an approximation of the Gamma function &#x1D6AA;(z)
     * for values of z where |z| is close to 0.
     * A basic approximation is &#x1D6AA;(z) &sim; 1/z &minus; &#x1D6FE; for z&nbsp;&#x2192;&nbsp;0
     * <br>A better approximation is given by
     * &#x1D6AA;(z) &#x2245; (1/z)&sdot;(1 + (π² &minus; 6γ²)z/12γ)/(1 + (π² + 6γ²)z/12γ)
     * and is the one used in this method.
     * @param z a value that is close to 0
     * @return an approximation of the Gamma function at z
     * @see <a href="https://math.stackexchange.com/a/3928423">this expansion of &#x1D6AA;(z) for values near 0</a> by Claude Leibovici
     */
    private static Numeric gammaNearZero(Numeric z) {
        final MathContext ctx = z.getMathContext();
        RealType piSquared = computeIntegerExponent(Pi.getInstance(ctx), 2L);
        RealType gamma = EulerMascheroni.getInstance(ctx);  // γ from the Javadoc
        RealType gammaSquared = (RealType) gamma.multiply(gamma);  // γ² from the Javadoc
        RealType six = new RealImpl(BigDecimal.valueOf(6L), ctx);
        RealType twelve = new RealImpl(BigDecimal.valueOf(12L), ctx);
        final Numeric one = One.getInstance(ctx);

        // derived values
        RealType gamma12 = (RealType) gamma.multiply(twelve);
        RealType gamma6sq = (RealType) gammaSquared.multiply(six);
        Numeric zDivG12 = z.divide(gamma12); // z divided by 12γ

        Numeric numerator = piSquared.subtract(gamma6sq).multiply(zDivG12).add(one);
        Numeric denominator = piSquared.add(gamma6sq).multiply(zDivG12).add(one);
        return z.inverse().multiply(numerator).divide(denominator);
    }

    private static RealType gammaNeighborhoodOfZero(MathContext ctx) {
        if (ctx.getPrecision() < 2) ctx = new MathContext(2, ctx.getRoundingMode());  // this will also fix UNLIMITED precision
        final String value = System.getProperty(GAMMA_NEIGHBORHOOD_OF_ZERO, "0.01");
        return new RealImpl(value, ctx);
    }

    /**
     * Obtain the real part of {@code z}. For non-complex
     * arguments, this will coerce the value to {@link RealType}.
     * @param z any {@link Numeric} value
     * @return the real part of z
     */
    public static RealType Re(Numeric z) {
        if (z instanceof ComplexType) return ((ComplexType) z).real();
        try {
            return (RealType) z.coerceTo(RealType.class);
        } catch (CoercionException e) {
            throw new ArithmeticException("Unable to extract real part of " + z);
        }
    }

    /**
     * If {@code z} is a complex value, return the imaginary component.
     * Otherwise, return zero.
     * @param z any {@link Numeric} value
     * @return the imaginary part of z, or 0 if it does not exist
     */
    public static RealType Im(Numeric z) {
        if (z instanceof ComplexType) return ((ComplexType) z).imaginary();
        // anything else has a zero imaginary component
        return new RealImpl(BigDecimal.ZERO, z.getMathContext());
    }

    /**
     * Computes the Riemann zeta function &#x1D701;(s), where s can be any
     * {@link Numeric} value (including {@link ComplexType}). The logic is
     * optimized for certain specific arguments (e.g., even positive integers),
     * and the main algorithm is globally convergent everywhere except
     * where Re(s)&nbsp;=&nbsp;1.
     * @param s the argument to the zeta function
     * @return the calculated value of &#x1D701;(s)
     * @see <a href="https://en.wikipedia.org/wiki/Riemann_zeta_function">the Wikipedia article</a>
     * @see <a href="https://en.wikipedia.org/wiki/Particular_values_of_the_Riemann_zeta_function">special values of Riemann zeta</a>
     * @see <a href="https://mathworld.wolfram.com/RiemannZetaFunction.html">the Wolfram Mathworld article</a>
     */
    public static Numeric zeta(Numeric s) {
        if (One.isUnity(s)) {
            // there is a pole at s = 1
            return PosInfinity.getInstance(s.getMathContext());
        }
        if (isInfinity(s, Sign.POSITIVE)) {
            return One.getInstance(s.getMathContext());  // 𝜁(+∞) = 1
        }
        if (ComplexType.isExtendedEnabled() && s instanceof PointAtInfinity) {
            throw new ArithmeticException("\uD835\uDF01(s) is singular at s=\u221E");
        }
        final RealType two = new RealImpl(decTWO, s.getMathContext());
        if (s.isCoercibleTo(IntegerType.class)) {
            try {
                IntegerType intArg = (IntegerType) s.coerceTo(IntegerType.class);
                long n = intArg.asBigInteger().longValueExact();
                BernoulliNumbers bn = new BernoulliNumbers(n > 1000L ? 1024 : (int) (Math.abs(n) + 2L), s.getMathContext());

                if (n <= 0L) {
                    long posN = Math.abs(n);
                    Numeric result = bn.getB(posN).divide(new IntegerImpl(BigInteger.valueOf(posN + 1L)));
                    if (posN % 2L == 1L) result = result.negate();
                    return result;
                } else if (n % 2L == 0L) {
                    // even positive integers
                    RealType factor = (RealType) Pi.getInstance(s.getMathContext()).multiply(two);
                    Numeric result = computeIntegerExponent(factor, 2L).multiply(bn.getB(n))
                            .divide(two.multiply(factorial(intArg)));
                    if ((n/2L + 1L) % 2L == 1L) result = result.negate();
                    return result;
                } // there is really no good pattern for odd positive integers, so we'll fall through to the logic below
            } catch (CoercionException e) {
                throw new ArithmeticException("While computing \uD835\uDF01(" + s + ") as an integer");
            }
        }
        // use the 1930-era Euler-Maclaurin algorithm to compute 𝜁(s)
        long n = Math.max(5L, s.getMathContext().getPrecision() / 3L); // was 10L; DECIMAL64 -> 5, DECIMAL128 -> 11
        long m = Math.max(7L, s.getMathContext().getPrecision() / 2L); // was 10L; DECIMAL64 -> 8, DECIMAL128 -> 17
        Logger.getLogger(MathUtils.class.getName()).log(Level.INFO,
                "Computing \uD835\uDF01(s) using Euler-Maclaurin formula with n={0}, m={1}.",
                new Object[] {n, m});
        BernoulliNumbers numbers = new BernoulliNumbers((int) (n * m) + 2, s.getMathContext());
        Logger.getLogger(MathUtils.class.getName()).log(Level.INFO,
                "\uD835\uDF01({0}) error term = {1}", new Object[] {s, zetaE_mn(n, m, s, numbers)});

        Numeric finalTerm = LongStream.rangeClosed(1L, m).parallel()
                .mapToObj(k -> zetaT_kn(n, k, s, numbers))
                .reduce(ExactZero.getInstance(s.getMathContext()), Numeric::add);
        return zetaNterms(n, s).add(finalTerm);
    }

    private static Numeric zetaNterms(long n, Numeric s) {
        final Logger logger = Logger.getLogger(MathUtils.class.getName());
        final MathContext ctx = s.getMathContext();
        final Numeric one = One.getInstance(ctx);
        final Numeric negS = s.negate();
        logger.log(Level.FINE,
                "Computing {0} terms of j^({1})",
                new Object[] {n - 1L, negS});
        Numeric jsum = LongStream.range(1L, n).parallel() // 1..n-1
                .mapToObj(BigDecimal::valueOf)
                .map(j -> new RealImpl(j, ctx))
                .map(j -> {
                    if (s instanceof ComplexType) return generalizedExponent(j, (ComplexType) negS, ctx);
                    return generalizedExponent(j, negS, ctx);
                }).reduce(ExactZero.getInstance(ctx), Numeric::add);
        logger.log(Level.FINE,
                "Finished computing {0} terms of j^({1})",
                new Object[] {n - 1L, negS});
        final RealType reN = new RealImpl(BigDecimal.valueOf(n), ctx);
        final RealType two = new RealImpl(decTWO, ctx);
        logger.log(Level.FINE,
                "Computing {0}^({1})",
                new Object[] {reN, negS});
        Numeric midTerm = (s instanceof ComplexType ? generalizedExponent(reN, (ComplexType) negS, ctx) :
                generalizedExponent(reN, negS, ctx)).divide(two);
        logger.log(Level.FINE,
                "Computing {0}^({1}) / {2}",
                new Object[] {reN, one.subtract(s), s.subtract(one)});
        Numeric endTerm = ((s instanceof ComplexType ? generalizedExponent(reN, (ComplexType) one.subtract(s), ctx) :
                generalizedExponent(reN, one.subtract(s), ctx))).divide(s.subtract(one));
        return jsum.add(midTerm).add(endTerm);
    }

    /**
     * Computes the T<sub>k,n</sub>(s) term of the Riemann zeta function &#x1D701;(s).
     * @param n  a parameter determining accuracy
     * @param k  the index of this term
     * @param s  the parameter of &#x1D701;(s)
     * @param bn a generator for Bernoulli numbers, initialized to contain a sufficient number of precalculated values
     * @return the computed value of the k<sub>th</sub> term
     */
    private static Numeric zetaT_kn(long n, long k, Numeric s, BernoulliNumbers bn) {
        final IntegerType twoK = new IntegerImpl(BigInteger.valueOf(2L * k), true) {
            @Override
            public MathContext getMathContext() {
                return s.getMathContext();
            }
        };
        final Numeric one = One.getInstance(s.getMathContext());
        // n^(1 - s - 2k)
        Numeric npow;
        if (s instanceof ComplexType) {
            // 1 - s - 2k is going to be complex here, so use the special version of generalizedExponent()
            npow = generalizedExponent(new RealImpl(BigDecimal.valueOf(n), s.getMathContext()),
                    (ComplexType) one.subtract(s).subtract(twoK), s.getMathContext());
        } else {
            npow = generalizedExponent(new RealImpl(BigDecimal.valueOf(n), s.getMathContext()),
                    one.subtract(s).subtract(twoK), s.getMathContext());
        }
        Numeric coeff = bn.getB(2L * k).multiply(npow).divide(factorial(twoK));
        return LongStream.rangeClosed(0L, 2L * k - 2L)
                .mapToObj(j -> s.add(new RealImpl(BigDecimal.valueOf(j), s.getMathContext())))
                .reduce(coeff, Numeric::multiply);
    }

    /**
     * Computes the error term E<sub>m,n</sub>(s) for the Riemann zeta function &#x1D701;(s)
     * where m and n are integer values (see {@link #zetaT_kn(long, long, Numeric, BernoulliNumbers) T<sub>k,n</sub>(s)}
     * as well as the {@link #zeta(Numeric) implementation of &#x1D701;(s)} itself).
     * @param n  a parameter determining accuracy
     * @param m  a parameter determining accuracy
     * @param s  the parameter of &#x1D701;(s)
     * @param bn a generator for Bernoulli numbers, initialized to contain a sufficient number of precalculated values
     * @return the magnitude of the error term, expressed as a real
     */
    private static RealType zetaE_mn(long n, long m, Numeric s, BernoulliNumbers bn) {
        final RealType twoMplus1 = new RealImpl(new BigDecimal(2L * m + 1L), s.getMathContext(), true);
        final RealType sigma = Re(s);
        return s.add(twoMplus1).multiply(zetaT_kn(n, m + 1L, s, bn)).divide(sigma.add(twoMplus1)).magnitude();
    }

    /**
     * Determine if a given value is an infinity with the given sign.
     * Note that for {@link PointAtInfinity}, this will always return false
     * since complex infinity has no sign.
     * @param value        the value to test
     * @param infinitySign the sign of the infinity for which we're checking
     * @return true if {@code value} is an infinity of the given sign, false otherwise
     */
    public static boolean isInfinity(Numeric value, Sign infinitySign) {
        if (value instanceof PosInfinity && infinitySign == Sign.POSITIVE) return true;
        if (value instanceof NegInfinity && infinitySign == Sign.NEGATIVE) return true;
        if (value instanceof RealInfinity) {
            return ((RealType) value).sign() == infinitySign;
        }
        return false;
    }

    /**
     * Compute the binomial coefficient.
     * @param n the size of the set from which we are choosing
     * @param k the number of elements we are choosing from the set at a time
     * @return the binomial coefficient
     */
    public static IntegerType nChooseK(IntegerType n, IntegerType k) {
        // negative n gets special handling
        if (n.sign() == Sign.NEGATIVE) {
            final IntegerType one = new IntegerImpl(BigInteger.ONE);
            IntegerType result = nChooseK((IntegerType) k.subtract(n).subtract(one), k);
            if (k.isOdd()) result = result.negate();
            return result;
        }
        // factorial() already checks for negative values, so we just need to check that k <= n
        if (k.compareTo(n) > 0) throw new IllegalArgumentException("k must be \u2264 n");
        try {
            return (IntegerType) factorial(n).divide(factorial(k).multiply(factorial((IntegerType) n.subtract(k)))).coerceTo(IntegerType.class);
        } catch (CoercionException e) {
            // the result should always be an integer, so this should never happen
            Logger.getLogger(MathUtils.class.getName()).log(Level.SEVERE,
                    "A non-integer result was generated by nChooseK", e);
            throw new ArithmeticException("Received a non-integer result for n-choose-k");
        }
    }

    /**
     * A convenience method for computing n-choose-k when only {@code long}
     * values are available.
     * @param n the size of the set from which we are choosing
     * @param k the number of elements we are choosing from the set at a time
     * @return the binomial coefficient
     */
    public static IntegerType nChooseK(long n, long k) {
        IntegerType N = new IntegerImpl(BigInteger.valueOf(n));
        IntegerType K = new IntegerImpl(BigInteger.valueOf(k));
        return nChooseK(N, K);
    }

    /**
     * Compute the generalized binomial coefficient, where {@code x} is a value
     * of any type and {@code k} is an integer.
     * @param x the generalization of {@code n} found in {@link #nChooseK(IntegerType, IntegerType)}
     * @param k the number of elements &ldquo;chosen&rdquo; at a time
     * @return the generalized binomial coefficient
     * @see <a href="https://math.stackexchange.com/questions/340124/binomial-coefficients-1-2-choose-k">an article
     *   on Mathematics Stack Exchange</a>
     */
    public static Numeric generalizedBinomialCoefficient(Numeric x, IntegerType k) {
        final MathContext ctx = inferMathContext(List.of(x, k));
        Numeric accum = x;

        for (long kval = 1L; kval < k.asBigInteger().longValueExact(); kval++) {
            accum = accum.multiply(x.subtract(new IntegerImpl(BigInteger.valueOf(kval)) {
                @Override
                public MathContext getMathContext() {
                    return ctx;
                }
            }));
        }

        return accum.divide(factorial(k));
    }

    /**
     * Round a value x to the given {@link MathContext}.
     * @param x   the real value to be rounded
     * @param ctx the {@link MathContext} to apply
     * @return the value {@code x} rounded
     */
    public static RealType round(RealType x, MathContext ctx) {
        if (x.getClass().isAnnotationPresent(Constant.class)) {
            String constantName = x.getClass().getAnnotation(Constant.class).name();
            return OptionalOperations.instantiateConstant(constantName, ctx);
        }
        BigDecimal value = x.asBigDecimal().round(ctx);
        return new RealImpl(value, ctx, false);
    }

    /**
     * Round the values of a {@link Vector<RealType>} to the
     * precision of the given {@link MathContext}.
     * @param x   a {@link Vector} with real element values
     * @param ctx the {@link MathContext}, which provides the precision and {@link RoundingMode}
     * @return a new vector of rounded real values
     */
    public static RealVector round(Vector<RealType> x, MathContext ctx) {
        RealVector result = new RealVector(x.length());
        LongStream.range(0L, x.length()).mapToObj(x::elementAt).map(v -> round(v, ctx))
                .forEachOrdered(result::append);
        result.setMathContext(ctx);
        return result;
    }

    /**
     * Round a value z to the given {@link MathContext}. This operation
     * is equivalent to performing a rounding operation on each of the
     * components of the complex value z.
     * @param z   the complex value to be rounded
     * @param ctx the {@link MathContext} to apply
     * @return the complex value z rounded
     */
    public static ComplexType round(ComplexType z, MathContext ctx) {
        if (z.getClass().isAnnotationPresent(Constant.class)) {
            String constantName = z.getClass().getAnnotation(Constant.class).name();
            return OptionalOperations.instantiateConstant(constantName, ctx);
        }
        if (z.getClass().isAnnotationPresent(Polar.class)) {
            return new ComplexPolarImpl(round(z.magnitude(), ctx), round(z.argument(), ctx), false);
        }
        return new ComplexRectImpl(round(z.real(), ctx), round(z.imaginary(), ctx), false);
    }

    /**
     * Round the values of a {@link Vector<ComplexType>} to the
     * precision of the given {@link MathContext}.
     * @param z   a {@link Vector} with complex element values
     * @param ctx the {@link MathContext}, which provides the precision and {@link RoundingMode}
     * @return a new vector of rounded complex values
     * @apiNote This method <em>should</em> be named {@code round} for consistency with the other
     *   rounding methods, and because overloading makes sense here.  Unfortunately, the first
     *   argument is a {@code Vector<ComplexType>}, which conflicts with
     *   {@link #round(Vector, MathContext) round(Vector&lt;RealType&gt;,&thinsp;&hellip;)}.
     *   This is due to limitations of Java generics and type erasure.  This method may be renamed in the future.
     */
    public static ComplexVector roundCV(Vector<ComplexType> z, MathContext ctx) {
        ComplexVector result = new ComplexVector(z.length());
        LongStream.range(0L, z.length()).mapToObj(z::elementAt).map(v -> round(v, ctx))
                .forEachOrdered(result::append);
        result.setMathContext(ctx);
        return result;
    }

    /**
     * Compute the minimum of two real values.
     * @param a the first value
     * @param b the second value
     * @return the minimum of a and b
     */
    public static RealType min(RealType a, RealType b) {
        if (a.compareTo(b) < 0) return a;
        return b;
    }

    /**
     * Compute the minimum of two rational values.
     * @param a the first value
     * @param b the second value
     * @return the minimum of a and b
     */
    public static RationalType min(RationalType a, RationalType b) {
        if (a.compareTo(b) <= 0) return a;
        return b;
    }

    /**
     * Compute the maximum of two real values.
     * @param a the first value
     * @param b the second value
     * @return the maximum of a and b
     */
    public static RealType max(RealType a, RealType b) {
        if (a.compareTo(b) > 0) return a;
        return b;
    }

    /**
     * Compute the maximum of two rational values.
     * @param a the first value
     * @param b the second value
     * @return the maximum of a and b
     */
    public static RationalType max(RationalType a, RationalType b) {
        if (a.compareTo(b) >= 0) return a;
        return b;
    }

    /**
     * Generates a random real value that fits within the provided range.
     * @param range the {@link Range<RealType>} that specifies the lower and upper
     *              bounds of the value to be generated
     * @return a random number in the specified {@code range}
     */
    public static RealType random(Range<RealType> range) {
        final MathContext ctx = inferMathContext(List.of(range.getLowerBound(), range.getUpperBound()));
        final Random rand = new Random();
        if (range.getLowerBound() instanceof RealInfinity) {
            if (range.getLowerBound().sign() != Sign.NEGATIVE) {
                throw new IllegalArgumentException("Lower bound may not be +\u221E");
            }
            if (range.getUpperBound() instanceof RealInfinity) {
                if (range.getUpperBound().sign() == Sign.NEGATIVE) {
                    throw new IllegalArgumentException("Upper bound must not be \u2212\u221E if lower bound is " + range.getLowerBound());
                }
                // we have a full range from -∞ to +∞
                return new RealImpl(nextBigDecimal(rand, ctx.getPrecision() + 2, false), ctx, false);
            }
            // generate a value between -∞ and upper bound
            BigDecimal offset = nextBigDecimal(rand, ctx.getPrecision() + 1, true);
            return new RealImpl(range.getUpperBound().asBigDecimal().subtract(offset, ctx), ctx, false);
        } else if (range.getUpperBound() instanceof RealInfinity) {
            if (range.getUpperBound().sign() != Sign.POSITIVE) {
                throw new IllegalArgumentException("Upper bound may not be \u2212\u221E");
            }
            // generate a value between lower bound and +∞
            BigDecimal offset = nextBigDecimal(rand, ctx.getPrecision() + 1, true);
            return new RealImpl(range.getLowerBound().asBigDecimal().add(offset, ctx), ctx, false);
        }
        RealType span = (RealType) range.getUpperBound().subtract(range.getLowerBound());
        RealType randVal;
        do {
            randVal = random(ctx, rand);
        } while (!range.isLowerClosed() && Zero.isZero(randVal));  // exclude values from lower limit if needed
        return (RealType) range.getLowerBound().add(span.multiply(randVal));
    }

    /**
     * Generates a random value in the interval [0,&nbsp;1) to a specified precision; values
     * are guaranteed to be roughly equally distributed across this interval.
     * This method is directly analogous to {@link Math#random()} and can practically
     * generate any number of digits that can be expressed in a {@link BigDecimal}.
     * @param ctx    a valid {@link MathContext} other than {@link MathContext#UNLIMITED}
     * @param random a source of randomness
     * @return a randomly generated value x such that 0 &le; x &lt; 1
     * @see <a href="https://stackoverflow.com/a/71743540/7491719">Ben McKenneby's StackOverflow answer</a>
     */
    public static RealType random(MathContext ctx, Random random) {
        if (ctx.getPrecision() == 0) throw new ArithmeticException("Cannot generate a random value with unbounded precision");
        final double log2 = Math.log10(2.0d); // this is adequate resolution since we're casting to an int
        int digitCount = ctx.getPrecision();
        int bitCount = (int) (digitCount / log2);
        BigDecimal value = new BigDecimal(new BigInteger(bitCount, random)).movePointLeft(digitCount);
        return new RealImpl(value.round(ctx), ctx, false);
    }

    /**
     * Randomly generate a {@link BigDecimal} value that is evenly distributed
     * across the entire range of possible {@link BigDecimal} values. If
     * {@code positiveOnly} is {@code true}, then the output is limited to
     * positive values only.
     * @param rand         a source of randomness
     * @param maxBytes     the maximum number of bytes used to generate the unscaled value
     * @param positiveOnly if true, only output positive values
     * @return a randomly generated value
     */
    private static BigDecimal nextBigDecimal(Random rand, int maxBytes, boolean positiveOnly) {
        if (maxBytes < 1) throw new IllegalArgumentException("Maximum number of bytes must be at least 1");
        int length = rand.nextInt(maxBytes) + 1;
        byte[] bytes = new byte[length];
        rand.nextBytes(bytes);
        BigInteger unscaled = new BigInteger(bytes);
        if (positiveOnly && unscaled.signum() == -1) unscaled = unscaled.negate();
        return new BigDecimal(unscaled, rand.nextInt());
    }

    /**
     * Create a source of Gaussian noise, given the mean {@code mu} (&mu;)
     * and the standard deviation {@code sigma} (&sigma;).  The math context
     * of the resulting values is inferred from the arguments.  This method
     * returns a {@link Supplier<RealType>} which includes its own source
     * of randomness, independent of any used elsewhere.
     * <br>This method is roughly analogous to {@link Random#nextGaussian()}
     * except that it returns a theoretically unending source of values
     * rather than a single value.  The algorithm used in that method is
     * virtually identical to the one used here, except that more Gaussian noise
     * values are cached in this version.
     * <br>Best efforts have been made to make the resulting {@link Supplier}
     * thread-safe, should it be needed for e.g. a parallel
     * {@link java.util.stream.Stream stream}.
     * @param mu    the mean of the distribution
     * @param sigma the standard deviation of the distribution
     * @return a {@link Supplier} of real values conforming to a Gaussian distribution
     * @see <a href="https://en.wikipedia.org/wiki/Marsaglia_polar_method">the Wikipedia article
     *   for this algorithm</a>
     */
    public static Supplier<RealType> gaussianNoise(RealType mu, RealType sigma) {
        final int qsize = 8;  // this can be set dynamically
        final MathContext ctx = inferMathContext(List.of(mu, sigma));
        final Random randSrc = new Random();

        return new Supplier<>() {
            private final Logger logger = Logger.getLogger(this.getClass().getName());
            private final BlockingQueue<RealType> fifo = new LinkedBlockingQueue<>(qsize);

            @Override
            public RealType get() {
                // this needs to be synchronized to avoid a deadlock
                // without atomicity, we could have 2 threads A and B
                // both requesting a noise value with a queue of size = 1
                // if A calls isEmpty() and then B calls isEmpty(), both returning false,
                // B could snipe the last remaining entry in the queue and leave
                // A expecting there to be something in the queue but blocking on take()
                synchronized (fifo) {
                    if (fifo.isEmpty()) generateValues(qsize);
                    try {
                        return fifo.take();
                    } catch (InterruptedException e) {
                        logger.log(Level.SEVERE,
                                "While obtaining a Gaussian noise value", e);
                        throw new NoSuchElementException("No element found while waiting for FIFO to fill");
                    }
                }
            }

            private final RealType negtwo = new RealImpl("-2.0", ctx);
            private final RealType two = new RealImpl(decTWO, ctx);
            private final One one = (One) One.getInstance(ctx);
            private final ExecutorService exec = Executors.newSingleThreadExecutor();

            private void generateValues(int n) {
                final int limit = n % 2 == 0 ? n / 2 : (n + 1) / 2;
                for (int k = 0; k < limit; k++) {
                    exec.submit(this::generatePair);
                }
                logger.log(Level.INFO, "Submitted {0} requests to generate Gaussian noise pairs.", limit);
            }

            private void generatePair() {
                RealType u, v, s;
                do {
                    u = (RealType) random(ctx, randSrc).multiply(two).subtract(one);
                    v = (RealType) random(ctx, randSrc).multiply(two).subtract(one);
                    s = (RealType) u.multiply(u).add(v.multiply(v));
                } while (Zero.isZero(s) || one.compareTo(s) <= 0);
                s = (RealType) ln(s).multiply(negtwo).divide(s).sqrt();
                try {
                    fifo.put((RealType) mu.add(sigma.multiply(u).multiply(s)));
                    fifo.put((RealType) mu.add(sigma.multiply(v).multiply(s)));
                } catch (InterruptedException e) {
                    logger.log(Level.SEVERE,
                            "While generating Gaussian noise value", e);
                    if (fifo.isEmpty()) {
                        throw new IllegalStateException("No Gaussian noise values have been generated", e);
                    } else {
                        logger.log(Level.WARNING, "Only generated {0} total Gaussian noise values.", fifo.size());
                    }
                }
            }
        };
    }

    /**
     * Compute the value of x<sup>n</sup>, where x is coercible to {@link RealType} and
     * n is an integer value.
     * @param x any value that can be coerced to a real
     * @param n any integer value
     * @return the value of x<sup>n</sup>
     * @throws ArithmeticException if x is not coercible to a real value
     */
    public static RealType computeIntegerExponent(Numeric x, IntegerType n) {
        final RealType result;
        final BigInteger exponent = n.asBigInteger();
        final boolean exactness = x.isExact() && n.isExact();

        if (exponent.equals(BigInteger.ZERO)) {
            result = new RealImpl(BigDecimal.ONE, x.getMathContext(), exactness);
        } else {
            try {
                result = computeIntegerExponent((RealType) x.coerceTo(RealType.class), exponent.longValueExact());
                if (result.isExact() != exactness) {
                    Logger.getLogger(MathUtils.class.getName()).log(Level.INFO,
                            "Expected exactness of {0} but got {1} for calculating {2}{3}.",
                            new Object[] {exactness, result.isExact(), x, UnicodeTextEffects.numericSuperscript(n.asBigInteger().intValue())});
                }
            } catch (CoercionException ex) {
                Logger.getLogger(MathUtils.class.getName()).log(Level.SEVERE, "Failed to coerce argument to RealType.", ex);
                throw new ArithmeticException("While coercing argument to computeIntegerExponent: " + ex.getMessage());
            }
        }
        
        return result;
    }

    /**
     * A convenience method for computing z<sup>n</sup> for complex value z.
     * @param z a complex number
     * @param n any integer value
     * @return the value of z<sup>n</sup>
     */
    public static ComplexType computeIntegerExponent(ComplexType z, IntegerType n) {
        return computeIntegerExponent(z, n.asBigInteger().longValueExact(), z.getMathContext());
    }

    // This is the value of the maximum allowed integer exponent for
    // BigDecimal.pow(), as documented in the JDK.  This could be adjusted
    // lower in the future, but never higher.  (The absolute upper
    // limit would be the maximum value of int.)
    private static final long MAX_INT_FOR_EXPONENT = 999_999_999L;

    /**
     * Compute x<sup>n</sup>.
     * @param x the value to take the exponent of
     * @param n the integer exponent
     * @param mctx the {@link MathContext} for computing the exponent
     * @return x raised to the n<sup>th</sup> power
     */
    public static RealType computeIntegerExponent(RealType x, long n, MathContext mctx) {
        if (n == 0L) return new RealImpl(BigDecimal.ONE, mctx, x.isExact());
        if (n == 1L) return x;
        // if n falls within a certain integer range, delegate to BigDecimal.pow()
        if (useBuiltInOperations() && Math.abs(n) < MAX_INT_FOR_EXPONENT) {
            RealImpl real = new RealImpl(x.asBigDecimal().pow((int) n, mctx), mctx, x.isExact());
            // TODO tighten up the condition under which we set irrational = true
            // x could be irrational, say, √2, and yet x² = 2, not an irrational number
            real.setIrrational(x.isIrrational());
            return real;
        }
        try {
            if (n == -1L) {
                return (RealType) x.inverse().coerceTo(RealType.class);
            }

            MathContext compctx = new MathContext(mctx.getPrecision() + 4, mctx.getRoundingMode());
            Numeric intermediate = One.getInstance(compctx);
            Numeric factor = new RealImpl(x.magnitude().asBigDecimal(), compctx, x.isExact());
            long m = Math.abs(n);
            if (m % 2L == 1L) {  // handle the corner case of odd exponents
                intermediate = factor;
                m--;
            }
            while (m % 2L == 0L) {
                factor = factor.multiply(factor);
                m >>= 1L;
            }
            for (long k = 0; k < m; k++) intermediate = intermediate.multiply(factor);
            if (n < 0L) intermediate = intermediate.inverse();
            // if |n| is odd, preserve original sign
            if (x.sign() == Sign.NEGATIVE && Math.abs(n) % 2L != 0L) intermediate = intermediate.negate();
            return round((RealType) intermediate.coerceTo(RealType.class), mctx);
        } catch (CoercionException ex) {
            Logger.getLogger(MathUtils.class.getName()).log(Level.SEVERE, "Unrecoverable exception thrown while computing integer exponent.", ex);
            throw new ArithmeticException("Failure to coerce result to RealType");
        }
    }

    /**
     * Compute z<sup>n</sup> for complex z.
     * @param z    the complex value to be raised to the given exponent
     * @param n    the exponent
     * @param mctx the {@link MathContext} to be used for any intermediate real values, which may not be
     *             reflected in the result
     * @return z raised to the n<sup>th</sup> power
     */
    public static ComplexType computeIntegerExponent(ComplexType z, long n, MathContext mctx) {
        if (n == 0L) return new ComplexRectImpl(new RealImpl(BigDecimal.ONE, mctx, z.isExact()));
        if (n == 1L) return z;
        if (n == -1L) {
            return z.inverse();
        }
        if (z.getClass().isAnnotationPresent(Polar.class)) {
            // computing powers of complex numbers in polar form is much faster and easier
            final IntegerImpl exponent = new IntegerImpl(BigInteger.valueOf(n));
            RealType modulus = computeIntegerExponent(z.magnitude(), exponent);
            RealType argument = (RealType) z.argument().multiply(exponent);
            return new ComplexPolarImpl(modulus, argument, z.isExact());
        }

        try {
            Numeric intermediate = One.getInstance(mctx);
            Numeric factor = z;
            long m = Math.abs(n);
            if (m % 2L == 1L) {  // odd exponents are a simple corner case
                intermediate = factor;
                m--;
            }
            while (m % 2L == 0L) {
                factor = factor.multiply(factor);
                m >>= 1L;
            }
            for (long k = 0L; k < m; k++) intermediate = intermediate.multiply(factor);
            if (n < 0L) intermediate = intermediate.inverse();
            return (ComplexType) intermediate.coerceTo(ComplexType.class);
        } catch (CoercionException ex) {
            Logger.getLogger(MathUtils.class.getName()).log(Level.SEVERE, "Unrecoverable exception thrown while computing integer exponent.", ex);
            throw new ArithmeticException("Failure to coerce result to ComplexType");
        }
    }
    
    /**
     * Compute x<sup>n</sup>. The {@link MathContext} is inferred from {@code x}.
     * @param x the value to take the exponent of
     * @param n the integer exponent
     * @return x raised to the n<sup>th</sup> power
     */
    public static RealType computeIntegerExponent(RealType x, long n) {
        return computeIntegerExponent(x, n, x.getMathContext());
    }
    
    private static final BigDecimal decTWO = BigDecimal.valueOf(2L);
    private static final Range<RealType> newtonRange = new Range<>(new RealImpl(BigDecimal.ZERO), new RealImpl(decTWO), BoundType.EXCLUSIVE);
    
    /**
     * Compute the natural logarithm, ln(x).
     * @param x the value for which to obtain the natural logarithm
     * @param mctx the {@link MathContext} to use for this operation
     * @return the natural logarithm of {@code x}
     */
    public static RealType ln(RealType x, MathContext mctx) {
        if (x.asBigDecimal().compareTo(BigDecimal.ONE) == 0) {
            try {
                return (RealType) ExactZero.getInstance(mctx).coerceTo(RealType.class);
            } catch (CoercionException ex) {
                // We should never get here!
                throw new IllegalStateException(ex);
            }
        }
        if (x.asBigDecimal().compareTo(BigDecimal.ZERO) <= 0) {
            if (x.asBigDecimal().compareTo(BigDecimal.ZERO) == 0) return RealInfinity.getInstance(Sign.NEGATIVE, mctx);
            throw new ArithmeticException("ln(x) is undefined for x < 0");
        }
        if (newtonRange.contains(x)) return lnNewton(x, mctx);
        
        if (x.asBigDecimal().compareTo(BigDecimal.TEN) > 0) {
            RealType mantissa = mantissa(x);
            IntegerType exponent = exponent(x);
            // use the identity ln(a*10^n) = ln(a) + n*ln(10)
            RealType ln10 = lnSeries(new RealImpl(BigDecimal.TEN), mctx);
            try {
                // We are mainly coercing here out of an abundance of caution, so if something does break, we log it well.
                return (RealType) ln(mantissa, mctx).add(ln10.multiply(exponent)).coerceTo(RealType.class);
            } catch (CoercionException ex) {
                Logger logger = Logger.getLogger(MathUtils.class.getName());
                logger.log(Level.SEVERE, "While computing ln(a\u2009×\u200910\u207F) = ln(a) + n\u2009×\u2009ln(10).", ex);
                logger.log(Level.INFO, "Attempted to decompose ln(x) for x\u2009=\u2009{0} with mantissa\u2009=\u2009{1} and exponent\u2009=\u2009{2}.",
                        new Object[] {x, mantissa, exponent});
                throw new IllegalStateException("Calculation of ln(a × 10\u207F) = ln(a) + n × ln(10) failed to generate a real value", ex);
            }
        }
        
        return lnSeries(x, mctx);
    }
    
    /**
     * Compute the natural logarithm, ln(x).
     * @param x the value for which to obtain the natural logarithm
     * @return the natural logarithm of {@code x}
     */
    public static RealType ln(RealType x) {
        return ln(x, x.getMathContext());
    }
    
    private static RealType lnNewton(RealType x, MathContext mctx) {
        Euler e = Euler.getInstance(mctx);
        BigDecimal xval = x.asBigDecimal();
        BigDecimal y0 = BigDecimal.ONE;
        BigDecimal y1;
        while (true) {
            final BigDecimal expval = e.exp(new RealImpl(y0, false)).asBigDecimal();
            
            BigDecimal num = xval.subtract(expval, mctx);
            BigDecimal denom = xval.add(expval, mctx);
            y1 = y0.add(decTWO.multiply(num.divide(denom, mctx), mctx), mctx);
            if (y0.compareTo(y1) == 0) break;
            
            y0 = y1;
        }
        final RealImpl result = new RealImpl(y0, false);
        result.setIrrational(true);
        result.setMathContext(mctx);
        return result;
    }
    
    private static RealType lnSeries(RealType x, MathContext mctx) {
        final MathContext compctx = new MathContext(mctx.getPrecision() + 4, mctx.getRoundingMode());
        BigDecimal xfrac = x.asBigDecimal().subtract(BigDecimal.ONE, compctx).divide(x.asBigDecimal(), compctx);
        BigDecimal sum = BigDecimal.ZERO;
        for (int n = 1; n < mctx.getPrecision() * 17; n++) {
            sum = sum.add(computeNthTerm_ln(xfrac, n, compctx), compctx);
        }
        final RealImpl result = new RealImpl(sum.round(mctx), false);
        result.setIrrational(true);
        result.setMathContext(mctx);
        return result;
    }
    
    private static BigDecimal computeNthTerm_ln(BigDecimal frac, int n, MathContext mctx) {
        BigDecimal ninv = BigDecimal.ONE.divide(BigDecimal.valueOf(n), mctx);
        return ninv.multiply(frac.pow(n, mctx), mctx);
    }

    /**
     * Compute the natural logarithm of a complex value.
     * @param z the value we want to calculate the natural logarithm of
     * @return the principal natural logarithm as a {@link ComplexType} value
     */
    public static ComplexType ln(ComplexType z) {
        return new ComplexRectImpl(ln(z.magnitude()), z.argument());
    }

    /**
     * A fast way to compute the natural logarithm for integer values; it is more
     * accurate for large values of {@code N}.<br>
     * This method takes advantage of the observation that the series<br>
     * 1 + 1&#x2044;2 + 1&#x2044;3 + 1&#x2044;4 + 1&#x2044;5 + &#x22EF; + 1&#x2044;N &cong; ln(N)&nbsp;+&nbsp;&#x1D6FE;
     * <br> where &#x1D6FE; is the {@link EulerMascheroni Euler-Mascheroni constant}.  Thus, to compute an
     * approximation of ln(N) where N is an integer value, one merely has to compute the series of 1/n for
     * n in [1, N], then subtract &#x1D6FE;.  Note that the calculation of &#x1D6FE; can be expensive, but the
     * value is cached on a per-{@link MathContext} basis &mdash; thus, the expected average case cost for
     * this method is merely O(N), the cost of computing a rational sum.
     * @param N    an integer value
     * @param mctx the {@link MathContext} for computing this approximation
     * @return an approximation of ln(N)
     */
    public static RealType ln(IntegerType N, MathContext mctx) {
        if (N.sign() == Sign.NEGATIVE) throw new ArithmeticException("ln(N) is undefined for N < 0");
        final long nInt = N.asBigInteger().longValueExact();
        if (nInt == 0L) return RealInfinity.getInstance(Sign.NEGATIVE, mctx);
        if (nInt == 1L) return new RealImpl(BigDecimal.ZERO, mctx, N.isExact());
        final RealType gamma = EulerMascheroni.getInstance(mctx);
        RationalType sum = LongStream.rangeClosed(1L, nInt).parallel()
                .mapToObj(denom -> new RationalImpl(1L, denom, mctx))
                .map(RationalType.class::cast).reduce((A, B) -> (RationalType) A.add(B))
                .orElseThrow(() -> new ArithmeticException("Unable to compute sum of 1/n for n in 1.." + N));
        return (RealType) sum.subtract(gamma);
    }

    /**
     * System property governing when the natural log of a rational value should be computed using
     * {@link #ln(IntegerType, MathContext) an integer approximation} or by first converting to a
     * real value.  If <em>both</em> the numerator and the denominator of a rational number are below this
     * threshold, the rational is converted into a real before computing the natural log.  The default
     * value is 250.
     */
    public static final String LN_RATIONAL_THRESHOLD = "tungsten.types.numerics.MathUtils.ln.rational.threshold";
    private static final IntegerType lnRationalCutoff = new IntegerImpl(System.getProperty(LN_RATIONAL_THRESHOLD, "250"));

    /**
     * Compute the natural logarithm of a rational value. For a rational p/q, this method may
     * compute it by first converting the rational value into a real, or it may compute the
     * result as ln(p)&nbsp;&minus;&nbsp;ln(q).  The threshold for which calculation to use
     * is configurable via a system property.
     * @param x a rational value
     * @return the natural logarithm ln(x)
     * @see #LN_RATIONAL_THRESHOLD
     */
    public static RealType ln(RationalType x) {
        final MathContext ctx = x.getMathContext();
        if (x.numerator().compareTo(lnRationalCutoff) < 0 && x.denominator().compareTo(lnRationalCutoff) < 0) {
            try {
                return ln((RealType) x.coerceTo(RealType.class), ctx);
            } catch (CoercionException fatal) {
                throw new IllegalStateException("While computing ln(" + x + ")", fatal);
            }
        }
        return (RealType) ln(x.numerator(), ctx).subtract(ln(x.denominator(), ctx));
    }

    /**
     * Compute the general logarithm, log<sub>b</sub>(x).
     * @param x the number for which we wish to take a logarithm
     * @param base the base of the logarithm
     * @param mctx the MathContext to use for computing the logarithm
     * @return the logarithm of {@code x} in {@code base}
     */
    public static RealType log(RealType x, RealType base, MathContext mctx) {
        final RealType one = new RealImpl(BigDecimal.ONE, mctx);
        if (base.compareTo(one) <= 0) throw new ArithmeticException("Cannot compute log with base " + base);
        if (x.equals(base)) return one;
        final MathContext compCtx = new MathContext(mctx.getPrecision() + 8, mctx.getRoundingMode());
        // determined that you need at least 8 extra decimal places to get a value that rounds correctly
        // otherwise, you get things like log₂(1024) = 9.9999... instead of 10.0
        return round((RealType) ln(x, compCtx).divide(ln(base, compCtx)), mctx);
    }
    
    /**
     * Compute the general logarithm, log<sub>b</sub>(x).
     * The {@link MathContext} is inferred from the argument {@code x}.
     * @param x the number for which we wish to take a logarithm
     * @param base the base of the logarithm
     * @return the logarithm of {@code x} in {@code base}
     */
    public static RealType log(RealType x, RealType base) {
        return log(x, base, x.getMathContext());
    }

    /**
     * Efficiently calculate &#x230A;log<sub>2</sub>(x)&#x230B; for any
     * integer, rational, or real value x.
     * <br><strong>Note:</strong> this method does not support zero or
     * negative arguments.
     * @param val a {@link Numeric} that is not a complex value
     * @return the floor of log<sub>2</sub>({@code val})
     */
    public static IntegerType log2floor(Numeric val) {
        boolean noFrac = true;
        BigInteger intermediate;
        if (val instanceof IntegerType) {
            intermediate = ((IntegerType) val).asBigInteger();
        } else if (val instanceof RealType) {
            noFrac = val.isCoercibleTo(IntegerType.class);
            intermediate = ((RealType) val).asBigDecimal().toBigInteger();
        } else if (val instanceof RationalType) {
            RationalType that = (RationalType) val;
            // use integer division instead of BigDecimal -> BigInteger conversion
            IntegerType[] quotient = that.divideWithRemainder();
            intermediate = quotient[0].asBigInteger();
            noFrac = quotient[1].asBigInteger().equals(BigInteger.ZERO);
        } else {
            if (!(val instanceof ComplexType)) {
                if (One.isUnity(val)) return new IntegerImpl(BigInteger.ZERO);
                throw new ArithmeticException("log\u2082(" + val + ") is undefined");
            }
            // Complex numbers are not comparable, therefore floor() has no meaning for them.
            throw new IllegalArgumentException("Complex arguments not supported");
        }
        if (intermediate.compareTo(BigInteger.ZERO) <= 0) {
            throw new ArithmeticException("log\u2082(x) undefined for x \u2264 0");
        }
        int highestBit = intermediate.bitLength() - 1;
        return new IntegerImpl(BigInteger.valueOf(highestBit), noFrac && intermediate.bitCount() == 1) {
            @Override
            public MathContext getMathContext() {
                return val.getMathContext();
            }
        };
    }

    /**
     * Computes the mantissa of a real value as expressed in scientific
     * notation, mantissa&nbsp;&times;&nbsp;10<sup>exponent</sup>.
     * @param x the real value
     * @return the mantissa of {@code x}
     */
    public static RealType mantissa(RealType x) {
        BigDecimal mantissa = x.asBigDecimal().scaleByPowerOfTen(x.asBigDecimal().scale() + 1 - x.asBigDecimal().precision());
        RealImpl result = new RealImpl(mantissa, x.getMathContext(), x.isExact());
        result.setIrrational(x.isIrrational());
        return result;
    }
    
    /**
     * Computes the exponent of a real value as expressed in scientific
     * notation, mantissa&nbsp;&times;&nbsp;10<sup>exponent</sup>.
     * @param x the real value
     * @return the exponent of {@code x}
     */
    public static IntegerType exponent(RealType x) {
        int exponent = x.asBigDecimal().precision() - x.asBigDecimal().scale() - 1;
        return new IntegerImpl(BigInteger.valueOf(exponent));  // the exponent should always be exact
    }
    
    /**
     * Compute the general case of x<sup>y</sup>, where x is a real number
     * and y is anything generally coercible to a real (i.e., integer,
     * rational, or real values).
     * @param base the value to raise to a given power
     * @param exponent the power to which we want to raise {@code base}
     * @param mctx the {@link MathContext} to use for this calculation
     * @return the value of base<sup>exponent</sup>
     */
    public static RealType generalizedExponent(RealType base, Numeric exponent, MathContext mctx) {
        if (Zero.isZero(exponent)) {
            try {
                return (RealType) One.getInstance(mctx).coerceTo(RealType.class);
            } catch (CoercionException ex) {
                Logger.getLogger(MathUtils.class.getName()).log(Level.SEVERE,
                        "Could not obtain a real instance of One", ex);
                throw new IllegalStateException(ex);
            }
        } else if (One.isUnity(exponent)) {
            return base;
        }
        if (exponent instanceof RealInfinity) {
            if (base.sign() == Sign.POSITIVE) {
                switch (((RealInfinity) exponent).sign()) {
                    case NEGATIVE:
                        return new RealImpl(BigDecimal.ZERO, mctx);
                    case POSITIVE:
                        return RealInfinity.getInstance(Sign.POSITIVE, mctx);
                    default:
                        throw new IllegalStateException("Unknown state for " + exponent);
                }
            } else {
                if (((RealInfinity) exponent).sign() == Sign.NEGATIVE) {
                    return new RealImpl(BigDecimal.ZERO, mctx);
                }
                throw new ArithmeticException(base + UnicodeTextEffects.convertToSuperscript(exponent.toString()) + " does not converge");
            }
        } else if (exponent instanceof PosInfinity) {
            if (base.sign() == Sign.POSITIVE) return RealInfinity.getInstance(Sign.POSITIVE, mctx);
            throw new ArithmeticException(base + UnicodeTextEffects.convertToSuperscript(exponent.toString()) + " does not converge");
        } else if (exponent instanceof NegInfinity) {
            return new RealImpl(BigDecimal.ZERO, mctx);
        }
        NumericHierarchy htype = NumericHierarchy.forNumericType(exponent.getClass());
        switch (htype) {
            case INTEGER:
                long n = ((IntegerType) exponent).asBigInteger().longValueExact();
                return computeIntegerExponent(base, n, mctx);
            case REAL:
                if (exponent.isCoercibleTo(IntegerType.class)) {
                    try {
                        IntegerType integer = (IntegerType) exponent.coerceTo(IntegerType.class);
                        return generalizedExponent(base, integer, mctx);
                    } catch (CoercionException ex) {
                        Logger.getLogger(MathUtils.class.getName()).log(Level.SEVERE, "Failed to coerce real to integer.", ex);
                        throw new IllegalStateException("Failed type coercion after test for coercibility", ex);
                    }
                }
                // approximate with a rational
                try {
                    RationalType ratexponent = (RationalType) exponent.coerceTo(RationalType.class);
                    return generalizedExponent(base, ratexponent, mctx);
                } catch (CoercionException ex) {
                    // recover by using exponential identity, which is more costly than rational exponentiation
                    final Euler e = Euler.getInstance(mctx);
                    // use the identity x^y = e^(y * ln(x))
                    RealType arg = (RealType) ln(base).multiply(exponent);
                    return e.exp(arg);
                }
            case RATIONAL:
                // use the identity b^(u/v) = vth root of b^u
                RationalType ratexponent = (RationalType) exponent;
                final long n_num = ratexponent.numerator().asBigInteger().longValueExact();
                RealType intermediate = computeIntegerExponent(base, n_num, mctx);
                return nthRoot(intermediate, ratexponent.denominator(), mctx);
            default:
                throw new ArithmeticException("Currently generalizedExponent() has no support for exponents of type " + exponent.getClass().getTypeName());
        }
    }

    /**
     * Method for raising a real-valued number to a complex-valued exponent.
     * This method is relatively efficient since it only needs to handle a very
     * specific type of exponentiation, and no coercion of {@code base} is required.
     * @param base     the real-valued base, i.e., the thing to be raised to some power
     * @param exponent a complex-valued exponent
     * @param mctx     the {@link MathContext} for the calculation
     * @return the complex-valued result of base<sup>exponent</sup>
     */
    public static ComplexType generalizedExponent(RealType base, ComplexType exponent, MathContext mctx) {
        // this logic could not be folded into the generalizedExponent() method above without changing that method's return type
        // this method should be the equivalent of converting base to a ComplexType and calling the generalizedExponent()
        // method below, but this method should be faster (uses real-valued ln(), no exp()) and involves fewer temporary objects
        return new ComplexPolarImpl(generalizedExponent(base, exponent.real(), mctx), (RealType) ln(base, mctx).multiply(exponent.imaginary()));
    }

    /**
     * Calculates b<sup>x</sup> for any complex value b and any value x.
     * @param base     b, the complex-valued base
     * @param exponent x, an exponent of any {@link Numeric} type whatsoever
     * @param mctx     the {@link MathContext} for the calculation
     * @return the complex-valued result of base<sup>exponent</sup>
     */
    public static ComplexType generalizedExponent(ComplexType base, Numeric exponent, MathContext mctx) {
        if (Zero.isZero(exponent)) {
            try {
                return (ComplexType) One.getInstance(mctx).coerceTo(ComplexType.class);
            } catch (CoercionException ex) {
                Logger.getLogger(MathUtils.class.getName()).log(Level.SEVERE,
                        "Could not obtain a complex instance of One", ex);
                throw new IllegalStateException(ex);
            }
        } else if (One.isUnity(exponent)) {
            return base;
        }
        NumericHierarchy htype = NumericHierarchy.forNumericType(exponent.getClass());
        switch (htype) {
            case INTEGER:
                long n = ((IntegerType) exponent).asBigInteger().longValueExact();
                return computeIntegerExponent(base, n, mctx);
            case REAL:
                if (exponent.isCoercibleTo(IntegerType.class)) {
                    try {
                        IntegerType integer = (IntegerType) exponent.coerceTo(IntegerType.class);
                        return generalizedExponent(base, integer, mctx);
                    } catch (CoercionException ex) {
                        Logger.getLogger(MathUtils.class.getName()).log(Level.SEVERE, "Failed to coerce real to integer.", ex);
                        throw new IllegalStateException("Failed type coercion after test for coercibility", ex);
                    }
                }
                // approximate with a rational
                try {
                    RationalType ratexponent = (RationalType) exponent.coerceTo(RationalType.class);
                    return generalizedExponent(base, ratexponent, mctx);
                } catch (CoercionException ex) {
                    // recover by using the exponential identity
                    final Euler e = Euler.getInstance(mctx);
                    ComplexType arg = (ComplexType) ln(base).multiply(exponent);
                    return e.exp(arg);
                }
            case RATIONAL:
                // use the identity b^(u/v) = vth root of b^u
                RationalType ratexponent = (RationalType) exponent;
                final long n_num = ratexponent.numerator().asBigInteger().longValueExact();
                ComplexType intermediate = computeIntegerExponent(base, n_num, mctx);
                RealType modulus = nthRoot(intermediate.magnitude(), ratexponent.denominator());
                RealType argument = (RealType) intermediate.argument().divide(ratexponent.denominator());
                return new ComplexPolarImpl(modulus, argument, false);
            case COMPLEX:
                final Euler e = Euler.getInstance(mctx);
                // use the identity z^w = e^(w⋅ln(z))
                ComplexType argForE = (ComplexType) ln(base).multiply(exponent);
                return e.exp(argForE);
            default:
                throw new ArithmeticException("Currently generalizedExponent() has no support for exponents of type " + exponent.getClass().getTypeName());
        }
    }

    /**
     * Compute the generalized exponent of a {@link Matrix} <strong>M</strong>, that is,
     * <strong>M</strong><sup>x</sup> where x is any {@link Numeric} value (e.g., real or complex).
     * This method uses the identity
     * <strong>M</strong><sup>x</sup>&nbsp;=&nbsp;&#x212f;<sup>x&sdot;ln(<strong>M</strong>)</sup><br>
     * and should work for all square matrices and exponent types. Note that this operation can be very
     * slow, so {@link Matrix#pow(Numeric)} is preferred in the case of integer exponents.
     * @param M        the matrix to exponentiate
     * @param exponent the power with which to raise M
     * @return the value of M<sup>exponent</sup>
     */
    public static Matrix<? extends Numeric> generalizedExponent(Matrix<? extends Numeric> M, Numeric exponent) {
        Matrix<? extends Numeric> logexp = ((Matrix<Numeric>) ln(M)).scale(exponent);
        return exp(logexp);
    }

    /**
     * Compute the n<sup>th</sup> root of a real value a.  The result is the principal
     * root of the equation x<sup>n</sup>&nbsp;=&nbsp;a.  Note that the {@link MathContext}
     * is inferred from the argument {@code a}.
     * @param a the value for which we want to find a root
     * @param n the degree of the root
     * @return the {@code n}<sup>th</sup> root of {@code a}
     */
    public static RealType nthRoot(RealType a, IntegerType n) {
        return nthRoot(a, n, a.getMathContext());
    }
    
    /**
     * Compute the n<sup>th</sup> root of a real value a.  The result is the principal
     * root of the equation x<sup>n</sup>&nbsp;=&nbsp;a.  The {@link MathContext}
     * is explicitly supplied.
     * @param a the value for which we want to find a root
     * @param n the degree of the root
     * @param mctx the {@link MathContext} to use for this calculation
     * @return the {@code n}th root of {@code a}
     */
    public static RealType nthRoot(RealType a, IntegerType n, MathContext mctx) {
        if (n.sign() != Sign.POSITIVE) throw new IllegalArgumentException("Degree of root must be positive");
        final BigDecimal A = a.asBigDecimal();
        if (A.compareTo(BigDecimal.ZERO) == 0) {
            try {
                return (RealType) ExactZero.getInstance(mctx).coerceTo(RealType.class);
            } catch (CoercionException ex) {
                // we should never get here
                throw new IllegalStateException(ex);
            }
        }
        if (n.isEven() && a.sign() == Sign.NEGATIVE) {
            throw new ArithmeticException("Cannot compute a real-valued " + n + "th root of " + a);
        }
        if (n.asBigInteger().longValue() == 2L && useBuiltInOperations()) {
            return new RealImpl(A.sqrt(mctx), mctx, a.isExact());  // faster but sloppier
        }

        // sadly, we need to use int here because we're relying on
        // BigDecimal.pow() for speed and efficiency
        // on the other hand, if we are taking nth roots of values where n > MAX_INTEGER,
        // we might have other problems...
        final int nint = n.asBigInteger().intValueExact();
        final BigDecimal ncalc = new BigDecimal(n.asBigInteger());
        final BigDecimal nminus1 = ncalc.subtract(BigDecimal.ONE);
        BigDecimal x0;
        BigDecimal x1 = A.divide(new BigDecimal(n.asBigInteger()), mctx); // initial estimate

        do {
            x0 = x1;
            x1 = nminus1.multiply(x0, mctx).add(A.divide(x0.pow(nint - 1, mctx), mctx), mctx).divide(ncalc, mctx);
            BigDecimal delta = x0.subtract(x1, mctx).abs();
            if (delta.compareTo(x0.ulp()) <= 0) break;  // to ensure we are not stuck in an infinite loop
        } while (x0.compareTo(x1) != 0);
        x1 = x1.stripTrailingZeros();
        boolean irrational = classifyIfIrrational(x1, mctx);
        final RealImpl result = new RealImpl(x1, a.isExact() && !irrational);
        result.setMathContext(mctx);
        result.setIrrational(irrational);
        return result;
    }
    
    private static boolean classifyIfIrrational(BigDecimal realval, MathContext mctx) {
        if (realval.scale() <= 0) return false;  // this is an integer
        IntegerType nonFractionPart = new IntegerImpl(realval.toBigInteger());
        int reducedDigitLength = mctx.getPrecision() - (int) nonFractionPart.numberOfDigits();
        return reducedDigitLength == realval.scale();
    }
    
    /**
     * Compute the n<sup>th</sup> roots of unity, &#x212f;<sup>2&pi;&#x2148;k/n</sup> for
     * {k=0,&thinsp;1,&thinsp;2,&thinsp;&hellip;,&thinsp;n&minus;1}.
     * @param n the degree of the roots
     * @param mctx the {@link MathContext} for computing these values
     * @return a {@link Set} of {@code n} complex roots
     */
    public static Set<ComplexType> rootsOfUnity(long n, MathContext mctx) {
        if (n < 1L) throw new IllegalArgumentException("Degree of roots must be \u2265 1");
        final RealImpl decTwo = new RealImpl(new BigDecimal(BigInteger.TWO), mctx);
        decTwo.setMathContext(mctx);
        final RealImpl decOne = new RealImpl(BigDecimal.ONE, mctx);
        decOne.setMathContext(mctx);
        final RealType twopi = (RealType) Pi.getInstance(mctx).multiply(decTwo);
        NumericSet set = new NumericSet();
        for (long k = 0L; k < n; k++) {
            RationalType expFactor = new RationalImpl(k, n, mctx);
            ComplexPolarImpl val = new ComplexPolarImpl(decOne, (RealType) twopi.multiply(expFactor));
            val.setMathContext(mctx);
            set.append(val);
        }
        try {
            return set.coerceTo(ComplexType.class);
        } catch (CoercionException ex) {
            Logger.getLogger(MathUtils.class.getName()).log(Level.SEVERE, "NumericSet -> Set<ComplexType>", ex);
            throw new IllegalStateException("We should never have gotten here!", ex);
        }
    }

    /**
     * Method intended to determine the lowest precision of a {@link Collection} of {@link Numeric} arguments.
     * @param args a {@link Collection} of {@link Numeric} arguments
     * @return a {@link MathContext} constructed from the given arguments, or {@link MathContext#UNLIMITED} if none can be inferred from arguments
     */
    public static MathContext inferMathContext(Collection<? extends Numeric> args) {
        int precision = args.stream().mapToInt(x -> x.getMathContext().getPrecision()).filter(x -> x > 0).min().orElse(-1);
        if (precision > 0) {
            return new MathContext(precision, findMostCommonRoundingMode(args.stream().map(Numeric::getMathContext).collect(Collectors.toSet())));
        }
        return MathContext.UNLIMITED;
    }

    private static RoundingMode findMostCommonRoundingMode(Collection<MathContext> mathContexts) {
        final Map<RoundingMode, Integer> counts = new HashMap<>();
        mathContexts.forEach(ctx -> counts.put(ctx.getRoundingMode(), counts.getOrDefault(ctx.getRoundingMode(), 0) + 1));
        return counts.entrySet().stream().sorted((e1, e2) -> e2.getValue().compareTo(e1.getValue())).map(Map.Entry::getKey)
                .findFirst().orElse(RoundingMode.HALF_UP);
    }

    /**
     * Render a real value in scientific notation.
     * @param value the value to render
     * @return a {@link String} representation of {@code value} rendered in scientific notation
     */
    public static String inScientificNotation(RealType value) {
        return convertToScientificNotation(value.asBigDecimal());
    }

    /**
     * Render a rational value in scientific notation. The formatting is decimal; if
     * traditional fractional representation is desired, consider using {@code value.toString()}
     * instead, or perhaps separately render the numerator and denominator in scientific notation.
     * @param value the value to render
     * @return a {@link String} representation of {@code value} rendered in scientific notation
     */
    public static String inScientificNotation(RationalType value) {
        return convertToScientificNotation(value.asBigDecimal());
    }
    
    private static String convertToScientificNotation(BigDecimal decValue) {
        if (decValue.scale() <= 0) {
            IntegerImpl temp = new IntegerImpl(decValue.toBigIntegerExact());
            return inScientificNotation(temp);
        }
        StringBuilder buf = new StringBuilder();
        
        int exponent = decValue.scale();
        BigDecimal temp = decValue;
        while (temp.abs().compareTo(BigDecimal.TEN) > 0) {
            temp = temp.movePointLeft(1);
            exponent++;
        }
        buf.append(temp.toPlainString()).append("\u2009\u00D7\u200910");
        buf.append(UnicodeTextEffects.numericSuperscript(exponent));
        
        return buf.toString();
    }

    /**
     * Render an integer value in scientific notation.
     * @param value the value to render
     * @return a {@link String} representation of {@code value} rendered in scientific notation
     */
    public static String inScientificNotation(IntegerType value) {
        long digits = value.numberOfDigits();
        int exponent = (int) (digits - 1L);
        final DecimalFormatSymbols dfSymbols = DecimalFormatSymbols.getInstance();
        StringBuilder buf = new StringBuilder();
        buf.append(value.asBigInteger());
        int insertionPoint = 1;
        if (value.sign() == Sign.NEGATIVE) insertionPoint++;
        buf.insert(insertionPoint, dfSymbols.getDecimalSeparator());
        // U+2009 is thin space, U+00D7 is multiplication symbol
        buf.append("\u2009\u00D7\u200910").append(UnicodeTextEffects.numericSuperscript(exponent));
        
        return buf.toString();
    }

    private static final RealType TEN = new RealImpl(BigDecimal.TEN, MathContext.UNLIMITED);
    
    /**
     * Generate a matrix of rotation in 2 dimensions.
     * 
     * @param theta the angle of rotation in radians around the origin
     * @return a 2&times;2 matrix of rotation
     */
    public static Matrix<RealType> get2DMatrixOfRotation(RealType theta) {
        RealType[][] temp = new RealType[2][2];

        RealType cos = cos(theta);
        RealType sin = sin(theta);

        temp[0][0] = cos;
        temp[0][1] = sin.negate();
        temp[1][0] = sin;
        temp[1][1] = cos;
        
        return new BasicMatrix<>(temp);
    }
    
    /**
     * Generate a matrix of rotation in 3 dimensions.
     * 
     * @param theta the angle of rotation in radians
     * @param axis the major axis around which the rotation is to occur
     * @return a 3&times;3 matrix of rotation
     * @see <a href="https://en.wikipedia.org/wiki/Rotation_matrix">the Wikipedia article on matrices of rotation</a>
     */
    public static Matrix<RealType> get3DMatrixOfRotation(RealType theta, Axis axis) {
        final MathContext ctx = theta.getMathContext();
        final RealType one = new RealImpl(BigDecimal.ONE, ctx);
        final RealType zero = new RealImpl(BigDecimal.ZERO, ctx);
        RealType[][] temp = new RealType[3][];

        RealType cos = cos(theta);
        RealType sin = sin(theta);

        switch (axis) {
            case X_AXIS:
                temp[0] = new RealType[] { one, zero, zero };
                temp[1] = new RealType[] { zero, cos, sin.negate() };
                temp[2] = new RealType[] { zero, sin, cos };
                break;
            case Y_AXIS:
                temp[0] = new RealType[] { cos, zero, sin };
                temp[1] = new RealType[] { zero, one, zero };
                temp[2] = new RealType[] { sin.negate(), zero, cos };
                break;
            case Z_AXIS:
                temp[0] = new RealType[] { cos, sin.negate(), zero };
                temp[1] = new RealType[] { sin, cos, zero };
                temp[2] = new RealType[] { zero, zero, one };
                break;
        }
        return new BasicMatrix<>(temp);
    }

    private static final EnumMap<Axis, Long> axisToIndex = new EnumMap<>(Axis.class);
    static {
        axisToIndex.put(Axis.X_AXIS, 0L);
        axisToIndex.put(Axis.Y_AXIS, 1L);
        axisToIndex.put(Axis.Z_AXIS, 2L);
    }

    /**
     * Determine if a vector is aligned with the given axis.
     * This is calculated by checking whether the vector has a non-zero
     * value for only one of its dimensions.
     * @param vector the vector to test
     * @param axis   the {@link Axis} against which we are testing
     * @return true if the given vector aligns with the given axis
     */
    public static boolean isAlignedWith(Vector<RealType> vector, Axis axis) {
        long idx = axisToIndex.get(axis);
        boolean match = false;
        for (int k = 0; k < vector.length(); k++) {
            if (k == idx) {
                // we should have a single non-zero element corresponding with the axis
                match = !Zero.isZero(vector.elementAt(k));
            } else {
                // all elements other than those aligned with the given axis should be zero
                if (!Zero.isZero(vector.elementAt(k))) return false;
            }
        }
        return match;
    }

    /**
     * Determine if a vector aligns with any spatial axis.
     * @param vector the vector to test
     * @return the {@link Axis} that {@code vector} aligns with, or {@code null}
     *  if the vector does not align fully with any axis.
     */
    public static Axis axisAlignedWith(Vector<RealType> vector) {
        return Arrays.stream(Axis.values()).filter(x -> isAlignedWith(vector, x))
                .findFirst().orElse(null);
    }

    /**
     * Compute the Hadamard product for two n&times;n matrices.
     * In standard notation, that is <strong>A&#x2218;B</strong> or A&#x2299;B.
     * Note that, unlike regular matrix multiplication, the Hadamard
     * product is commutative.
     * @param A the first matrix in the product
     * @param B the second matrix in the product
     * @return the Hadamard product A&#x2299;B
     * @param <T> the element type for the input matrices
     */
    public static <T extends Numeric> Matrix<T> hadamardProduct(Matrix<T> A, Matrix<T> B) {
        if (A.rows() != B.rows() || A.columns() != B.columns()) {
            throw new ArithmeticException("Matrices must be of equal dimension");
        }
        return new ParametricMatrix<>(A.rows(), A.columns(),
                (row, column) -> (T) A.valueAt(row, column).multiply(B.valueAt(row, column)));
    }

    /**
     * Compute the Hadamard product of two vectors of the same length.
     * @param a the first vector
     * @param b the second vector
     * @return the Hadamard product a&#x2299;b
     * @param <T> the element type for the input vectors
     */
    public static <T extends Numeric> Matrix<T> hadamardProduct(Vector<T> a, Vector<T> b) {
        if (a.length() != b.length()) throw new ArithmeticException("Vectors must be of equal dimension");
        Matrix<T> diag = new DiagonalMatrix<>(a);
        ColumnVector<T> col = new ArrayColumnVector<>(b);
        return diag.multiply(col);
    }

    public static long MAX_CLONE_DEPTH = (long) Integer.MAX_VALUE >> 4L;

    /**
     * Compute the conjugate transpose of a given matrix A, denoted A<sup>*</sup>
     * or A<sup>&#x2020;</sup>.
     * This is equivalent to taking the transpose of A and then taking the complex
     * conjugate of each value contained therein.
     * @param original the original matrix for which we want the conjugate transpose
     * @return the conjugate transpose of {@code original}
     */
    public static Matrix<ComplexType> conjugateTranspose(Matrix<? extends Numeric> original) {
        if (original instanceof Vector) {
            Vector<Numeric> asVector = (Vector<Numeric>) original;
            ComplexType[] cplxElements = new ComplexType[(int) asVector.length()];
            try {
                for (int idx = 0; idx < cplxElements.length; idx++)
                    cplxElements[idx] = ((ComplexType) asVector.elementAt(idx).coerceTo(ComplexType.class)).conjugate();
            } catch (CoercionException fail) {
                Logger.getLogger(MathUtils.class.getName()).log(Level.SEVERE, "While computing complex conjugate before transpose.", fail);
                throw new ArithmeticException("Unable to compute conjugate for " + asVector);
            }
            if (asVector instanceof RowVector) {
                return new ArrayColumnVector<>(cplxElements);
            } else if (asVector instanceof ColumnVector) {
                return new ArrayRowVector<>(cplxElements);
            }
            throw new IllegalStateException("Unknown class implementing both Matrix and Vector: " + original.getClass().getTypeName());
        }
        // if the matrix is big enough, return a transformed view
        if (original.rows() > MAX_CLONE_DEPTH || original.columns() > MAX_CLONE_DEPTH) {
            return new ParametricMatrix<>(original.columns(), original.rows(), (row, column) -> {
                try {
                    ComplexType interim = (ComplexType) original.valueAt(column, row).coerceTo(ComplexType.class);
                    return interim.conjugate();
                } catch (CoercionException e) {
                    throw new ArithmeticException("Could not compute conjugate for element at " + row + ", " + column);
                }
            });
        }
        // otherwise use a 2D array as a working copy
        ComplexType[][] working = new ComplexType[(int) original.columns()][(int) original.rows()];
        try {
            for (long j = 0L; j < original.columns(); j++) {
                for (long k = 0L; k < original.rows(); k++) {
                    ComplexType value = (ComplexType) original.valueAt(k, j).coerceTo(ComplexType.class);
                    working[(int) j][(int) k] = value.conjugate();
                }
            }
        } catch (CoercionException e) {
            throw new IllegalStateException("Any type should be coercible to ComplexType", e);
        }
        return new BasicMatrix<>(working);
    }

    /**
     * Compute the Hilbert-Schmidt norm for a matrix, written as
     * (Tr[X<sup>&#x2020;</sup>X])<sup>1/2</sup>.
     * This is an appropriate matrix norm to use when determining
     * whether certain series can be used for computing e.g.
     * a matrix logarithm or square root.
     * @param M any matrix for which we wish to compute the norm
     * @return the Hilbert-Schmidt norm of the given matrix
     * @since 0.4
     */
    public static RealType hilbertSchmidtNorm(Matrix<? extends Numeric> M) {
        Matrix<ComplexType> orig = new ComplexMatrixAdapter(M);
        Matrix<ComplexType> dagger = conjugateTranspose(M);
        if (M.rows() == M.columns() && M.rows() > 3L) {
            // just compute directly to avoid computing unnecessary dot products
            return LongStream.range(0L, M.rows()).parallel()
                    .mapToObj(idx -> dagger.getRow(idx).dotProduct(orig.getColumn(idx)))
                    .reduce((x, y) -> (ComplexType) x.add(y)).map(ComplexType::sqrt)
                    .map(MathUtils::Re).orElseThrow(() -> new ArithmeticException("No elements to compute norm"));
        }
        ComplexType trace = dagger.multiply(orig).trace();
        try {
            return (RealType) trace.sqrt().coerceTo(RealType.class);
        } catch (CoercionException e) {
            throw new ArithmeticException("Hilbert-Schmidt norm \u221A(" + trace + ") is not real");
        }
    }

    /**
     * Compute &#x212f;<sup>X</sup> for a square matrix <strong>X</strong>.
     * Since the calculation is an infinite series, we only compute k terms,
     * where k is derived from the {@link MathContext} of the elements in {@code X}.
     * @param X a n&times;n matrix
     * @return the n&times;n matrix that is an approximation of &#x212f;<sup>X</sup>
     */
    public static Matrix<? extends Numeric> exp(Matrix<? extends Numeric> X) {
        if (X instanceof DiagonalMatrix) return ((DiagonalMatrix<? extends Numeric>) X).exp();
        if (X instanceof SingletonMatrix || (X.columns() == 1L && X.rows() == 1L)) {
            final Numeric value = X.valueAt(0L, 0L);
            final Euler e = Euler.getInstance(value.getMathContext());
            try {
                return new SingletonMatrix<>(value instanceof ComplexType ? e.exp((ComplexType) value) :
                        e.exp((RealType) value.coerceTo(RealType.class)));
            } catch (CoercionException ex) {
                throw new ArithmeticException("Cannot compute \u212Fˣ: " + ex.getMessage());
            }
        }
        if (X.rows() != X.columns()) throw new ArithmeticException("Cannot compute exp for a non-square matrix");
        final MathContext ctx = X.valueAt(0L, 0L).getMathContext();
        if (ZeroMatrix.isZeroMatrix(X)) return new IdentityMatrix(X.rows(), ctx);
        if (X.rows() > 4L && X.isUpperTriangular()) {
            Logger.getLogger(MathUtils.class.getName()).info("Attempting the Parlett method for computing exp");
            final Euler e = Euler.getInstance(ctx);
            NumericHierarchy h = NumericHierarchy.forNumericType(OptionalOperations.findTypeFor(X));
            switch (h) {
                case REAL:
                    return parlett(x -> e.exp((RealType) x), X);
                case COMPLEX:
                    return parlett(z -> e.exp((ComplexType) z), X);
                // Note: The rest of these are faster but less safe than using coerceTo()
                case RATIONAL:
                    return parlett(x -> e.exp(new RealImpl((RationalType) x)), X);
                case INTEGER:
                    return parlett(x -> e.exp(new RealImpl((IntegerType) x)), X);
                default:
                    Logger.getLogger(MathUtils.class.getName()).log(Level.FINE,
                            "No mapping function available for exp() with argument type {0}.",
                            OptionalOperations.findTypeFor(X).getTypeName());
                    // if we got here, we're going to fall through to the series calculation below
                    break;
            }
        }
        Logger.getLogger(MathUtils.class.getName()).log(Level.INFO,
                "Computing exp of matrix with MathContext = {0}", ctx);
        Matrix<Numeric> intermediate = new ZeroMatrix(X.rows(), ctx);
        // since this series can converge (very) slowly, this multiplier may need to be increased
        long sumLimit = 32L * ctx.getPrecision() + 5L; // will get at least 5 terms if precision = 0 (Unlimited)
        for (long k = 0L; k < sumLimit; k++) {
            IntegerType kval = new IntegerImpl(BigInteger.valueOf(k)) {
                @Override
                public MathContext getMathContext() {
                    return ctx;
                }
            };
            Matrix<Numeric> mtxPower = (Matrix<Numeric>) X.pow(kval);
            if (ZeroMatrix.isZeroMatrix(mtxPower)) break; // nilpotent matrix check
            intermediate = intermediate.add(mtxPower.scale(factorial(kval).inverse()));
        }
        // return a special anonymous subclass of BasicMatrix which computes the
        // determinant based on the trace of X, which is much cheaper than the default calculation
        return new BasicMatrix<>(intermediate) {
            @Override
            public Numeric determinant() {
                final Euler e = Euler.getInstance(ctx);
                Numeric tr = X.trace();
                if (tr instanceof ComplexType) {
                    return e.exp((ComplexType) tr);
                } else {
                    try {
                        return e.exp((RealType) tr.coerceTo(RealType.class));
                    } catch (CoercionException ex) {
                        throw new IllegalStateException("While computing determinant from trace", ex);
                    }
                }
            }
        };
    }

    /**
     * Compute the natural logarithm of a {@link Matrix} <strong>X</strong>.
     * This method will attempt several different ways to compute the natural
     * logarithm.
     * @param X the matrix for which we wish to compute the natural logarithm
     * @return a matrix representing ln(<strong>X</strong>) which satisfies &#x212F;<sup>ln(X)</sup>=&thinsp;X
     * @see <a href="https://eprints.maths.manchester.ac.uk/318/1/36401.pdf">Approximating the logarithm of a matrix to
     *   specified accuracy</a> by Cheng et al.
     * @see <a href="https://en.wikipedia.org/wiki/Logarithm_of_a_matrix">the Wikipedia article on matrix logarithms</a>
     */
    @Experimental
    public static Matrix<? extends Numeric> ln(Matrix<? extends Numeric> X) {
        if (X instanceof DiagonalMatrix) return ((DiagonalMatrix<? extends Numeric>) X).ln();
        if (X instanceof SingletonMatrix || (X.columns() == 1L && X.rows() == 1L)) {
            final Numeric value = X.valueAt(0L, 0L);
            try {
                return new SingletonMatrix<>(value instanceof ComplexType ? ln((ComplexType) value) :
                        ln((RealType) value.coerceTo(RealType.class)));
            } catch (CoercionException ex) {
                throw new ArithmeticException("Cannot compute ln(X): " + ex.getMessage());
            }
        }
        final Logger logger = Logger.getLogger(MathUtils.class.getName());
        if (X.rows() != X.columns()) throw new ArithmeticException("Cannot compute ln for a non-square matrix");
        final MathContext ctx = X.valueAt(0L, 0L).getMathContext();
        final Matrix<Numeric> I = new IdentityMatrix(X.rows(), ctx);
        if (I.equals(X)) return new ZeroMatrix(X.rows(), ctx);  // ln(I) = 0
        if (X.isUpperTriangular()) {
            logger.info("Attempting the Parlett method for computing ln");
            NumericHierarchy h = NumericHierarchy.forNumericType(OptionalOperations.findTypeFor(X));
            switch (h) {
                case REAL:
                    return parlett(x -> ln((RealType) x), X);
                case COMPLEX:
                    return parlett(z -> ln((ComplexType) z), X);
                // Note: The rest of these are faster but less safe than using coerceTo()
                case RATIONAL:
                    return parlett(x -> ln(new RealImpl((RationalType) x)), X);
                case INTEGER:
                    return parlett(x -> ln(new RealImpl((IntegerType) x)), X);
                default:
                    logger.log(Level.FINE,
                            "No mapping function available for ln() with argument type {0}.",
                            OptionalOperations.findTypeFor(X).getTypeName());
                    // if we got here, we're going to fall through to the calculations below
                    break;
            }
        }
        if (X.rows() == 2L && RationalType.class.isAssignableFrom(OptionalOperations.findTypeFor(X))) {
            logger.fine("Checking for a special type of 2\u00D72 rational matrix");
            Matrix<RationalType> source = (Matrix<RationalType>) X;
            if (source.valueAt(0L, 0L).equals(source.valueAt(1L, 1L)) &&
                    source.valueAt(0L, 1L).equals(source.valueAt(1L, 0L))) {
                RationalType roq = source.valueAt(0L, 0L);
                RationalType poq = source.valueAt(0L, 1L);
                if (roq.denominator().equals(poq.denominator())) {
                    IntegerType q = roq.denominator();
                    IntegerType p = poq.numerator();
                    IntegerType r = roq.numerator();
                    logger.log(Level.INFO,
                            "Found a rational matrix with p={0}, r={1}, and denominator q={2}; computing logarithm.",
                            new Object[] {p, r, q});
                    RealType a = (RealType) ln((IntegerType) p.add(r), ctx).subtract(ln(q, ctx));
                    RealType zero = new RealImpl(BigDecimal.ZERO, ctx);
                    RealType[][] result = new RealType[][] {{zero, a}, {a, zero}};
                    return new BasicMatrix<>(result);
                }
            }
        }

        final RealType one = new RealImpl(BigDecimal.ONE, ctx);
        final Numeric two = new RealImpl(decTWO, ctx);
        if (X.rows() == 2L && hilbertSchmidtNorm(calcAminusI(X)).compareTo(one) > 0) {
            logger.fine("We have a 2×2 matrix, so computing exact sqrt and recursing to compute ln of that result");
            // we can get an exact square root for a 2×2 matrix, so recursively compute ln using this identity
            return ((Matrix<Numeric>) ln(fast2x2Sqrt(X))).scale(two);
        }
        if (hilbertSchmidtNorm(calcAminusI(X)).compareTo(one) < 0) {
            logger.fine("||X - I|| < 1, computing ln(X) using series.");
            return lnSeries(X);
        }
        // TODO check the norm first before using D-B at all
        // per Cheng et al., we can approximate the logarithm recursively using
        // a square root identity and the Denman-Beavers iteration
        logger.fine("Using square root identity to recursively compute ln(X) using Denman-Beavers iteration.");
        // log A = 2 log Yk − log YkZk
        try {
            Matrix<Numeric> Y = (Matrix<Numeric>) denmanBeavers(X, 8); // don't need it to be perfect, so limit to 8 iterations
            Matrix<Numeric> Z = (Matrix<Numeric>) Y.inverse();  // this is computed for free by Denman-Beavers
            return ((Matrix<Numeric>) ln(Y)).scale(two).subtract((Matrix<Numeric>) ln(Y.multiply(Z)));  // YZ should converge to I
        } catch (ConvergenceException e) {
            // if Denman-Beavers iteration fails, fall back to computing a power series -- slower but should work over the same domain
            Matrix<? extends Numeric> Y = sqrtPowerSeries(X);
            return ((Matrix<Numeric>) ln(Y)).scale(two);
        }
    }

    /**
     * Compute ln(<strong>B</strong>) for matrix <strong>B</strong> using a
     * series.  Note that this will only converge if ||<strong>B</strong>&nbsp;&minus;&nbsp;&#x1D7D9;||&nbsp;&lt;&nbsp;1
     * @param B the matrix for which to compute the natural logarithm
     * @return a matrix A such that &#x212F;<sup>A</sup>=&thinsp;B
     */
    private static Matrix<? extends Numeric> lnSeries(Matrix<? extends Numeric> B) {
        final MathContext ctx = B.valueAt(0L, 0L).getMathContext();
        final Matrix<? extends Numeric> M = calcAminusI(B);
        final long sumlimit = 32L * ctx.getPrecision() + 5L;
        Logger.getLogger(MathUtils.class.getName()).log(Level.FINE,
                "Computing ln() of a {0}\u00D7{1} matrix with {2} series terms.",
                new Object[] { B.rows(), B.columns(), sumlimit });

        Matrix<Numeric> intermediate = new ZeroMatrix(B.rows(), ctx);
        for (long k = 1L; k < sumlimit; k++) {
            // (-1)ⁿ⁺¹ is computed for the numerator
            RationalType factor = new RationalImpl(k % 2L == 1L ? 1L : -1L, k, ctx);
            IntegerType n = new IntegerImpl(BigInteger.valueOf(k));
            Matrix<Numeric> mtxpow = (Matrix<Numeric>) M.pow(n);
            if (ZeroMatrix.isZeroMatrix(mtxpow)) break; // nilpotency check
            intermediate = intermediate.add(mtxpow.scale(factor));
        }
        return intermediate;
    }

    /**
     * Compute ln(<strong>A</strong>) for matrix <strong>A</strong> using
     * a Gregory series.  This will converge for any Hermitian positive definite
     * matrix.
     * @param A the matrix for which we wish to find the natural log
     * @return the natural log, ln(<strong>A</strong>)
     * @throws ConvergenceException if this series will not converge
     * @see <a href="https://scipp.ucsc.edu/~haber/webpage/MatrixExpLog.pdf">this paper</a>
     *   by Howard E. Haber
     */
    private static Matrix<? extends Numeric> lnGregorySeries(Matrix<? extends Numeric> A) throws ConvergenceException {
        final MathContext ctx = A.valueAt(0L, 0L).getMathContext();
        if (ComplexType.class.isAssignableFrom(OptionalOperations.findTypeFor(A))) {
            Matrix<ComplexType> cplxA = (Matrix<ComplexType>) A;
            if (!isHermitian(cplxA)) {
                Logger.getLogger(MathUtils.class.getName()).warning("The given matrix is not Hermitian, and will not converge.");
                // TODO do we really want to throw this exception?  Do we know for a fact that ln(A) will not converge if A is not Hermitian?
                throw new ConvergenceException("ln(A) will not converge because A is not Hermitian");
            }
        } else {
            // for non-complex matrices, we just check equality with the transpose, not the conjugate transpose
            if (!isSymmetric(A)) {
                Logger.getLogger(MathUtils.class.getName()).info("The given matrix is not symmetric (not equal to its transpose), and may not converge");
            }
        }
        Matrix<? extends Numeric> IminA = calcIminusA(A);
        Matrix<? extends Numeric> IplusAinv = calcAplusI(A).inverse();
        // these casts are ugly TODO let's see if we can get rid of them
        Matrix<? extends Numeric> term = ((Matrix<Numeric>) IminA).multiply((Matrix<Numeric>) IplusAinv);

        final long sumlimit = 32L * ctx.getPrecision() + 5L;
        Logger.getLogger(MathUtils.class.getName()).log(Level.FINE,
                "Computing ln() of a {0}\u00D7{1} matrix with {2} Gregory series terms.",
                new Object[] { A.rows(), A.columns(), sumlimit });

        Matrix<Numeric> intermediate = new ZeroMatrix(A.rows(), ctx);
        for (long k = 1L; k < sumlimit; k++) {
            final long dval = 2L * k + 1L;
            RationalType factor = new RationalImpl(-2L, dval, ctx);
            IntegerType n = new IntegerImpl(BigInteger.valueOf(dval));
            Matrix<Numeric> mtxpow = (Matrix<Numeric>) term.pow(n);
            if (ZeroMatrix.isZeroMatrix(mtxpow)) break; // nilpotency check
            intermediate = intermediate.add(mtxpow.scale(factor));
        }
        return intermediate;
    }

    /**
     * Compute the Moore-Penrose inverse of a matrix.  This is a generalization of the
     * inverse of a square matrix, and can be used to solve a linear system of equations
     * represented by a non-square matrix. Given a matrix A, the Moore-Penrose inverse
     * is written as A<sup>+</sup>.<br>
     * If the supplied matrix is neither of full column rank nor full row rank, the
     * iterative algorithm by Ben-Israel and Cohen will be used.
     * @param M the {@link Matrix} for which to compute the Moore-Penrose inverse
     * @return the Moore-Penrose inverse of {@code M}, denoted M<sup>+</sup>
     * @see <a href="https://en.wikipedia.org/wiki/Moore%E2%80%93Penrose_inverse">the related article at Wikipedia</a>
     */
    public static Matrix<? extends Numeric> pseudoInverse(Matrix<? extends Numeric> M) {
        // if M is square, it's a degenerate case
        if (M.rows() == M.columns()) {
            return M.inverse();
        }
        // otherwise compute the pseudoinverse
        long rank = rank(M);
        Matrix<ComplexType> Mcxp = conjugateTranspose(M);
        Matrix<ComplexType> Mcplx = new ParametricMatrix<>(M.rows(), M.columns(), (row, column) -> {
            try {
                return (ComplexType) M.valueAt(row, column).coerceTo(ComplexType.class);
            } catch (CoercionException ce) {
                throw new IllegalStateException(String.format("Unable to upconvert element %s at %d,\u2009%d",
                        M.valueAt(row, column), row, column), ce);
            }
        });
        final Logger logger = Logger.getLogger(MathUtils.class.getName());
        if (rank == M.columns()) {
            // full column rank
            logger.log(Level.FINE, "Computing A\u20F0A");
            Matrix<ComplexType> prod = Mcxp.multiply(Mcplx);  // A⃰A
            logger.log(Level.FINE, "Computing A\u207A = (A\u20F0A)\u207B\u00B9A\u20F0");
            return ((Matrix<ComplexType>) prod.inverse()).multiply(Mcxp);
        } else if (rank == M.rows()) {
            // full row rank
            logger.log(Level.FINE, "Computing AA\u20F0");
            Matrix<ComplexType> prod = Mcplx.multiply(Mcxp);  // AA⃰
            logger.log(Level.FINE, "Computing A\u207A = A\u20F0(AA\u20F0)\u207B\u00B9");
            return Mcxp.multiply((Matrix<ComplexType>) prod.inverse());
        } else {
            final ComplexType sigma = sigma_1(Mcplx);
            final RealType two = new RealImpl(decTWO, sigma.getMathContext());
            ComplexType sigSquared = (ComplexType) sigma.multiply(sigma.conjugate());  // this should actually be a real (i.e., zero imaginary part)
            ComplexType maxAlpha = (ComplexType) two.divide(sigSquared);  // should work without coercion
            final RealType zero = new RealImpl(BigDecimal.ZERO, sigma.getMathContext());
            Range<RealType> alphaRange = new Range<>(zero, maxAlpha.real(), BoundType.EXCLUSIVE);
            final ComplexType scale = new ComplexRectImpl(random(alphaRange));
            ComplexType cplxTwo = new ComplexRectImpl(two, zero, true);

            // take the iterative approach
            Matrix<ComplexType> intermediate = Mcxp.scale(scale);
            final int iterationLimit = 3 * sigma.getMathContext().getPrecision() + 2;  // Needs tuning!
            logger.log(Level.INFO, "Computing {3} terms of convergent series A\u2093\u208A\u2081 = 2A\u2093 - A\u2093AA\u2093 for a " +
                    "{0}\u00D7{1} matrix A with rank {2}.", new Object[] {M.rows(), M.columns(), rank, iterationLimit});
            int count = 0;
            do {
                intermediate = intermediate.scale(cplxTwo).subtract(intermediate.multiply(Mcplx).multiply(intermediate));
            } while (++count < iterationLimit);  // TODO find a better way to estimate how many iterations we need
            return intermediate;
        }
    }

    /**
     * Given a matrix <strong>A</strong>, compute its square root.  The resulting matrix B
     * satisfies the relationship <strong>B&sdot;B</strong>&nbsp;=&nbsp;<strong>A</strong>,
     * or alternately <strong>B</strong><sup>2</sup>&nbsp;=&nbsp;<strong>A</strong>.
     * This method returns the principal root of {@code A}.
     * @param A any matrix
     * @return the matrix which is the square root of {@code A}
     * @see <a href="https://www.sciencedirect.com/science/article/pii/002437958380010X">A Schur Method
     *   for the Square Root of a Matrix</a> by Åke Björck and Sven Hammarling
     */
    public static Matrix<? extends Numeric> sqrt(Matrix<? extends Numeric> A) {
        if (A instanceof IdentityMatrix) return A;
        if (A instanceof DiagonalMatrix) {
            Numeric[] elements = LongStream.range(0L, A.rows()).mapToObj(idx -> A.valueAt(idx, idx))
                    .map(Numeric::sqrt).toArray(Numeric[]::new);
            return new DiagonalMatrix<>(elements);
        }
        if (A instanceof SingletonMatrix || (A.rows() == 1L && A.columns() == 1L)) {
            return new SingletonMatrix<>(A.valueAt(0L, 0L).sqrt());
        }
        // if it's a 2×2 matrix, we have an exact solution
        if (A.rows() == 2L && A.columns() == 2L) {
            return fast2x2Sqrt(A);
        }
        // if A is upper triangular and has no more than 1 diagonal element = 0
        if (A.isUpperTriangular() &&
                LongStream.range(0L, A.rows()).mapToObj(idx -> A.valueAt(idx, idx)).filter(Zero::isZero).count() <= 1L) {
            // see https://www.sciencedirect.com/science/article/pii/002437958380010X?ref=cra_js_challenge&fr=RR-1 section 3
            return parlett(Numeric::sqrt, A);
        }
        // TODO implement other algorithms, factor out the 2×2 case
        // TODO also check the norm first before we use Denman-Beavers or the power series
        final RealType one = new RealImpl(BigDecimal.ONE, A.valueAt(0L, 0L).getMathContext());
        if (hilbertSchmidtNorm(calcAminusI(A)).compareTo(one) <= 0) {
            try {
                return denmanBeavers(A, -1);
            } catch (ConvergenceException e) {
                return sqrtPowerSeries(A);
            }
        }
        throw new UnsupportedOperationException("Unable to compute a general square root for the given matrix");
    }

    private static Matrix<Numeric> fast2x2Sqrt(Matrix<? extends Numeric> A) {
        if (A.rows() != 2L || A.columns() != 2L) throw new IllegalArgumentException("Only 2×2 matrices supported");
        final Numeric sqrtDet = A.determinant().sqrt();
        final RealType two = new RealImpl(decTWO, sqrtDet.getMathContext());
        Numeric denom = A.trace().add(two.multiply(sqrtDet)).sqrt();
        // the cast is necessary to make the following expression work, though it boggles the mind that
        // Java generics can't see that a Matrix<Numeric> is a Matrix<? extends Numeric>
        return calcAplusI((Matrix<Numeric>) A).scale(denom.inverse());
    }

    /**
     * Experimental code to compute the square root of a matrix using a power series.
     * This may be impractical due to the use of {@link #generalizedBinomialCoefficient(Numeric, IntegerType)},
     * but testing will reveal all.
     * <br>Note that this method converges slowly (slower than {@link #denmanBeavers(Matrix, int) Denman-Beavers})
     * and appears to require that all matrix eigenvalues are at a distance &le; 1 from the
     * point z&nbsp;=&nbsp;1, which would require computing the eigenvalues of the input matrix to check
     * whether this method would even work.  That's a lot of up-front work to do, and there are methods
     * of computing the square root of a matrix directly from its eigenvalues.
     * @param A the matrix for which we need to find the square root
     * @return the square root of {@code A}
     * @see <a href="https://en.wikipedia.org/wiki/Square_root_of_a_matrix#Power_series">the Wikipedia article</a>
     */
    @Experimental
    public static Matrix<? extends Numeric> sqrtPowerSeries(Matrix<? extends Numeric> A) {
        final MathContext ctx = A.valueAt(0L, 0L).getMathContext();
        final RationalType onehalf = new RationalImpl(BigInteger.ONE, BigInteger.TWO);
        OptionalOperations.setMathContext(onehalf, ctx);
        Matrix<Numeric> result = new ZeroMatrix(A.rows(), ctx);
        final Matrix<Numeric> IminA = calcIminusA((Matrix<Numeric>) A);  // I - A only needs to be computed once

        long bailout = 2L * ctx.getPrecision() + 3L;
        for (long n = 0L; n < bailout; n++) {
            IntegerType nn = new IntegerImpl(BigInteger.valueOf(n)) {
                @Override
                public MathContext getMathContext() {
                    return ctx;
                }
            };
            Matrix<Numeric> intermediate = ((Matrix<Numeric>) IminA.pow(nn)).scale(generalizedBinomialCoefficient(onehalf, nn));
            if (nn.isOdd()) {
                result = result.subtract(intermediate);
            } else {
                result = result.add(intermediate);
            }
        }

        return result;
    }

    private static final int MAX_NORM_GROWTH = 3;

    /**
     * Implementation of the Denman-Beavers iteration for computing the
     * square root of matrix {@code A}.  This method is not guaranteed to
     * converge.
     * @param A the source matrix for which we wish to compute the square root
     * @param iterationLimit the maximum number of iterations to perform, or -1 to let the algorithm decide
     * @return the square root, if the iteration converges
     * @throws ConvergenceException if the iteration does not converge
     * @see <a href="https://en.wikipedia.org/wiki/Square_root_of_a_matrix#By_Denman%E2%80%93Beavers_iteration">the Wikipedia article</a>
     * @see <a href="https://arxiv.org/pdf/1804.11000.pdf">Zolotarev Iterations for the Matrix Square Root</a> by Evan S. Gawlik
     * @apiNote This implementation computes both the square root and its inverse; a reference to the inverse is
     *   included in the returned {@link Matrix} and is accessible using the {@link Matrix#inverse()} method.
     */
    private static Matrix<? extends Numeric> denmanBeavers(Matrix<? extends Numeric> A, int iterationLimit) throws ConvergenceException {
        if (iterationLimit == 0 || iterationLimit < -1) throw new IllegalArgumentException("Iteration limit must be -1 or positive");
        final MathContext ctx = A.valueAt(0L, 0L).getMathContext();
        final RationalType onehalf = new RationalImpl(BigInteger.ONE, BigInteger.TWO, ctx);
        Matrix<Numeric>  Y = (Matrix<Numeric>) A;
        Matrix<Numeric>  Z = new IdentityMatrix(A.rows(), ctx);

        final Logger logger = Logger.getLogger(MathUtils.class.getName());
        final RealType epsilon = computeIntegerExponent(TEN, 3L - ctx.getPrecision(), ctx);
        final int bailout = iterationLimit == -1 ? ctx.getPrecision() * 2 + 5 : iterationLimit;
        logger.log(Level.INFO,
                // surrogate pair for 1D700 epsilon
                "Attempting Denman-Beavers iteration with \uD835\uDF00={0} and a bailout set to {1}.",
                new Object[] { epsilon, bailout });
        int itercount = 0;
        int growthCount = 0;
        RealType prevNorm = hilbertSchmidtNorm(A);

        while (itercount++ < bailout) {
            Matrix<Numeric> Y1 = Y.add((Matrix<Numeric>) Z.inverse()).scale(onehalf);
            Matrix<Numeric> Z1 = Z.add((Matrix<Numeric>) Y.inverse()).scale(onehalf);
            // copy values back for the next iteration
            Y = Y1;
            Z = Z1;
            RealType errAbsolute = Y.multiply(Y).subtract((Matrix<Numeric>) A).norm(); // coarse
            RealType errRelative = hilbertSchmidtNorm(calcAminusI(Y.multiply(Z))); // Y.multiply(Z).subtract(I).norm();
            logger.log(Level.FINE,
                    "After {0} D-B iterations, absolute error = {1}, relative error = {2}",
                    new Object[] {itercount, errAbsolute, errRelative});
            RealType currNorm = hilbertSchmidtNorm(Y);
            if (currNorm.compareTo(prevNorm) > 0) {
                logger.log(Level.WARNING, "At iteration {0}, ||Y|| = {1} (was {2} in previous iteration)",
                        new Object[] { itercount, currNorm, prevNorm });
                if (++growthCount > MAX_NORM_GROWTH) {
                    throw new ConvergenceException("Denman-Beavers iteration appears to diverge", "denmanBeavers", itercount);
                }
            }
            prevNorm = currNorm;
            // check how far off we are and bail if we're ≤ epsilon
            if (min(errRelative, errAbsolute).compareTo(epsilon) <= 0) break;
        }
        // if we don't escape before hitting the bailout, we haven't converged
        if (iterationLimit == -1 && itercount >= bailout) {
            throw new ConvergenceException("Denman-Beavers iteration does not converge to within " + epsilon,
                    "denmanBeavers", bailout);
        }

        // Z converges to the inverse of Y, so let's not waste this result
        final Matrix<? extends Numeric> Yinv = Z;
        return new BasicMatrix<>(Y) {
            @Override
            public Matrix<? extends Numeric> inverse() {
                return Yinv;
            }
        };
    }

    /**
     * Compute the n<sup>th</sup> root of a given matrix.  There may be many roots for
     * a matrix (even for square roots).  This method currently has logic to compute
     * the n<sup>th</sup> root for diagonal and upper-triangular matrices; in the latter
     * case, this method delegates to {@link #parlett(Function, Matrix) Parlett's method}.
     * @param A    the matrix for which to compute the n<sup>th</sup> root
     * @param root an integer value of the order of the root
     * @return the matrix result, A<sup>1/root</sup>
     */
    public static Matrix<? extends Numeric> nthRoot(Matrix<Numeric> A, IntegerType root) {
        if (root.asBigInteger().equals(BigInteger.TWO)) return sqrt(A);
        if (A instanceof DiagonalMatrix) {
            DiagonalMatrix<Numeric> D = (DiagonalMatrix<Numeric>) A;
            RealType[] elements = new RealType[(int) A.columns()];
            try {
                for (long k = 0L; k < A.columns(); k++) {
                    elements[(int) k] = nthRoot((RealType) D.valueAt(k, k).coerceTo(RealType.class), root);
                }
            } catch (CoercionException e) {
                Logger.getLogger(MathUtils.class.getName()).log(Level.SEVERE,
                        "While computing nth root of a diagonal matrix", e);
                throw new ArithmeticException("Error computing " + root + "th root of a diagonal matrix");
            }
            return new DiagonalMatrix<>(elements);
        }
        if (A.isUpperTriangular() && RealType.class.isAssignableFrom(OptionalOperations.findTypeFor(A)) &&
                LongStream.range(0L, A.rows()).mapToObj(idx -> A.valueAt(idx, idx)).filter(Zero::isZero).count() <= 1L) {
            final Function<Numeric, ? extends Numeric> f =
                    x -> nthRoot((RealType) x, root);
            return parlett(f, A);
        }
        throw new UnsupportedOperationException("Cannot compute " + root + "th root of matrix");
    }

    /**
     * This is an implementation of Parlett's method for applying a function &fnof; to
     * an upper-triangular matrix <strong>S</strong> by using recurrence relationships
     * to compute the superdiagonals.
     * @param func a function &fnof; that takes a {@link Numeric} instance and computes a result
     * @param S    an upper-triangular matrix upon which to compute some function &fnof;
     * @return an upper-triangular matrix, the result of computing &fnof;(<strong>S</strong>)
     */
    private static Matrix<? extends Numeric> parlett(Function<Numeric, ? extends Numeric> func, Matrix<? extends Numeric> S) {
        final long n = S.rows();  // S.columns() would work too
        if (n > (long) Integer.MAX_VALUE) throw new ArithmeticException("Cannot allocate a square matrix of size " + n);
        final Class<? extends Numeric> clazz = OptionalOperations.findTypeFor(S);
        final MathContext ctx = S.valueAt(0L, 0L).getMathContext();  // quick and dirty hack
        final Numeric zero = OptionalOperations.dynamicInstantiate(clazz, 0d);
        OptionalOperations.setMathContext(zero, ctx);
        Numeric[][] temp = new Numeric[(int) n][(int) n];
        Arrays.stream(temp, 1, (int) n).forEach(row -> Arrays.fill(row, zero)); // row 0 will be taken care of
        // compute the main diagonal first
        for (int k = 0; k < (int) n; k++) {
            temp[k][k] = func.apply(S.valueAt(k, k));
        }
        // now fill in the superdiagonals, moving upward from the main
        for (int diag = 1; diag < (int) n; diag++) {  // using ints since we're restricting ourselves to arrays here
            for (int j = diag; j < (int) S.columns(); j++) {
                final int i = j - 1;
                Numeric denom = temp[i][i].add(temp[j][j]);
                final int jIdx = j;
                Numeric sum = IntStream.rangeClosed(i + 1, j - 1).mapToObj(k -> temp[i][k].multiply(temp[k][jIdx]))
                        .reduce(ExactZero.getInstance(ctx), Numeric::add);
                Numeric num = S.valueAt(i, j).subtract(sum);
                temp[i][j] = num.divide(denom);
            }
        }
        // and return the result
        return new BasicMatrix<>(temp);
    }

    /**
     * Compute &sigma;<sub>1</sub>(M) of any {@link Matrix} M, which returns the single largest
     * value of M (i.e., the matrix element with the greatest {@link Numeric#magnitude() magnitude}).
     * @param M any {@link Matrix}
     * @return the element of {@code M} with the greatest magnitude
     * @param <T> the numeric type of the elements of {@code M} as well as the return value
     */
    public static <T extends Numeric> T sigma_1(Matrix<T> M) {
        T maxVal = null;

        if (M.getClass().isAnnotationPresent(Columnar.class)) {
            for (long col = 0L; col < M.columns(); col++) {
                ColumnVector<T> column = M.getColumn(col);
                T colMax = column.stream().max((x, y) -> x.magnitude().compareTo(y.magnitude())).orElseThrow();
                if (maxVal == null || colMax.magnitude().compareTo(maxVal.magnitude()) > 0) maxVal = colMax;
            }
        } else {
            for (long rowidx = 0L; rowidx < M.rows(); rowidx++) {
                RowVector<T> row = M.getRow(rowidx);
                T rowMax = row.stream().max((x, y) -> x.magnitude().compareTo(y.magnitude())).orElseThrow();
                if (maxVal == null || rowMax.magnitude().compareTo(maxVal.magnitude()) > 0) maxVal = rowMax;
            }
        }

        return maxVal;
    }

    /**
     * Convert a {@link Matrix<ComplexType>} C into a {@link Matrix<RealType>}.
     * All values in C must be coercible to {@link RealType} or an
     * {@link ArithmeticException} will be thrown.
     * @param C a matrix of {@link ComplexType} values that can be coerced to real
     * @return a real-valued matrix
     * @throws ArithmeticException if any elements of C cannot be coerced to real values
     */
    public static Matrix<RealType> reify(Matrix<ComplexType> C) {
        if (C.getClass().isAnnotationPresent(Columnar.class)) {
            // for more efficient handling of column-based matrices
            ColumnarMatrix<RealType> cresult = new ColumnarMatrix<>();
            for (long column = 0L; column < C.columns(); column++) {
                ColumnVector<ComplexType> orig = C.getColumn(column);
                if (orig.stream().parallel().anyMatch(c -> !c.isCoercibleTo(RealType.class))) {
                    Logger.getLogger(MathUtils.class.getName()).log(Level.SEVERE,
                            "Column {0} of source matrix contains elements that cannot be converted to RealType: {1}",
                            new Object[] {column, orig});
                    throw new ArithmeticException("Source matrix cannot be converted to real, column = " + column);
                }
                ColumnVector<RealType> converted = new ListColumnVector<>();
                orig.stream().map(ComplexType::real).forEachOrdered(converted::append);
                cresult.append(converted);
            }
            return cresult;
        }
        BasicMatrix<RealType> result = new BasicMatrix<>();
        for (long row = 0L; row < C.rows(); row++) {
            RowVector<ComplexType> orig = C.getRow(row);
            if (orig.stream().parallel().anyMatch(c -> !c.isCoercibleTo(RealType.class))) {
                Logger.getLogger(MathUtils.class.getName()).log(Level.SEVERE,
                        "Row {0} of source matrix contains elements that cannot be converted to RealType: {1}",
                        new Object[] {row, orig});
                throw new ArithmeticException("Source matrix cannot be converted to real, row = " + row);
            }
            RowVector<RealType> converted = new ListRowVector<>();
            orig.stream().map(ComplexType::real).forEachOrdered(converted::append);
            result.append(converted);
        }
        return result;
    }

    /**
     * Compute the upper limit of the rank of a matrix.
     * @param M any matrix
     * @return the upper limit of rank(M)
     * @see <a href="https://en.wikipedia.org/wiki/Rank_(linear_algebra)">Wikipedia's article on Rank</a>
     */
    public static long rankUpperLimit(Matrix<? extends Numeric> M) {
        return Math.min(M.rows(), M.columns());
    }

    /**
     * Compute the rank of a matrix.
     * @param M any matrix for which to compute the rank
     * @return the value of rank(M)
     * @see <a href="https://en.wikipedia.org/wiki/Rank_(linear_algebra)">Wikipedia's article on Rank</a>
     */
    public static long rank(Matrix<? extends Numeric> M) {
        Matrix<? extends Numeric> R = toReducedRowEchelonForm(M);
        long rank = 0L;
        for (long rowIdx = 0L; rowIdx < R.rows(); rowIdx++) {
            if (!ZeroVector.isZeroVector(R.getRow(rowIdx))) rank++;
        }
        return rank;
    }

    /**
     * Convert a given matrix to reduced row echelon form.
     * The original {@link Matrix} is not changed, even if it is
     * a mutable subclass.
     * @param M the matrix to be converted
     * @return the converted matrix in reduced row echelon form
     * @see <a href="https://en.wikipedia.org/wiki/Row_echelon_form">the Wikipedia article</a>, which
     *   outlines the basic algorithm
     */
    public static Matrix<? extends Numeric> toReducedRowEchelonForm(Matrix<? extends Numeric> M) {
        long lead = 0L;
        BasicMatrix<Numeric> MM = new BasicMatrix<>((Matrix<Numeric>) M);

        for (long r = 0L; r < M.rows(); r++) {
            if (M.columns() < lead) break;

            long i = r;
            while (Zero.isZero(MM.valueAt(i, lead))) {
                if (M.rows() == ++i) {
                    i = r;
                    if (M.columns() == ++lead) return MM; // exit completely
                }
            }
            if (i != r) MM.exchangeRows(i, r);
            MM.updateRow(r, MM.getRow(r).scale(MM.valueAt(r, lead).inverse()));
            for (long j = 0L; j < M.rows(); j++) {
                if (j == r) continue;
                RowVector<Numeric> jthRow = MM.getRow(j);
                Vector<Numeric> subtrahend = MM.getRow(r).scale(MM.valueAt(j, lead));
                MM.updateRow(j, jthRow.subtract(subtrahend));
            }
            lead++;
        }
        return MM;
    }

    /**
     * Determine if a {@link List} contains linearly independent vectors.
     * @param vectors a list of {@link Vector} objects, which are assumed to be of the same dimension
     * @return true if all vectors are linearly independent, false otherwise
     */
    public static boolean areLinearlyIndependent(List<Vector<? extends Numeric>> vectors) {
        final long veclen = vectors.get(0).length();
        final long numVec = vectors.parallelStream().count();
        if (vectors.parallelStream().map(Vector::length).anyMatch(val -> val != veclen)) {
            throw new IllegalArgumentException("Vectors must be of the same length");
        }
        if (numVec > veclen) return false; // more vectors than dimensions
        if (vectors.parallelStream().anyMatch(ZeroVector::isZeroVector)) return false;

        ColumnarMatrix<Numeric> M = new ColumnarMatrix<>();
        vectors.stream().map(MathUtils::columnVectorFrom).forEach(colVec -> M.append((ColumnVector<Numeric>) colVec));
        if (veclen == numVec) {
            return !Zero.isZero(M.determinant());
        }
        // otherwise, handle the case where veclen > numVec, i.e., more dimensions than vectors
        Logger logger = Logger.getLogger(MathUtils.class.getName());
        logger.log(Level.INFO, "Computing {0} permutations of {1} vectors for a {2}\00D7{1} matrix.",
                new Object[] {nChooseK(veclen, numVec), numVec, veclen});
        List<List<Long>> indexSets = permuteIndices(veclen, numVec);
        for (List<Long> indices : indexSets) {
            if (logger.isLoggable(Level.FINE)) logger.log(Level.FINE, "Checking vectors at rows {0}.", indices);
            BasicMatrix<Numeric> constructed = new BasicMatrix<>();
            indices.stream().map(M::getRow).forEachOrdered(constructed::append);
            if (Zero.isZero(constructed.determinant())) return false;
        }
        return true;
    }

    private static List<List<Long>> permuteIndices(long n, long k) {
        if (k < 0L || k > n) throw new IllegalArgumentException("Bad parameters");
        final long limit = 1L << n;  // 2^n
        return LongStream.range(1L, limit).mapToObj(BigInteger::valueOf).filter(b -> b.bitCount() == k)
                .map(MathUtils::expandIndices).collect(Collectors.toList());
    }

    private static List<Long> expandIndices(BigInteger bits) {
        final int size = bits.bitLength();
        final int idxCount = bits.bitCount();
        List<Long> result = idxCount <= 10 ? new ArrayList<>(idxCount) : new LinkedList<>();
        for (int k = bits.getLowestSetBit(); k < size; k++) {
            if (bits.testBit(k)) result.add((long) k);
        }
        return result;
    }

    /**
     * Select one possible permutation of k indices chosen from n.
     * The selection is pseudo-random and assumes that one sampling
     * of k indices is just as good as any other.
     * @param n the total number of indices to select from
     * @param k the number of indices to select
     * @return a {@link List<Long>} of {@code k} indices chosen at random
     */
    public static List<Long> randomIndexPermutation(long n, long k) {
        final Random rand = new Random();
        List<List<Long>> allPermutations = permuteIndices(n, k);
        int index = rand.nextInt(allPermutations.size());
        // Note that this will not select from every possible permutation
        // if the length of allPermutations > Integer.MAX_VALUE
        return allPermutations.get(index);
    }

    /**
     * Determine if a given matrix is normal.
     * A matrix is considered normal if it commutes with its conjugate transpose.
     * In other words, A<sup>*</sup>A = AA<sup>*</sup>.
     * @param matrix the {@link Matrix} to test whether it is normal
     * @return true if the supplied {@link Matrix} is normal, false otherwise
     */
    public static boolean isNormal(Matrix<? extends Numeric> matrix) {
        if (matrix.rows() != matrix.columns()) return false;  // non-square matrices are non-normal
        Matrix<ComplexType> conjXpose = conjugateTranspose(matrix);
        Matrix<ComplexType> inputAsCplx = new ParametricMatrix<>(matrix.rows(), matrix.columns(), (row, column) -> {
            try {
                return (ComplexType) matrix.valueAt(row, column).coerceTo(ComplexType.class);
            } catch (CoercionException e) {
                throw new ArithmeticException("Unable to coerce element at " + row + ", " + column);
            }
        });
        // IntelliJ (and probably other IDEs) will complain that equals() is between objects of
        // inconvertible types, but this is some high-grade horseshit owing to the way Java
        // implements generic type support.  Matrix<ComplexType> is not considered a subtype
        // of Matrix<Numeric> (the supertype of ZeroMatrix).  Yet the equals() method of ZeroMatrix
        // delegates to ParametricMatrix.equals(), which tests two matrices for equality based upon
        // element-wise comparison using Numeric.equals()...
        // Ultimately, it's just easier to create a method ZeroMatrix.isZeroMatrix() which tests
        // the cell values for equality to zero.
        return ZeroMatrix.isZeroMatrix(inputAsCplx.multiply(conjXpose).subtract(conjXpose.multiply(inputAsCplx)));
    }

    /**
     * Determine if a complex matrix is Hermitian (that is, equal to its own conjugate transpose).
     * @param cplxMatrix the complex matrix to test
     * @return true if the given matrix is Hermitian, false otherwise
     */
    public static boolean isHermitian(Matrix<ComplexType> cplxMatrix) {
        return cplxMatrix.equals(conjugateTranspose(cplxMatrix));
    }

    /**
     * Determine if a matrix is orthogonal. That is, the columns and rows
     * of the matrix are orthonormal vectors.
     * @param M the matrix to test for orthogonality
     * @return true if the matrix is orthogonal, false otherwise
     */
    public static boolean isOrthogonal(Matrix<RealType> M) {
        if (M.rows() != M.columns()) return false;  // must be a square matrix
        try {
            return M.transpose().equals(M.inverse());
        } catch (ArithmeticException e) {
            // it makes more sense to log the event and return false
            // this is less costly than pre-checking if the matrix is singular
            Logger.getLogger(MathUtils.class.getName()).log(Level.FINE,
                    "While computing matrix inverse for comparison to transpose.", e);
            return false;
        }
    }

    /**
     * Determine if a matrix is unitary.  This is the complex equivalent
     * to orthogonality.
     * @param C the matrix to test
     * @return true if the matrix is unitary, false otherwise
     * @see #isOrthogonal(Matrix)
     */
    public static boolean isUnitary(Matrix<ComplexType> C) {
        if (C.rows() != C.columns()) return false;  // must be square
        try {
            return conjugateTranspose(C).equals(C.inverse());
        } catch (ArithmeticException e) {
            // it makes more sense to log the event and return false
            // this is less costly than pre-checking if the matrix is singular
            Logger.getLogger(MathUtils.class.getName()).log(Level.FINE,
                    "While computing matrix inverse for comparison to conjugate transpose.", e);
            return false;
        }
    }

    /**
     * Determine if a {@link Matrix} has only elements of a given type.
     * This method will inspect as many elements of the matrix as
     * necessary to ensure that all elements are of the specified type.
     * @param matrix any matrix
     * @param clazz  the {@link Class} to be used for testing elements of {@code matrix}
     * @return true if all elements of {@code matrix} are of type {@code clazz}
     *   or one of its subtypes
     */
    public static boolean isOfType(Matrix<? extends Numeric> matrix, Class<? extends Numeric> clazz) {
        if (matrix instanceof SingletonMatrix || (matrix.rows() == 1L && matrix.columns() == 1L)) {
            return clazz.isAssignableFrom(matrix.valueAt(0L, 0L).getClass());
        }
        if (matrix instanceof DiagonalMatrix) {
            // To guard against a heterogeneous matrix causing problems, we must check
            // one off-diagonal cell.
            return LongStream.range(0L, matrix.rows()).mapToObj(idx -> matrix.valueAt(idx, idx))
                    .map(Object::getClass).allMatch(clazz::isAssignableFrom) &&
                    clazz.isAssignableFrom(matrix.valueAt(0L, matrix.columns() - 1L).getClass());
        }
        if (matrix.getClass().isAnnotationPresent(Columnar.class)) {
            return LongStream.range(0L, matrix.columns()).mapToObj(matrix::getColumn).flatMap(ColumnVector::stream)
                    .map(Object::getClass).allMatch(clazz::isAssignableFrom);
        }
        return LongStream.range(0L, matrix.rows()).mapToObj(matrix::getRow).flatMap(RowVector::stream)
                .map(Object::getClass).allMatch(clazz::isAssignableFrom);
    }

    /**
     * Compute the eigenvalues for the given matrix. The returned {@link Set}
     * may be heterogeneous (i.e., {@link Set<Numeric>} which can contain
     * any subclass of {@link Numeric}).<br>
     * <strong>Note:</strong> This method is currently only guaranteed to
     * produce results for triangular matrices and 2&times;2 matrices.
     * Symmetric 3&times;3 matrices are also supported. There is very
     * limited support for block-diagonal matrices &mdash;
     * those implemented using {@link AggregateMatrix}, specifically.
     * If all else fails, this method will attempt to determine eigenvalues
     * using the QR algorithm, which repeatedly applies {@link #computeQRdecomposition(Matrix) QR decomposition}
     * and thus is costly.
     * @param M the {@link Matrix} for which we wish to obtain the eigenvalues
     * @return a {@link Set} of eigenvalues
     * @see <a href="https://en.wikipedia.org/wiki/QR_algorithm">the Wikipedia article on the QR algorithm</a>
     */
    public static Set<? extends Numeric> eigenvaluesOf(Matrix<? extends Numeric> M) {
        if (M.rows() != M.columns()) throw new IllegalArgumentException("Cannot compute eigenvalues for a non-square matrix");
        if (M.isTriangular()) {
            // the values on the diagonal are the eigenvalues
            NumericSet diagonalElements = new NumericSet();
            LongStream.range(0L, M.rows()).mapToObj(idx -> M.valueAt(idx, idx)).forEach(diagonalElements::append);
            return diagonalElements;
        }
        // if M is block-diagonal, then the eigenvalues of M are the eigenvalues of all submatrices on the diagonal
        if (M instanceof AggregateMatrix) {
            AggregateMatrix<? extends Numeric> blockMatrix = (AggregateMatrix<? extends Numeric>) M;
            if (blockMatrix.subMatrixRows() != blockMatrix.subMatrixColumns()) {
                Logger.getLogger(MathUtils.class.getName()).log(Level.WARNING,
                        "Block matrix is {0}×{1} (detected as square) but the tiles are laid out {2}×{3} (non-square).",
                        new Object[] { blockMatrix.rows(), blockMatrix.columns(),
                                blockMatrix.subMatrixRows(), blockMatrix.subMatrixColumns() });
            }
            if (isBlockDiagonal(blockMatrix)) {
                NumericSet allEigenvalues = new NumericSet();
                for (int idx = 0; idx < blockMatrix.subMatrixRows(); idx++) {
                    Set<? extends Numeric> subMatrixEigenvalues = eigenvaluesOf(blockMatrix.getSubMatrix(idx, idx));
                    StreamSupport.stream(subMatrixEigenvalues.spliterator(), false).forEach(allEigenvalues::append);
                }
                return allEigenvalues;
            }
        }
        if (M.rows() == 2L) {
            // 2×2 matrices are trivial to compute the eigenvalues of
            return computeEigenvaluesFor2x2(M);
        }
        if (M.rows() == 3L && isSymmetric(M)) {
            return computeEigenvaluesFor3x3Symmetric(M);
        }
        // let's try QR decomposition
        List<Matrix<Numeric>> decomp = computeQRdecomposition((Matrix<Numeric>) M);
        Matrix<Numeric> Q = decomp.get(0);
        Matrix<Numeric> R = decomp.get(1);
        // pull the MathContext from the last column, which should serve as a proxy for the whole matrix
        MathContext ctx = M.getColumn(M.columns() - 1L).getMathContext();
        RealType epsilon = computeIntegerExponent(TEN, 1 - ctx.getPrecision(), ctx);
        if (isUpperTriangularWithin(R, epsilon)) {
            Matrix<Numeric> A;
            do {
                A = R.multiply(Q);
                // the lower (left) triangle might not be filled with exactly zeroes, so check within some tolerance epsilon
                if (isUpperTriangularWithin(A, epsilon)) break;
                decomp = computeQRdecomposition(A);
                Q = decomp.get(0);
                R = decomp.get(1);
            } while (!isUpperTriangularWithin(A, epsilon));
            // pick off the eigenvalues from the diagonal
            NumericSet results = new NumericSet();
            for (long idx = 0L; idx < A.rows(); idx++) {
                results.append(A.valueAt(idx, idx));
            }
            return results;
        } else {
            Logger.getLogger(MathUtils.class.getName()).log(Level.INFO,
                    "QR decomposition failed. R is not upper-triangular to within {1}:\n{0}",
                    new Object[] {R, epsilon});
        }

        // we can't handle this type of matrix yet
        throw new UnsupportedOperationException("Cannot compute eigenvalues for square matrix of size " + M.rows());
    }

    /**
     * Determine if a given matrix is upper-triangular within some tolerance.
     * More formally, determines if all the matrix elements in the lower (left) triangle
     * are &lt;&thinsp;&epsilon; for some value &#x1D700; that satisfies the
     * inequality 0 &lt; &#x1D700; &#x226A; 1.
     * @param M       the matrix to test for upper-triangularity
     * @param epsilon the tolerance value &epsilon; denoting the maximum acceptable error
     * @return true if the given matrix satisfies the error-tolerance criteria for upper-triangularity,
     *   false otherwise
     */
    public static boolean isUpperTriangularWithin(Matrix<? extends Numeric> M, RealType epsilon) {
        if (!epsilonRange.contains(epsilon)) throw new IllegalArgumentException("Tolerance should be in range 0 < \uD835\uDF00 \u226A 1");
        if (M.columns() != M.rows()) return false;
        if (M.rows() == 1L) return false;  // singleton matrix can't really be triangular

        for (long row = 1L; row < M.rows(); row++) {
            for (long column = 0L; column < M.columns() - (M.rows() - row); column++) {
                try {
                    if (((RealType) M.valueAt(row, column).magnitude().coerceTo(RealType.class)).compareTo(epsilon) >= 0) {
                        return false;
                    }
                } catch (CoercionException e) {
                    Logger.getLogger(MathUtils.class.getName()).log(Level.WARNING,
                            "Failed to coerce the magnitude of the matrix element at {0},{1} to a real value.",
                            new Object[] {row, column});
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Given a matrix <strong>M</strong> and a set of eigenvalues, computes the eigenvectors.
     * @param M           a square matrix
     * @param eigenvalues a set of one or more eigenvalues of <strong>M</strong>, though
     *                    this set need not be comprehensive
     * @return a {@link Map} of eigenvalues and their corresponding eigenvectors
     * @param <T> the type of the elements of {@code M} and its eigenvalues
     */
    public static <T extends Numeric> Map<T, Vector<T>> eigenvectorsOf(Matrix<T> M, Set<T> eigenvalues) {
        if (eigenvalues.cardinality() <= 0L) throw new IllegalArgumentException("No eigenvalues to solve for");
        Map<T, Vector<T>> results = new HashMap<>((int) eigenvalues.cardinality());

        try {
            for (T value : eigenvalues) {
                final Class<T> clazz = (Class<T>) value.getClass();
                T zero = (T) ExactZero.getInstance(value.getMathContext()).coerceTo(clazz);
                Vector<T> zeroVector = columnVectorFrom(ZeroVector.getInstance(M.rows(), value.getMathContext()), clazz);
                Matrix<T> lambdaMatrix = new ParametricMatrix<>(M.rows(), M.columns(), (row, column) -> {
                    if (row.longValue() == column.longValue()) return value;
                    return zero;
                });
                results.put(value, triangularizeAndSolve(M.subtract(lambdaMatrix), zeroVector));
            }
        } catch (CoercionException e) {
            throw new IllegalStateException("While obtaining a zero instance and converting it", e);
        }
        return results;
    }

    /**
     * Determines whether an {@link AggregateMatrix} is block diagonal.
     * @param blockMatrix a block matrix
     * @return true if the supplied matrix is block diagonal
     */
    public static boolean isBlockDiagonal(AggregateMatrix<? extends Numeric> blockMatrix) {
        for (int blockRow = 0; blockRow < blockMatrix.subMatrixRows(); blockRow++) {
            for (int blockCol = 0; blockCol < blockMatrix.subMatrixColumns(); blockCol++) {
                if (blockRow == blockCol) continue;
                if (!ZeroMatrix.isZeroMatrix(blockMatrix.getSubMatrix(blockRow, blockCol))) return false;
            }
        }
        return true;
    }

    /**
     * Determines whether a matrix is symmetric around the diagonal.
     * @param matrix the matrix to be checked for symmetry
     * @return true if the supplied matrix is symmetric, false otherwise
     */
    public static boolean isSymmetric(Matrix<? extends Numeric> matrix) {
        if (matrix instanceof DiagonalMatrix) return true;
        if (matrix.rows() != matrix.columns()) return false;
        if (matrix.rows() == 1L) return true; // a singleton matrix is always symmetric
        for (long row = 0L; row < matrix.rows() - 1L; row++) {
            for (long column = row + 1L; column < matrix.columns(); column++) {
                if (!matrix.valueAt(row, column).equals(matrix.valueAt(column, row))) return false;
            }
        }
        return true;
    }

    /**
     * Decompose an augmented matrix [<strong>A</strong>|b&#x20d7;] into
     * <strong>A</strong> and column vector b&#x20d7;.
     * @param augmented the augmented matrix to decompose
     * @return a {@link List} containing a matrix and a column vector, in that order
     * @param <T> the type of the elements of {@code augmented}
     */
    public static <T extends Numeric> List<Matrix<T>> splitAugmentedMatrix(Matrix<T> augmented) {
        final long lastColumn = augmented.columns() - 1L;
        ColumnVector<T> rhs = augmented.getColumn(lastColumn);
        Matrix<T> lhs;
        if (augmented instanceof BasicMatrix) {
            lhs = ((BasicMatrix<T>) augmented).removeColumn(lastColumn);
        } else if (augmented instanceof ColumnarMatrix) {
            lhs = ((ColumnarMatrix<T>) augmented).removeColumn(lastColumn);
        } else {
            lhs = new SubMatrix<>(augmented, 0L, 0L, augmented.rows() - 1L, lastColumn - 1L);
        }
        assert lhs.rows() == lhs.columns();  // lhs should be a square matrix
        return List.of(lhs, rhs);
    }

    /**
     * Given matrix <strong>A</strong> and vector b&#x20d7;, perform Gaussian elimination
     * and then solve for x&#x20d7; in the equation <strong>A</strong>x&#x20d7;&nbsp;=&nbsp;b&#x20d7;.
     * @param A a square matrix
     * @param b a vector of values with a length corresponding to {@code A.rows()}
     * @return the solution to the equation <strong>A</strong>x&#x20d7;&nbsp;=&nbsp;b&#x20d7;
     * @param <T> the type of the elements of {@code A} and {@code b}
     */
    public static <T extends Numeric> Vector<T> triangularizeAndSolve(Matrix<T> A, Vector<T> b) {
        if (A.rows() != A.columns()) throw new IllegalArgumentException("A must be square");
        if (b.length() != A.rows()) throw new IllegalArgumentException("Length of b must equal rows of A");
        final long n = A.rows();

        Matrix<T> U = A;
        ColumnVector<T> c = b instanceof ColumnVector ? (ColumnVector<T>) b : new ArrayColumnVector<>(b);
        for (long j = 0L; j < n - 1L; j++) {
            Matrix<T> intermediate = gaussianElimination(U, c, j);
            List<Matrix<T>> parts = splitAugmentedMatrix(intermediate);
            U = parts.get(0);
            c = (ColumnVector<T>) parts.get(1);
            // we could put a bailout condition here if the matrix U is already upper triangular
            // if (U.isUpperTriangular()) break;
            // but that might cost us more than just continuing the iteration... needs more thought
        }
        return backSubstitution(U, c);
    }

    /**
     * Perform Gaussian elimination on a matrix <strong>A</strong> and vector b&#x20d7;.
     * The result is a reduced matrix <strong>U</strong> and associated vector c&#x20d7;
     * which, after the final pivot, is in upper-triangular form, suitable for backsolving in the form
     * <strong>U</strong>x&#x20d7;&nbsp;=&nbsp;c&#x20d7;.
     *
     * @param A the n&times;n {@link Matrix} we wish to reduce
     * @param b the associated {@link Vector}
     * @param j the row on which to pivot
     * @return an augmented n&thinsp;&times;&thinsp;n+1 matrix containing a reduced matrix and its
     *  associated vector in the form [<strong>U</strong>|c&#x20d7;]
     * @param <T> the numeric type of the elements of {@code A} and {@code b}
     */
    public static <T extends Numeric> Matrix<T> gaussianElimination(Matrix<T> A, Vector<T> b, long j) {
        if (j < 0L || j >= A.rows()) throw new IndexOutOfBoundsException("Index j must be between 0 and " + A.rows());
        if (A.columns() != A.rows()) throw new IllegalArgumentException("Matrix is non-square");
        if (A.rows() != b.length()) throw new IllegalArgumentException("Vector length must match rows of matrix");
        BasicMatrix<T> U = new BasicMatrix<>(A);
        // for convenience, we attach b to A and create an augmented matrix
        U.append(new ArrayColumnVector<>(b));
        final long n = A.rows();

        if (Zero.isZero(U.valueAt(j, j))) {
            Comparator<Numeric> comp = obtainGenericComparator();
            Numeric biggestValue = ExactZero.getInstance(b.getMathContext());
            long kRow = j;

            for (long k = j + 1L; k < n; k++) {
                if (comp.compare(U.valueAt(k, j).magnitude(), biggestValue) > 0) {
                    biggestValue = U.valueAt(k, j).magnitude();
                    kRow = k;
                }
            }
            // swap rows j and kRow
            // Note: original algorithm only bothers exchanging elements starting at column j
            U.exchangeRows(j, kRow);  // this also effectively swaps elements of the b vector
        }
        T pivot = U.valueAt(j, j);
        if (Zero.isZero(pivot)) {
            throw new ArithmeticException("Matrix A is singular");
        }
        // reduce the rows
        for (long i = j + 1L; i < n; i++) {
            T multiplier = (T) U.valueAt(i, j).divide(pivot);
            for (long l = j; l < n + 1L; l++) { // ensure we include the column corresponding to c
                T updValue = (T) U.valueAt(i, l).subtract(U.valueAt(j, l).multiply(multiplier));
                U.setValueAt(updValue, i, l);
            }
        }

        return U;
    }

    /**
     * Perform back substitution to solve <strong>U</strong>x&#x20d7;&nbsp;=&nbsp;c&#x20d7; for x&#x20d7;,
     * where <strong>U</strong> is an upper-triangular matrix and c&#x20d7; is a vector.
     *
     * @param U a {@link Matrix} in upper-triangular form, that is, {@code U.isUpperTriangular()} returns {@code true}
     * @param c a {@link Vector} of values which must have the same number of elements as U has rows
     * @return the solution vector
     * @param <T> the type of U and the result
     */
    public static <T extends Numeric> Vector<T> backSubstitution(Matrix<T> U, Vector<? super T> c) {
        if (U.rows() != c.length()) throw new IllegalArgumentException("Matrix U must have the same number of rows as elements in Vector c");
        if (U.rows() != U.columns() || !U.isUpperTriangular()) throw new IllegalArgumentException("Matrix U must be upper-triangular and square");
        final Class<T> clazz = U.getRow(0L).getElementType(); // U is upper-triangular, so row 0 should consist of all non-zero elements
        NumericHierarchy h = NumericHierarchy.forNumericType(clazz);
        Vector<T> x;
        switch (h) {
            case COMPLEX:
                x = (Vector<T>) new ComplexVector(c.length());
                break;
            case REAL:
                x = (Vector<T>) new RealVector(c.length());
                break;
            default:
                throw new UnsupportedOperationException("No vector type defined for " + clazz.getTypeName());
        }
        final long n = U.rows();  // also U.columns() would work since U is an upper-triangular square matrix

        for (long i = n - 1L; i >= 0L; i--) {
            Numeric sum = ExactZero.getInstance(c.getMathContext());
            for (long j = i + 1L; j < n; j++) {
                sum = sum.add(x.elementAt(j).multiply(U.valueAt(i, j)));
            }
            try {
                T value = (T) c.elementAt(i).subtract(sum).multiply(U.valueAt(i, i).inverse()).coerceTo(clazz);
                x.setElementAt(value, i);
            } catch (CoercionException fatal) {
                Logger.getLogger(MathUtils.class.getName()).log(Level.SEVERE,
                        "While computing the " + i + "th element of x for Ux = c, c = " + c, fatal);
                throw new ArithmeticException("Coercion error while computing " + i + "th element of solution vector");
            }
        }

        return x;
    }

    private static <T extends Numeric> ColumnVector<T> columnVectorFrom(Vector<? super T> source, Class<T> clazz) {
        final long THRESHOLD = 1_000L;
        List<T> converted = new LinkedList<>();
        try {
            for (long index = 0L; index < source.length(); index++) {
                T value = (T) source.elementAt(index).coerceTo(clazz);
                converted.add(value);
            }
        } catch (CoercionException e) {
            throw new IllegalStateException("While converting " + source + " to a column vector", e);
        }
        if (source.length() > THRESHOLD) return new ListColumnVector<>(converted);
        return new ArrayColumnVector<>(converted);
    }

    private static ColumnVector<? extends Numeric> columnVectorFrom(Vector<? extends Numeric> source) {
        final long THRESHOLD = 1_000L;
        List<Numeric> temp = new LinkedList<>();
        LongStream.range(0L, source.length()).mapToObj(source::elementAt).forEach(temp::add);
        if (source.length() > THRESHOLD) return new ListColumnVector<>(temp);
        return new ArrayColumnVector<>(temp);
    }

    private static Set<Numeric> computeEigenvaluesFor2x2(Matrix<? extends Numeric> matrix) {
        Numeric diagSum = matrix.valueAt(0L, 0L).add(matrix.valueAt(1L, 1L));
        Numeric diagDiff = matrix.valueAt(0L, 0L).subtract(matrix.valueAt(1L, 1L));
        final MathContext ctx = inferMathContext(List.of(diagSum, diagDiff, matrix.valueAt(0L, 1L)));
        final IntegerType four = new IntegerImpl(BigInteger.valueOf(4L)) {
            @Override
            public MathContext getMathContext() {
                return ctx;
            }
        };
        Numeric term2 = diagDiff.multiply(diagDiff)
                .add(four.multiply(matrix.valueAt(0L, 1L)).multiply(matrix.valueAt(1L, 0L)))
                .sqrt();
        final IntegerType two = new IntegerImpl(BigInteger.TWO) {
            @Override
            public MathContext getMathContext() {
                return ctx;
            }
        };
        return Set.of(diagSum.add(term2).divide(two), diagSum.subtract(term2).divide(two));
    }

    private static Set<ComplexType> computeEigenvaluesFor3x3Symmetric(Matrix<? extends Numeric> matrix) {
        final MathContext ctx = matrix.valueAt(0L, 0L).getMathContext();
        final RealType two = new RealImpl(decTWO, ctx);
        final RealType three = new RealImpl(BigDecimal.valueOf(3L), ctx);
        final RealType six = new RealImpl(BigDecimal.valueOf(6L), ctx);
        final RealType one = new RealImpl(BigDecimal.ONE, ctx);
        final RealType negone = new RealImpl(BigDecimal.valueOf(-1L), ctx);
        final RealType pi = Pi.getInstance(ctx);
        try {
            ComplexType triangleSq = (ComplexType) matrix.valueAt(0L, 1L).multiply(matrix.valueAt(0L, 1L))
                    .add(matrix.valueAt(0L, 2L).multiply(matrix.valueAt(0L, 2L)))
                    .add(matrix.valueAt(1L, 2L).multiply(matrix.valueAt(1L, 2L))).coerceTo(ComplexType.class);
            ComplexType q = (ComplexType) matrix.trace().divide(three).coerceTo(ComplexType.class);
            ComplexType intermediate = (ComplexType) LongStream.range(0L, matrix.rows()).mapToObj(idx -> matrix.valueAt(idx, idx))
                    .map(z -> z.multiply(z)).reduce(triangleSq.multiply(two), Numeric::add).coerceTo(ComplexType.class);
            ComplexType p = (ComplexType) intermediate.divide(six).sqrt();
            Matrix<ComplexType> A = new ComplexMatrixAdapter(matrix);
            Matrix<ComplexType> B = A.subtract(lambdaMatrix(3L, q));
            ComplexType r = (ComplexType) B.determinant().divide(two);
            ComplexType phi;
            if (r.isCoercibleTo(RealType.class) && isOfType(matrix, RealType.class)) {
                // if the matrix is real, ensure we keep the value of phi within bounds
                RealType reR = r.real();
                if (reR.compareTo(negone) <= 0) phi = new ComplexPolarImpl((RealType) pi.divide(three));
                else if (reR.compareTo(one) >= 0) phi = new ComplexRectImpl(new RealImpl(BigDecimal.ZERO, ctx));
                else phi = (ComplexType) arccos(reR).divide(three).coerceTo(ComplexType.class);
            } else {
                phi = (ComplexType) arccos(r).divide(three);
            }
            ComplexType eig1 = (ComplexType) q.add(p.multiply(cos(phi)).multiply(two));
            ComplexType nextAngle = (ComplexType) phi.add(two.multiply(pi).divide(three));
            ComplexType eig2 = (ComplexType) q.add(p.multiply(cos(nextAngle)).multiply(two));
            // since A.trace() = eig1 + eig2 + eig3, we can solve for eig3
            ComplexType eig3 = (ComplexType) A.trace().subtract(eig1).subtract(eig2);
            return Set.of(eig1, eig2, eig3);
        } catch (CoercionException e) {
            throw new ArithmeticException("While computing eigenvalues: " + e.getMessage());
        }
    }

    private static Matrix<ComplexType> lambdaMatrix(long dimension, ComplexType lambda) {
        final ComplexType zero = new ComplexRectImpl(new RealImpl(BigDecimal.ZERO, lambda.getMathContext()));
        return new ParametricMatrix<>(dimension, dimension, (row, column) -> {
            if (row.longValue() == column.longValue()) return lambda;
            return zero;
        });
    }

    /**
     * Decompose a matrix A into Q and R such that A&nbsp;=&nbsp;QR,
     * where Q is an orthogonal matrix and R is an upper-triangular matrix.
     * This implementation uses the Gram-Schmidt process.
     * @param A the matrix to be decomposed
     * @return a {@link List} containing Q and R, in that order
     * @param <T> the type of the elements of {@code A}
     * @see <a href="https://en.wikipedia.org/wiki/QR_decomposition">the Wikipedia article on QR decomposition</a>
     */
    public static <T extends Numeric> List<Matrix<T>> computeQRdecomposition(Matrix<T> A) {
        ColumnarMatrix<T> Q = new ColumnarMatrix<>();
        for (long col = 0L; col < A.columns(); col++) {
            ColumnVector<T> cvec = new ArrayColumnVector<>(A.getColumn(col).normalize());
            Q.append(cvec);
        }
        Matrix<T> R = isOfType(A, ComplexType.class) ? (Matrix<T>) conjugateTranspose(Q).multiply((Matrix<ComplexType>) A) : Q.transpose().multiply(A);
        return List.of(Q, R);
    }

    /**
     * Decompose a matrix A into a lower-triangular matrix L
     * and an upper-triangular matrix U such that A&nbsp;=&nbsp;LU
     * via the process of compact elimination.  No pivots are
     * used in this algorithm, and there are no checks for singularity.
     * @param A the matrix to decompose
     * @return a {@link List} containing L and U, in that order
     * @param <T> the type of the elements of A
     * @see <a href="https://www.sciencedirect.com/book/9780125535601/theory-and-applications-of-numerical-analysis">Theory
     *   and Applications of Numerical Analysis</a>
     */
    public static <T extends Numeric> List<Matrix<T>> compactLUdecomposition(Matrix<T> A) {
        if (A.rows() != A.columns()) throw new IllegalArgumentException("Matrix must be square");
        final Class<T> clazz = (Class<T>) OptionalOperations.findTypeFor(A);
        final long n = A.rows();
        final T one, zero;
        try {
            MathContext ctx = A.getRow(0L).getMathContext();
            one = (T) One.getInstance(ctx).coerceTo(clazz);
            zero = (T) ExactZero.getInstance(ctx).coerceTo(clazz);
        } catch (CoercionException e) {
            throw new IllegalStateException("Could not obtain unity or zero for " + clazz.getTypeName(), e);
        }
        BasicMatrix<T> U = new BasicMatrix<>() {
            @Override
            public boolean isUpperTriangular() {
                return true;  // we know the result is going to be upper triangular
            }
        };
        U.append(A.getRow(0L));
        ColumnarMatrix<T> L = new ColumnarMatrix<>() {
            @Override
            public boolean isLowerTriangular() {
                return true;  // we know the result is going to be lower triangular
            }
        };
        L.append(A.getColumn(0L).scale((T) U.valueAt(0L, 0L).inverse()));
        for (long i = 1L; i < n; i++) {
            RowVector<T> row = new ArrayRowVector<>(clazz, n);
            // initialize the row vector to 0
            for (long j = 0L; j < n; j++) row.setElementAt(zero, j);
            for (long k = i; k < n; k++) {
                RowVector<T> reducedL = L.getRow(i).trimTo(i);
                ColumnVector<T> reducedU = U.getColumn(k).trimTo(i);
                row.setElementAt((T) A.valueAt(i, k).subtract(reducedL.dotProduct(reducedU)), k);
            }
            U.append(row);
            ColumnVector<T> col = new ArrayColumnVector<>(clazz, n);
            // initialize the column vector to 0 except for the ith element
            for (long j = 0L; j < n; j++) col.setElementAt(j == i ? one : zero, j);
            if (i + 1L  < n) {
                for (long k = i + 1L; k < n; k++) {
                    RowVector<T> reducedL = L.getRow(k).trimTo(i);
                    ColumnVector<T> reducedU = U.getColumn(i).trimTo(i);
                    col.setElementAt((T) A.valueAt(k, i).subtract(reducedL.dotProduct(reducedU)).divide(U.valueAt(i, i)), k);
                }
            }
            L.append(col);
        }

        return List.of(L, U);
    }

    /**
     * Perform a LDU decomposition of a 2&times;2 block matrix.  This method will compute
     * the decomposition and return it in a {@link List<Matrix>}.  Note that to obtain a
     * conventional LU decomposition, it is necessary to multiply the D and U factors, giving
     * [L, DU].
     * @param blockMatrix a block matrix consisting of 2&times;2 submatrices (currently the only supported
     *                    configuration)
     * @return a {@link List} of block matrices consisting of a lower-triangular (L), block diagonal (D),
     *   and upper-triangular (U) matrix, in that order
     * @param <T> the numeric type of the elements of {@code blockMatrix}
     * @see <a href="https://www.math.ucdavis.edu/~linear/old/notes11.pdf">linear algebra notes from UC Davis</a>
     */
    public static <T extends Numeric> List<Matrix<? super T>> blockLDUdecomposition(AggregateMatrix<T> blockMatrix) {
        if (blockMatrix.subMatrixRows() != 2 || blockMatrix.subMatrixColumns() != 2) {
            throw new ArithmeticException("LDU decomposition cannot be performed for non-2\u00D72 block matrices");
        }
        final MathContext ctx = blockMatrix.getRow(0L).getMathContext();
        Matrix<T> X = blockMatrix.getSubMatrix(0, 0);
        Matrix<Numeric> Y = (Matrix<Numeric>) blockMatrix.getSubMatrix(0, 1);
        Matrix<Numeric> Z = (Matrix<Numeric>) blockMatrix.getSubMatrix(1, 0);
        Matrix<Numeric> W = (Matrix<Numeric>) blockMatrix.getSubMatrix(1, 1);
        IdentityMatrix  I = new IdentityMatrix(X.rows(), ctx);
        ZeroMatrix     z0 = new ZeroMatrix(X.rows(), ctx);
        Matrix<Numeric> Xinv   = (Matrix<Numeric>) X.inverse();
        Matrix<Numeric>[][] ll = new Matrix[][] { {I, z0}, {Z.multiply(Xinv), I} };
        AggregateMatrix<Numeric> L = new AggregateMatrix<>(ll);
        Matrix<Numeric> XinvY  = Xinv.multiply(Y);
        Matrix<Numeric>[][] uu = new Matrix[][] { {I, XinvY}, {z0, I} };
        AggregateMatrix<Numeric> U = new AggregateMatrix<>(uu);
        Matrix<Numeric>[] dd = new Matrix[] { X, W.subtract(Z.multiply(XinvY)) };
        AggregateMatrix<Numeric> D = AggregateMatrix.blockDiagonal(dd); // block diagonal matrix D of LDU

        return List.of(L, D, U);
    }

    /**
     * Given a matrix <strong>A</strong>, calculate <strong>A</strong>&minus;<strong>I</strong>,
     * where <strong>I</strong> is the identity matrix.  This is far more efficient than
     * instantiating an identity matrix and doing regular subtraction.  The value
     * <strong>A</strong>&minus;<strong>I</strong> is used in some routine calculations, e.g.,
     * computing the logarithm of a matrix.
     * @param A the matrix
     * @return the result A&minus;I
     * @param <T> the element type of matrix A
     * @since 0.4
     */
    public static <T extends Numeric> Matrix<T> calcAminusI(Matrix<T> A) {
        if (A.rows() != A.columns()) throw new IllegalArgumentException("Matrix A must be square");

        BasicMatrix<T> result = new BasicMatrix<>(A);
        Class<T> clazz = (Class<T>) OptionalOperations.findTypeFor(A);
        final Numeric one = One.getInstance(A.getClass().isAnnotationPresent(Columnar.class) ?
                A.getColumn(0L).getMathContext() : A.getRow(0L).getMathContext());

        try {
            for (long idx = 0L; idx < A.rows(); idx++) {
                T element = (T) A.valueAt(idx, idx).subtract(one).coerceTo(clazz);
                result.setValueAt(element, idx, idx);
            }
        } catch (CoercionException e) {
            throw new ArithmeticException("While computing A\u2212I: " + e.getMessage());
        }

        return result;
    }

    /**
     * Given the matrix <strong>A</strong>, calculate <strong>A</strong>+<strong>I</strong>,
     * where I is the identity matrix.  This is much faster and more efficient than
     * instantiating an identity matrix for this calculation.
     * @param A the matrix
     * @return the result A+I
     * @param <T> the element type of matrix A
     * @since 0.4
     */
    public static <T extends Numeric> Matrix<T> calcAplusI(Matrix<T> A) {
        if (A.rows() != A.columns()) throw new IllegalArgumentException("Matrix A must be square");

        BasicMatrix<T> result = new BasicMatrix<>(A);
        Class<T> clazz = (Class<T>) OptionalOperations.findTypeFor(A);
        final Numeric one = One.getInstance(A.getClass().isAnnotationPresent(Columnar.class) ?
                A.getColumn(0L).getMathContext() : A.getRow(0L).getMathContext());

        try {
            for (long idx = 0L; idx < A.rows(); idx++) {
                T element = (T) A.valueAt(idx, idx).add(one).coerceTo(clazz);
                result.setValueAt(element, idx, idx);
            }
        } catch (CoercionException e) {
            throw new ArithmeticException("While computing A+I: " + e.getMessage());
        }

        return result;
    }

    /**
     * Given a matrix <strong>A</strong>, calculate <strong>I</strong>&minus;<strong>A</strong>,
     * where <strong>A</strong> is the identity matrix.  This implementation is far more
     * efficient than performing this calculation using an actual identity matrix.
     * One application of this method is in calculating the square root of a matrix via
     * a power series.
     * @param A the matrix
     * @return the result I&minus;A
     * @param <T> the element type of matrix A
     * @since 0.4
     */
    public static <T extends Numeric> Matrix<T> calcIminusA(Matrix<T> A) {
        if (A.rows() != A.columns()) throw new IllegalArgumentException("Matrix A must be square");

        Class<T> clazz = (Class<T>) OptionalOperations.findTypeFor(A);
        final Numeric one = One.getInstance(A.getClass().isAnnotationPresent(Columnar.class) ?
                A.getColumn(0L).getMathContext() : A.getRow(0L).getMathContext());

        try {
            if (A.getClass().isAnnotationPresent(Columnar.class)) {
                // we should use a column-based approach
                ColumnarMatrix<T> columnarResult = new ColumnarMatrix<>();
                for (long idx = 0L; idx < A.columns(); idx++) {
                    ColumnVector<T> col = A.getColumn(idx).negate();
                    T element = (T) one.subtract(A.valueAt(idx, idx)).coerceTo(clazz);
                    col.setElementAt(element, idx);
                    columnarResult.append(col);
                }
                return columnarResult;
            }

            // otherwise, build it up by rows
            BasicMatrix<T> result = new BasicMatrix<>();
            for (long idx = 0L; idx < A.rows(); idx++) {
                RowVector<T> row = A.getRow(idx).negate();
                T element = (T) one.subtract(A.valueAt(idx, idx)).coerceTo(clazz);
                row.setElementAt(element, idx);
                result.append(row);
            }
            return result;
        } catch (CoercionException e) {
            throw new ArithmeticException("While computing I\u2212A: " + e.getMessage());
        }
    }

    /**
     * Multiply two matrices using the Strassen/Winograd algorithm.
     * This algorithm uses 7 multiplications instead of the usual 8
     * at each stage of recursion.
     *
     * @param lhs the left-hand matrix in the multiplication
     * @param rhs the right-hand matrix in the multiplication
     * @return the product of {@code lhs} and {@code rhs}
     * @see <a href="https://en.wikipedia.org/wiki/Strassen_algorithm">the Wikipedia article on Strassen's algorithm</a>
     */
    public static Matrix<RealType> efficientMatrixMultiply(Matrix<RealType> lhs, Matrix<RealType> rhs) {
        if (lhs.rows() == rhs.rows() && lhs.columns() == rhs.columns() && lhs.rows() == lhs.columns()) {
            if (lhs.rows() == 1L) return new SingletonMatrix<>((RealType) lhs.valueAt(0L, 0L).multiply(rhs.valueAt(0L, 0L)));
            // we have two square matrices of equal dimension
            if (BigInteger.valueOf(lhs.rows()).bitCount() == 1) {
                // rows and columns are powers of 2
                if (lhs.rows() == 2L) {
                    // we have 2×2 matrices
                    // we could delegate to the normal multiply for the 2×2 case, but this actually would take longer
//                    return lhs.multiply(rhs);
                    final RealType a = lhs.valueAt(0L, 0L);
                    final RealType b = lhs.valueAt(0L, 1L);
                    final RealType c = lhs.valueAt(1L, 0L);
                    final RealType d = lhs.valueAt(1L, 1L);
                    final RealType A = rhs.valueAt(0L, 0L);
                    final RealType C = rhs.valueAt(0L, 1L);
                    final RealType B = rhs.valueAt(1L, 0L);
                    final RealType D = rhs.valueAt(1L, 1L);

                    // using the Winograd form
                    final RealType u = (RealType) c.subtract(a).multiply(C.subtract(D));
                    final RealType v = (RealType) c.add(d).multiply(C.subtract(A));
                    final RealType aA_product = (RealType) a.multiply(A);
                    final RealType w = (RealType) aA_product.add(c.add(d).subtract(a).multiply(A.add(D).subtract(C)));

                    RealType[][] result = new RealType[2][2];
                    result[0][0] = (RealType) aA_product.add(b.multiply(B));
                    result[0][1] = (RealType) w.add(v).add(a.add(b).subtract(c).subtract(d).multiply(D));
                    result[1][0] = (RealType) w.add(u).add(B.add(C).subtract(A).subtract(D).multiply(d)); // order not important for multiplying scalars
                    result[1][1] = (RealType) w.add(u).add(v);
                    return new BasicMatrix<>(result);
                } else {
                    // recursively drill down using the same relations as shown above for the scalar case
                    final Matrix<RealType> a = new SubMatrix<>(lhs, 0L, 0L, lhs.rows()/2L - 1L, lhs.columns()/2L - 1L); // 0, 0
                    final Matrix<RealType> b = new SubMatrix<>(lhs, 0L, lhs.columns()/2L, lhs.rows()/2L - 1L, lhs.columns() - 1L); // 0, 1
                    final Matrix<RealType> c = new SubMatrix<>(lhs, lhs.rows()/2L, 0L, lhs.rows() - 1L, lhs.columns()/2L - 1L); // 1, 0
                    final Matrix<RealType> d = new SubMatrix<>(lhs, lhs.rows()/2L, lhs.columns()/2L, lhs.rows() - 1L, lhs.columns() - 1L); // 1, 1
                    final Matrix<RealType> A = new SubMatrix<>(rhs, 0L, 0L, rhs.rows()/2L - 1L, rhs.columns()/2L - 1L); // 0, 0
                    final Matrix<RealType> C = new SubMatrix<>(rhs, 0L, rhs.columns()/2L, rhs.rows()/2L - 1L, rhs.columns() - 1L); // 0, 1
                    final Matrix<RealType> B = new SubMatrix<>(rhs, rhs.rows()/2L, 0L, rhs.rows() - 1L, rhs.columns()/2L - 1L); // 1, 0
                    final Matrix<RealType> D = new SubMatrix<>(rhs, rhs.rows()/2L, rhs.columns()/2L, rhs.rows() - 1L, rhs.columns() - 1L); // 1, 1

                    // using the Winograd form
                    final Matrix<RealType> u = efficientMatrixMultiply(c.subtract(a), C.subtract(D));
                    final Matrix<RealType> v = efficientMatrixMultiply(c.add(d), C.subtract(A));
                    final Matrix<RealType> aAprod = efficientMatrixMultiply(a, A);
                    final Matrix<RealType> w = aAprod.add(efficientMatrixMultiply(c.add(d).subtract(a), A.add(D).subtract(C)));

                    Matrix<RealType>[][] result = new Matrix[2][2];
                    result[0][0] = aAprod.add(efficientMatrixMultiply(b, B));
                    result[0][1] = w.add(v).add(efficientMatrixMultiply(a.add(b).subtract(c).subtract(d), D));
                    result[1][0] = w.add(u).add(efficientMatrixMultiply(d, B.add(C).subtract(A).subtract(D)));
                    result[1][1] = w.add(u).add(v);
                    return new AggregateMatrix<>(result);
                }
            } else {
                // matrices are square, but rows and columns are not a power of 2
                long resize = smallestPowerOf2GTE(lhs.rows());
                final RealType zero = new RealImpl(BigDecimal.ZERO, lhs.valueAt(0L, 0L).getMathContext());
                Matrix<RealType> left = new PaddedMatrix<>(lhs, resize, resize, zero);
                Matrix<RealType> right = new PaddedMatrix<>(rhs, resize, resize, zero);
                Matrix<RealType> result = efficientMatrixMultiply(left, right);
                // pick off the extraneous zero columns/rows
                return new SubMatrix<>(result, 0L, 0L, lhs.rows(), rhs.columns());
            }
        }
        // if the above conditions are not met, do it the old-fashioned way
        return lhs.multiply(rhs);
    }

    /**
     * Compute the smallest power of 2 that is greater than or
     * equal to a given value.
     *
     * @param input the given value
     * @return the smallest power of 2 &ge; {@code input}
     */
    public static long smallestPowerOf2GTE(long input) {
        if (input < 0L) throw new IllegalArgumentException("Negative values not supported");
        double intermediate = Math.ceil(Math.log(input) / Math.log(2d));
        return (long) Math.pow(2d, intermediate);
    }

    private static final Range<RealType> epsilonRange = new Range<>(new RealImpl("0"), new RealImpl("1"), BoundType.EXCLUSIVE);
    
    /**
     * Tests if two real values are within &epsilon; of each other.  This is
     * useful in cases where rounding error or truncation can render a test
     * using {@link RealImpl#equals(Object) the default test for equality}
     * entirely useless.
     * 
     * @param A the first real value to test for equality
     * @param B the second real value to test for equality
     * @param epsilon the largest allowable delta between A and B for them to be
     *  considered equal, a fractional value between 0 and 1 (exclusive)
     * @return true if the supplied values have a difference &lt;&nbsp;&epsilon;
     */
    public static boolean areEqualToWithin(RealType A, RealType B, RealType epsilon) {
        if (epsilon.sign() != Sign.POSITIVE || !epsilonRange.contains(epsilon)) {
            throw new IllegalArgumentException("Argument epsilon must satisfy 0 < \uD835\uDF00 \u226A 1"); // U+1D700 MATHEMATICAL ITALIC SMALL EPSILON
        }

        try {
            final RealType difference = (RealType) A.subtract(B).magnitude().coerceTo(RealType.class);
            return difference.compareTo(epsilon) < 0;
        } catch (CoercionException e) {
            throw new IllegalStateException("Cannot coerce delta to a real value", e);
        }
    }
    
    /**
     * Tests if two real vectors are equal according to
     * {@link #areEqualToWithin(RealType, RealType, RealType) }. The two
     * vectors are compared element-wise, and if any pair of elements has
     * a difference &ge;&nbsp;&epsilon;, the comparison fails fast and returns false.
     *
     * @param A       the first real-valued vector to test for equality
     * @param B       the second real-valued vector to test for equality
     * @param epsilon a value between 0 and 1, exclusive, denoting the maximum
     *   difference allowed between any pair of elements for A and B to be
     *   considered equal
     * @return true if the supplied vectors are of equal length and all the
     *   elements of A are within &epsilon; of their counterparts in B
     */
    public static boolean areEqualToWithin(Vector<RealType> A, Vector<RealType> B, RealType epsilon) {
        if (A.length() != B.length()) return false;
        for (long index = 0L; index < A.length(); index++) {
            if (!MathUtils.areEqualToWithin(A.elementAt(index), B.elementAt(index), epsilon)) return false;
        }
        return true;
    }
    
    /**
     * Tests if two real-valued matrices are equal according to
     * {@link #areEqualToWithin(RealType, RealType, RealType) }.
     * This method will attempt to use the most optimal strategy for comparing
     * two matrices (still a work in progress).  It recognizes any matrices
     * annotated as {@link Columnar} and will attempt to adjust its access
     * pattern accordingly.
     * 
     * @param A       the first real-valued matrix to test for equality
     * @param B       the second real-valued matrix to test for equality
     * @param epsilon the maximum delta allowed between corresponding elements,
     *   a fractional value between 0 and 1 (exclusive)
     * @return true if all elements of A are within &epsilon; of their counterparts in B
     */
    public static boolean areEqualToWithin(Matrix<RealType> A, Matrix<RealType> B, RealType epsilon) {
        if (A.rows() != B.rows() || A.columns() != B.columns()) return false;
        if (A.getClass().isAnnotationPresent(Columnar.class) && B.getClass().isAnnotationPresent(Columnar.class)) {
            // go by columns instead of by rows (optimal for any columnar store)
            for (long column = 0L; column < A.columns(); column++) {
                if (!MathUtils.areEqualToWithin(A.getColumn(column),
                        (Vector<RealType>) B.getColumn(column), epsilon)) return false;
            }
            return true;
        } else {
            // default behavior is to compare by rows
            for (long row = 0L; row < A.rows(); row++) {
                if (!MathUtils.areEqualToWithin(A.getRow(row),
                        (Vector<RealType>) B.getRow(row), epsilon)) return false;
            }
            return true;
        }
    }

    /**
     * Truncates a value, i.e., removes the fractional part.
     * This is conceptually the same as rounding toward zero with integer precision.
     * @param val the value to be rounded
     * @return an integer which consists of the non-fractional part of {@code val}
     */
    public static IntegerType trunc(Numeric val) {
        NumericHierarchy hval = NumericHierarchy.forNumericType(val.getClass());
        switch (hval) {
            case RATIONAL:
                IntegerType[] wholeAndFrac = ((RationalType) val).divideWithRemainder();
                return wholeAndFrac[0];
            case REAL:
                RealType rval = (RealType) val;
                return new IntegerImpl(rval.asBigDecimal().toBigInteger(),
                        rval.isExact() && rval.isCoercibleTo(IntegerType.class));
            case INTEGER:
                return (IntegerType) val;
            default:
                throw new ArithmeticException("Cannot truncate " + val);
        }
    }

    /**
     * A version of arctan(z) which works for all complex values of z.
     * Non-complex values are also handled, but the calculation for real
     * values is delegated to {@link #arctan(RealType)}.
     * <br>This function is also commonly referred to as atan().
     * @param z a value for which to compute the arctangent
     * @return the value of arctan(z)
     */
    public static Numeric arctan(Numeric z) {
        if (z.isCoercibleTo(RealType.class)) {
            try {
                RealType coerced = (RealType) z.coerceTo(RealType.class);
                return arctan(coerced);
            } catch (CoercionException e) {
                throw new IllegalStateException("Failure to coerce after check for coercion", e);
            }
        }
        // otherwise, complex values get handled here
        final ComplexType i = ImaginaryUnit.getInstance(z.getMathContext());
        final ComplexType coeff = (ComplexType) i.negate().divide(new RealImpl(decTWO, z.getMathContext()));
        ComplexType frac = (ComplexType) i.subtract(z).divide(i.add(z));
        return coeff.multiply(ln(frac));
    }

    /**
     * The main implementation of {@link #arctan(Numeric)} had one flaw:
     * For non-polar complex numbers, {@link ComplexType#argument()} requires
     * computation using {@link #atan2(RealType, RealType)}, which delegated
     * to {@link #arctan(Numeric)}.  Unfortunately, that method computes the
     * result of ln(z) using {@code z.argument()}, which results in an
     * infinite loop.<br>
     * One solution is to compute arctan() using some kind of power series.
     * We've already implemented Maclaurin series (special case of Taylor
     * series) for cos() and sin(), but Euler discovered a series for arctan()
     * that converges faster.  That's what's implemented here, though only
     * for real values.
     * @param x a real value
     * @return the computed value of atan(x)
     */
    public static RealType arctan(RealType x) {
        final MathContext compCtx = new MathContext(x.getMathContext().getPrecision() * 2,
                x.getMathContext().getRoundingMode());
        BigDecimal xsq = x.asBigDecimal().multiply(x.asBigDecimal(), compCtx);
        BigDecimal coeff = x.asBigDecimal().divide(xsq.add(BigDecimal.ONE, compCtx), compCtx);
        long nterms = compCtx.getPrecision() * 2L + 3L;
        BigDecimal val = coeff.multiply(atanEulerSum(x, nterms), compCtx);
        RealImpl result = new RealImpl(val.round(x.getMathContext()), x.getMathContext(), false);
        result.setIrrational(x.isIrrational() || classifyIfIrrational(val, compCtx));
        return result;
    }

    private static BigDecimal atanEulerSum(RealType x, long terms) {
        if (terms < 1L) throw new IllegalArgumentException("Requested number of terms is " + terms);
        final MathContext sumCtx = new MathContext(x.getMathContext().getPrecision() * 2 + 2,
                x.getMathContext().getRoundingMode());
        BigDecimal accum = BigDecimal.ZERO;
        for (long n = 0L; n < terms; n++) {
            accum = accum.add(atanEulerProduct(x, n), sumCtx);
        }
        return accum;
    }

    private static BigDecimal atanEulerProduct(RealType x, long n) {
        if (n < 0) throw new IllegalArgumentException("Product undefined for n < 0");
        if (n == 0) return BigDecimal.ONE;  // the empty product
        final MathContext prodCtx = new MathContext(x.getMathContext().getPrecision() * 2 + 4,
                x.getMathContext().getRoundingMode());
        BigDecimal accum = BigDecimal.ONE;
        BigDecimal xsq = x.asBigDecimal().multiply(x.asBigDecimal(), prodCtx);
        for (long k = 1L; k <= n; k++) {
            BigDecimal kval = BigDecimal.valueOf(k);
            BigDecimal twoKplus1 = BigDecimal.valueOf(2L * k + 1L);
            BigDecimal numerator = decTWO.multiply(kval, prodCtx).multiply(xsq, prodCtx);
            BigDecimal denominator = xsq.add(BigDecimal.ONE, prodCtx).multiply(twoKplus1, prodCtx);
            accum = accum.multiply(numerator, prodCtx).divide(denominator, prodCtx);
        }
        return accum;
    }


    /**
     * A version of arctan() that preserves quadrant information.
     * Given a point on the Cartesian plane (x,&nbsp;y) which forms
     * a triangle anchored at the origin with legs of length x and y,
     * find the angle between the positive x-axis and the hypotenuse
     * of the triangle.
     * @param y the y-axis coordinate of our &ldquo;point,&rdquo; or
     *          in the case of a complex value, the imaginary component
     * @param x the x-axis coordinate, or for a complex value,
     *          the real component
     * @return the angle formed between the positive x-axis and
     *   the vector (x,&nbsp;y)
     */
    public static Numeric atan2(RealType y, RealType x) {
        final RealType two  = new RealImpl(decTWO, y.getMathContext());
        final Numeric term = x.multiply(x).add(y.multiply(y)).sqrt();
        if (x.sign() == Sign.POSITIVE) {
            return two.multiply(arctan(y.divide(term.add(x))));
        }
        if (x.sign() != Sign.POSITIVE && !Zero.isZero(y)) {
            return two.multiply(arctan(term.subtract(x).divide(y)));
        }
        if (x.sign() == Sign.NEGATIVE && Zero.isZero(y)) {
            // use the MathContext of the non-zero argument in this case
            return Pi.getInstance(x.getMathContext());
        }
        // undefined otherwise, i.e., x = 0 and y = 0
        throw new ArithmeticException(String.format("Could not calculate atan2 for y = %1$s, x = %2$s",
                y, x));
    }

    private static final Range<RealType> acosRange = new Range<>(new RealImpl(BigDecimal.valueOf(-1L)),
            new RealImpl(BigDecimal.ONE), BoundType.INCLUSIVE);

    /**
     * Compute arccos(z), the inverse function of cos().
     * Real input values in the range [-1,&nbsp;1] will generate a real-valued result.
     * @param z the argument, may be complex or real
     * @return the value of arccos(z), typically interpreted as an angle for real results
     */
    public static Numeric arccos(Numeric z) {
        if (z instanceof RealType && !acosRange.contains((RealType) z)) {
            throw new ArithmeticException("arccos input range is " + acosRange + " for real-valued input");
        }
        // use the logarithmic form, which extends cleanly into the complex plane
        final ComplexType i = ImaginaryUnit.getInstance(z.getMathContext());
        final ComplexType negi = i.negate();
        try {
            final ComplexType one = (ComplexType) One.getInstance(z.getMathContext()).coerceTo(ComplexType.class);
            ComplexType term = (ComplexType) i.multiply(one.subtract(z.multiply(z)).sqrt()).add(z).coerceTo(ComplexType.class);
            Numeric result = negi.multiply(ln(term));
            if (result instanceof ComplexType && result.isCoercibleTo(RealType.class)) {
                // the result is a real, so return it as a RealType
                return result.coerceTo(RealType.class);
            }
            return result;
        } catch (CoercionException e) {
            throw new ArithmeticException("Type coercion error while computing ln(" + z + "): " + e.getMessage());
        }
    }

    private static RealType computeTrigSum(RealType x, Function<Long, IntegerType> subTerm) {
        final MathContext calcCtx = new MathContext(x.getMathContext().getPrecision() * 2, x.getMathContext().getRoundingMode());
        Numeric accum = ExactZero.getInstance(calcCtx);
        // we must compute at least 4 terms (polynomial order 7) to get an acceptable result within the input range
        final int termLimit = Math.max(4, calculateOptimumNumTerms(x.getMathContext(), subTerm));
        final RealType x_upscaled = new RealImpl(x.asBigDecimal(), calcCtx, x.isExact());
        for (int i = 0; i < termLimit; i++) {
            IntegerType subVal = subTerm.apply((long) i);
            if (i % 2 == 0) {
                accum = accum.add(computeIntegerExponent(x_upscaled, subVal).divide(factorial(subVal)));
            } else {
                accum = accum.subtract(computeIntegerExponent(x_upscaled, subVal).divide(factorial(subVal)));
            }
        }
        try {
            RealType raw = (RealType) accum.coerceTo(RealType.class);
            return round(raw, x.getMathContext());
        } catch (CoercionException e) {
            throw new ArithmeticException("While coercing computed sum " + accum + ": " + e.getMessage());
        }
    }

    private static class TrigTermCountKey {
        private final MathContext mctx;
        private final Function<Long, IntegerType> lambda;

        public TrigTermCountKey(MathContext mctx, Function<Long, IntegerType> lambda) {
            this.mctx = mctx;
            this.lambda = lambda;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            TrigTermCountKey that = (TrigTermCountKey) o;
            return mctx.equals(that.mctx) && lambda.equals(that.lambda);
        }

        @Override
        public int hashCode() {
            return Objects.hash(mctx, lambda);
        }
    }

    private static final Map<TrigTermCountKey, Integer> optimumTermCounts = new ConcurrentHashMap<>();

    /**
     * Given a {@link MathContext} and a mapping function to map from integer indices to subterms
     * within a power series expansion, determine the optimum number of terms to compute so that
     * the error is kept below the resolution threshold determined by the {@link MathContext}.
     * Note that this method computes a uniform estimate of the error, based on a Taylor expansion
     * centered at zero within the interval (&minus;&pi;, &pi;].  In this case,
     * the range r&nbsp;=&nbsp;&pi;, so the estimated error term is &lt;&thinsp;r<sup>N</sup>/N!
     * where N is determined by the mapping function.
     * @param ctx       the math context for computing error terms
     * @param idxMapper the mapping function for summation indices
     * @return the estimated number of terms required to minimize error
     * @see <a href="https://en.wikipedia.org/wiki/Taylor's_theorem#Estimates">Wikipedia article on Taylor's Theorem,
     *   section on estimates for the remainder</a>
     */
    private static int calculateOptimumNumTerms(MathContext ctx, Function<Long, IntegerType> idxMapper) {
        final TrigTermCountKey key = new TrigTermCountKey(ctx, idxMapper);
        if (optimumTermCounts.containsKey(key)) return optimumTermCounts.get(key);
        Logger.getLogger(MathUtils.class.getName()).log(Level.INFO, "Cache miss for MathContext {0}; calculating optimum number of power series terms.", ctx);
        final MathContext calcContext = new MathContext(ctx.getPrecision() * 2, ctx.getRoundingMode());
        final RealType realTwo = new RealImpl(decTWO, calcContext);
        final RealType maxError = (RealType) computeIntegerExponent(TEN, 1 - ctx.getPrecision(), calcContext).divide(realTwo);
        final Pi pi = Pi.getInstance(calcContext);
        int k = 2;
        do {
            IntegerType kVal = idxMapper.apply((long) k);
            Numeric error = computeIntegerExponent(pi, kVal).divide(factorial(kVal));
            if (error instanceof RationalType) {
                // if we got back a rational, it's because the exponent we calculated has no fractional digits left after rounding
                Logger.getLogger(MathUtils.class.getName()).log(Level.WARNING,
                        "Compute MathContext {0} does not provide sufficient resolution to represent \uD835\uDF0B^{1}.",
                        new Object[] {calcContext, kVal});
                error = new RealImpl((RationalType) error); // to avoid coerceTo() penalties
            }
            Logger.getLogger(MathUtils.class.getName()).log(Level.FINE, "k = {0} error = {1}", new Object[] { k, error });
            if (((RealType) error).compareTo(maxError) < 0) {
                Logger.getLogger(MathUtils.class.getName()).log(Level.INFO, "Recommend computing {0} power series terms for MathContext {1}.",
                        new Object[] { k, ctx });
                optimumTermCounts.put(key, k);
                return k;
            }
        } while (++k < calcContext.getPrecision());
        throw new ArithmeticException("Cannot determine optimum number of power series terms for keeping error < " + maxError);
    }

    private static RealType mapToInnerRange(RealType input, Range<RealType> internalRange) {
        if (internalRange.contains(input)) return input;

        final RealType period = (RealType) internalRange.getUpperBound().subtract(internalRange.getLowerBound());
        RealType temp = input;
        while (internalRange.isBelow(temp)) {
            temp = (RealType) temp.add(period);
        }
        while (internalRange.isAbove(temp)) {
            temp = (RealType) temp.subtract(period);
        }
        return temp;
    }

    /**
     * The cosine function.
     * @param x a real value (e.g., an angle in radians)
     * @return the real-valued result of cos(x)
     */
    public static RealType cos(RealType x) {
        if (x instanceof Pi) return new RealImpl(BigDecimal.valueOf(-1L), x.getMathContext());
        RealType inBounds = mapToInnerRange(x, RangeUtils.getAngularInstance(x.getMathContext()));
        return computeTrigSum(inBounds, n -> new IntegerImpl(BigInteger.valueOf(2L * n)));
    }

    /**
     * The sine function.
     * @param x a real value (e.g., an angle in radians)
     * @return the real-valued result of sin(x)
     */
    public static RealType sin(RealType x) {
        if (x instanceof Pi) return new RealImpl(BigDecimal.ZERO, x.getMathContext());
        RealType inBounds = mapToInnerRange(x, RangeUtils.getAngularInstance(x.getMathContext()));
        return computeTrigSum(inBounds, n -> new IntegerImpl(BigInteger.valueOf(2L * n + 1L)));
    }

    /**
     * The tangent function.
     * @param x a real value (e.g., an angle in radians)
     * @return the real-valued result of tan(x)
     */
    public static RealType tan(RealType x) {
        final MathContext ctx = x.getMathContext();
        final Pi pi = Pi.getInstance(ctx);
        final RealType epsilon = computeIntegerExponent(TEN, 1 - ctx.getPrecision(), ctx);
        // check for zero crossings before incurring the cost of computing
        // an in-range argument or calculating the sin() and cos() power series
        RealType argOverPi = x.divide(pi).magnitude();
        if (((RealType) argOverPi.subtract(argOverPi.floor())).compareTo(epsilon) < 0) {
            // tan(x) has zero crossings periodically at x=k𝜋 ∀ k ∈ ℤ
            return new RealImpl(BigDecimal.ZERO, ctx);
        }
        Range<RealType> range = RangeUtils.getTangentInstance(ctx);
        RealType inBounds = mapToInnerRange(x, range);
        // check if we're within epsilon of the limits of our input range
        // if so, tan(x) blows up to infinity
        if (areEqualToWithin(inBounds, range.getLowerBound(), epsilon)) {
            return RealInfinity.getInstance(Sign.NEGATIVE, ctx);
        } else if (areEqualToWithin(inBounds, range.getUpperBound(), epsilon)) {
            return RealInfinity.getInstance(Sign.POSITIVE, ctx);
        }
        return (RealType) sin(inBounds).divide(cos(inBounds));
    }

    /**
     * The complex-valued cosine function.
     * @param z a complex value
     * @return the complex-valued result of cos(z)
     */
    public static ComplexType cos(ComplexType z) {
        if (z.isCoercibleTo(RealType.class)) return new ComplexRectImpl(cos(z.real()));
        final ComplexType i = ImaginaryUnit.getInstance(z.getMathContext());
        final ComplexType iz = (ComplexType) i.multiply(z);
        final Euler e = Euler.getInstance(z.getMathContext());
        return (ComplexType) e.exp(iz).add(e.exp(iz.negate())).divide(new RealImpl(decTWO, z.getMathContext()));
    }

    /**
     * The complex-valued sine function.
     * @param z a complex value
     * @return the complex-valued result of sin(z)
     */
    public static ComplexType sin(ComplexType z) {
        if (z.isCoercibleTo(RealType.class)) return new ComplexRectImpl(sin(z.real()));
        final ComplexType i = ImaginaryUnit.getInstance(z.getMathContext());
        final ComplexType iz = (ComplexType) i.multiply(z);
        final Euler e = Euler.getInstance(z.getMathContext());
        return (ComplexType) e.exp(iz).subtract(e.exp(iz.negate())).divide(new RealImpl(decTWO, z.getMathContext()).multiply(i));
    }

    /**
     * The complex version of the hyperbolic tangent function.
     *
     * @param z a complex-valued argument
     * @return the result of computing tanh(z)
     */
    public static ComplexType tanh(ComplexType z) {
        final RealType one = new RealImpl(BigDecimal.ONE, z.getMathContext());
        final RealType two = new RealImpl(decTWO, z.getMathContext());
        ComplexType scaledArg = (ComplexType) z.multiply(two);
        final Euler e = Euler.getInstance(z.getMathContext());
        ComplexType exp = e.exp(scaledArg);
        return (ComplexType) exp.subtract(one).divide(exp.add(one));
    }

    /**
     * Compute the hyperbolic tangent function, tanh(x).
     * This makes an excellent thresholding function for
     * applications where the sigmoid function is inappropriate
     * or not performant.  The output of this function smoothly
     * varies from &minus;1 to 1 over all reals, with a
     * transition centered on the origin.
     *
     * @param x the real-valued argument
     * @return the result of computing tanh(x)
     */
    public static RealType tanh(RealType x) {
        final RealType one = new RealImpl(BigDecimal.ONE, x.getMathContext());
        final RealType two = new RealImpl(decTWO, x.getMathContext());
        RealType scaledArg = (RealType) x.multiply(two);
        final Euler e = Euler.getInstance(x.getMathContext());
        RealType exp = e.exp(scaledArg);
        return (RealType) exp.subtract(one).divide(exp.add(one));
    }

    /**
     * Factory method that provides a comparator that works with
     * all {@link Numeric} subtypes that are comparable, even
     * distinctly different subtypes. This comparator also
     * works with concrete classes that inherit directly from
     * {@link Numeric}.
     * @return a comparator suitable for use with e.g. heterogeneous collections
     */
    public static Comparator<Numeric> obtainGenericComparator() {
        return new Comparator<>() {
            @Override
            public int compare(Numeric A, Numeric B) {
                if (A instanceof Comparable && B instanceof Comparable) {
                    Class<? extends Numeric> classA = A.getClass();
                    Class<? extends Numeric> classB = B.getClass();
                    NumericHierarchy h1 = NumericHierarchy.forNumericType(classA);
                    NumericHierarchy h2 = NumericHierarchy.forNumericType(classB);
                    // if A or B is a direct subclass of Numeric, delegate to
                    // its compareTo() method
                    if (h1 == null) {
                        Comparable<Numeric> Acomp = (Comparable<Numeric>) A;
                        return Acomp.compareTo(B);
                    } else if (h2 == null) {
                        Comparable<Numeric> Bcomp = (Comparable<Numeric>) B;
                        return -Bcomp.compareTo(A);
                    }
                    // otherwise, we need to do type coercion
                    try {
                        if (h1.compareTo(h2) >= 0) {
                            Comparable<Numeric> Bconv = (Comparable<Numeric>) B.coerceTo(h1.getNumericType());
                            return -Bconv.compareTo(A);
                        } else {
                            Comparable<Numeric> Aconv = (Comparable<Numeric>) A.coerceTo(h2.getNumericType());
                            return Aconv.compareTo(B);
                        }
                    } catch (CoercionException ce) {
                        Logger.getLogger(MathUtils.class.getName()).log(Level.SEVERE, "No common type found for {0} and {1}.",
                                new Object[] { h1, h2 });
                        throw new IllegalArgumentException("Failure to coerce arguments to a common type", ce);
                    }
                } else {
                    throw new IllegalArgumentException("Numeric subtype must be comparable");
                }
            }

            @Override
            public boolean equals(Object obj) {
                if (obj instanceof Comparator) {
                    return obj.getClass() == this.getClass();
                }
                return false;
            }
        };
    }

    private static final Map<Class<? extends UnaryFunction>, Class<? extends UnaryFunction>> inverses =
            new HashMap<>();

    static {
        inverses.put(Negate.class, Negate.class);
        inverses.put(Exp.class, NaturalLog.class);
        inverses.put(NaturalLog.class, Exp.class);
    }

    /**
     * Given a {@link UnaryFunction}, return a function (if known) which performs the inverse operation.
     * @param fClazz the {@link Class} of the unary function for which we wish to obtain the inverse
     * @return the {@link Class} of the inverse unary function, if known, otherwise {@code null}
     */
    public static Class<? extends UnaryFunction> inverseFunctionFor(Class<? extends UnaryFunction> fClazz) {
        return inverses.get(fClazz);
    }
}
