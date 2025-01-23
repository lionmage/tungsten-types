package tungsten.types.functions.impl;

import tungsten.types.Numeric;
import tungsten.types.exceptions.CoercionException;
import tungsten.types.functions.Proxable;
import tungsten.types.functions.Term;
import tungsten.types.functions.UnaryArgVector;
import tungsten.types.functions.UnaryFunction;
import tungsten.types.numerics.impl.IntegerImpl;
import tungsten.types.util.ClassTools;
import tungsten.types.util.MathUtils;

import java.math.BigInteger;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * A representation of a Taylor polynomial, the result of evaluating a Taylor series
 * for a function.
 * @param <T> the input type of the parameter for this polynomial
 * @param <R> the return type of this polynomial
 * @author Robert Poole, <a href="mailto:Tarquin.AZ@gmail.com">Gmail</a> or
 *   <a href="mailto:tarquin@alum.mit.edu">MIT alumni e-mail</a>
 */
public abstract class TaylorPolynomial<T extends Numeric & Comparable<? super T>, R extends Numeric> extends Polynomial<T, R> {
    private final String argname;
    private final T diffAround;
    protected final UnaryFunction<T, R> f0;

    /**
     * Instantiate a Taylor polynomial by approximating a given function with a
     * Taylor series.
     * @param argName              the argument name for this polynomial
     * @param original             the original function to approximate with a Taylor series
     * @param differentiableAround the point a<sub>0</sub> around which to differentiate {@code original}
     */
    public TaylorPolynomial(String argName, UnaryFunction<T, R> original, T differentiableAround) {
        super(original.getReturnType());
        this.argname = argName;
        this.f0 = original;
        this.diffAround = differentiableAround;
        // the first term is f(a₀) and is a constant
        final R f0_a0;
        if (original instanceof Proxable) {
            var proxyfunc = ((Proxable<T, R>) original).obtainProxy();
            f0_a0 = proxyfunc.apply(differentiableAround);
        } else {
            f0_a0 = original.apply(differentiableAround);
        }
        super.add(new ConstantTerm<>(f0_a0));
    }

    @Override
    public Class<T> getArgumentType() {
        if (f0.getArgumentType() != null) {
            if (!f0.getArgumentType().isAssignableFrom(diffAround.getClass())) {
                Logger.getLogger(TaylorPolynomial.class.getName()).log(Level.WARNING,
                        "Argument type mismatch: {0} vs. {1}",
                        new Object[] { f0.getArgumentType().getTypeName(), diffAround.getClass().getTypeName() });
            } else {
                return f0.getArgumentType();
            }
        }
        return (Class<T>) ClassTools.getInterfaceTypeFor(diffAround.getClass());
    }

    protected TaylorPolynomial(TaylorPolynomial<T, R> toCopy, List<Term<T, R>> computedTerms) {
        super(computedTerms, toCopy.getReturnType());
        this.argname = toCopy.argname;
        this.diffAround = toCopy.diffAround;
        this.f0 = toCopy.f0;
    }

    /**
     * Apply this function to x&nbsp;&minus;&nbsp;a<sub>0</sub> for the given
     * value of x.
     * This is a bit of a hack, but superior in performance to composing this
     * polynomial with a separate linear function.
     *
     * @param input the single argument x to this polynomial function
     * @return the function &fnof;(x &minus; a<sub>0</sub>) evaluated for the given x
     */
    public R apply(T input) {
        T translated = (T) input.subtract(diffAround);  // x - a₀
        return super.apply(new UnaryArgVector<>(argname, translated));
    }

    private void generateNthTerm(long n) {
        if (countTerms() - 1L >= n) return;  // we already generated the requested term
        try {
            R coeff = (R) f_n(n).apply(diffAround)
                    .divide(MathUtils.factorial(new IntegerImpl(BigInteger.valueOf(n)))).coerceTo(getReturnType());
            PolyTerm<T, R> nterm = new PolyTerm<>(coeff, List.of(argname), List.of(n));
            super.add(nterm);
        } catch (CoercionException e) {
            throw new ArithmeticException("While computing the " + n + "th term of a Taylor Polynomial: " + e.getMessage());
        }
    }

    @Override
    public TaylorPolynomial<T, R> firstN(long N) {
        return new TaylorPolynomial<>(this, termStream().limit(N).collect(Collectors.toList())) {
            @Override
            protected UnaryFunction<T, R> f_n(long n) {
                return TaylorPolynomial.this.f_n(n);
            }
        };
    }

    /**
     * Obtain a Taylor polynomial with exactly n terms.
     * @param n the desired number of terms
     * @return a Taylor polynomial with {@code n} terms
     */
    public TaylorPolynomial<T, R> getForNTerms(long n) {
        if (n < countTerms() - 1L) {
            return firstN(n);
        }
        for (long i = countTerms(); i <= n; i++) {
            generateNthTerm(i);
        }
        return this;
    }

    /**
     * Method to compute the n<sup>th</sup> derivative of &fnof;<sub>0</sub>.
     * Implementing classes should cache intermediate results.
     *
     * @param n the order of the derivative to obtain
     * @return the n<sup>th</sup> derivative of &fnof;<sub>0</sub>
     */
    protected abstract UnaryFunction<T, R> f_n(long n);

    @Override
    public long arity() {
        return 1L; // we're generating univariate polynomials
    }
}
