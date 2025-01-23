package tungsten.types.functions.impl;

import tungsten.types.Numeric;
import tungsten.types.Range;
import tungsten.types.annotations.Differentiable;
import tungsten.types.exceptions.CoercionException;
import tungsten.types.functions.ArgVector;
import tungsten.types.functions.NumericFunction;
import tungsten.types.functions.Term;
import tungsten.types.functions.UnaryFunction;
import tungsten.types.numerics.IntegerType;
import tungsten.types.numerics.RationalType;
import tungsten.types.numerics.RealType;
import tungsten.types.numerics.impl.ExactZero;
import tungsten.types.numerics.impl.One;
import tungsten.types.util.ClassTools;
import tungsten.types.util.RangeUtils;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigInteger;
import java.math.MathContext;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A representation of a polynomial, consisting of one or more terms.
 * @param <T> the type of the input parameter or parameters
 * @param <R> the return type of this function
 * @author Robert Poole, <a href="mailto:Tarquin.AZ@gmail.com">Gmail</a> or
 *   <a href="mailto:tarquin@alum.mit.edu">MIT alumni e-mail</a>
 */
public class Polynomial<T extends Numeric, R extends Numeric> extends NumericFunction<T, R> {
    private final List<Term<T, R>> terms = new ArrayList<>();

    protected Polynomial(List<Term<T, R>> supplied, Class<R> rtnType) {
        super(rtnType);
        terms.addAll(supplied);
    }

    /**
     * Instantiate a polynomial initially containing no terms.
     * @param rtnType the return type of this polynomial
     */
    public Polynomial(Class<R> rtnType) {
        super(rtnType);
    }

    @SafeVarargs
    public Polynomial(Term<T, R>... initialTerms) {
        super(initialTerms[0].getReturnType());  // use the return type of the first term
        Arrays.stream(initialTerms).forEachOrdered(terms::add);
    }

    private static final Pattern constPattern = Pattern.compile("^\\s*([+-]?\\d+[./]?\\d*)");

    /**
     * Given a string representation of a polynomial, parse it into its
     * component terms and assemble them into a new {@code Polynomial}.
     * <br>
     * Note that terms are separated by a + (plus) surrounded by at least
     * one whitespace character on either side. This is to avoid confusing
     * the regex-based parser.  Parsing the contents of individual terms is up
     * to the {@link String}-based constructor for that term type.
     *
     * @param init a textual representation of a polynomial
     */
    public Polynomial(String init, Class<R> rtnType) {
        super(rtnType);
        String[] strTerms = init.split("\\s+\\+\\s+");
        for (String termInit : strTerms) {
            Matcher m = constPattern.matcher(termInit);
            if (m.matches()) {
                terms.add(new ConstantTerm<>(termInit.stripLeading(), rtnType));
                continue;
            }
            if (termInit.contains("/")) {
                // probably a rational exp term
                terms.add(new RationalExponentPolyTerm<>(termInit, rtnType));
                continue;
            }
            terms.add(new PolyTerm<>(termInit, rtnType));
        }
    }

    @Override
    public R apply(ArgVector<T> arguments) {
        final MathContext ctx = arguments.getMathContext() == null ? arguments.elementAt(0L).getMathContext() : arguments.getMathContext();
        // on the off chance that summing the result of evaluating two or more terms
        // gives us something other than the desired response type,
        // keep everything as a Numeric until we're ready to return
        // Note: this avoids dealing with checked exceptions inside the stream
        Numeric result = terms.parallelStream().map(t -> t.apply(arguments))
                .map(Numeric.class::cast)
                .reduce(ExactZero.getInstance(ctx), Numeric::add);

        try {
            return (R) result.coerceTo(getReturnType());
        } catch (CoercionException e) {
            throw new IllegalStateException("Unable to convert result of polynomial evaluation to " +
                    getReturnType().getTypeName(), e);
        }
    }

    public void add(Term<? extends T, ? extends R> term) {
        if (term instanceof PolyTerm) {
            PolyTerm<T, R> arg = (PolyTerm<T, R>) term;
            Optional<PolyTerm<T, R>> matchingTerm = terms.stream().filter(PolyTerm.class::isInstance)
                    .map(t -> (PolyTerm<T, R>) t) // would love to use ::cast here, but then we'd get a raw type
                    .filter(arg::hasMatchingSignature).findAny();
            if (matchingTerm.isPresent()) {
                PolyTerm<T, R> old = matchingTerm.get();
                List<Long> exponents = Arrays.stream(arg.expectedArguments())
                        .map(arg::order).collect(Collectors.toList());
                PolyTerm<T, R> updated = new PolyTerm<>((R) term.coefficient().add(old.coefficient()),
                        Arrays.asList(term.expectedArguments()), exponents);
                terms.remove(old);
                terms.add(updated);
            } else {
                terms.add((Term<T, R>) term);
            }
        } else if (term instanceof RationalExponentPolyTerm) {
            RationalExponentPolyTerm<T, R> arg = (RationalExponentPolyTerm<T, R>) term;
            Optional<RationalExponentPolyTerm<T, R>> matchingTerm = terms.stream()
                    .filter(RationalExponentPolyTerm.class::isInstance)
                    .map(t -> (RationalExponentPolyTerm<T, R>) t) // would love to use ::cast here, but then we'd get a raw type
                    .filter(arg::hasMatchingSignature).findAny();
            if (matchingTerm.isPresent()) {
                RationalExponentPolyTerm<T, R> old = matchingTerm.get();
                List<RationalType> exponents = Arrays.stream(arg.expectedArguments())
                        .map(arg::exponentFor).collect(Collectors.toList());
                R sum = (R) term.coefficient().add(old.coefficient());
                RationalExponentPolyTerm<T, R> updated = new RationalExponentPolyTerm<>(sum,
                        Arrays.asList(term.expectedArguments()), exponents);
                terms.remove(old);
                terms.add(updated);
            } else {
                terms.add((Term<T, R>) term);
            }
        } else if (term.isConstant()) {
            // find all the constant terms and combine them
            R aggConst = terms.stream().filter(Term::isConstant).map(Term::coefficient)
                    .reduce(term.coefficient(), (c1, c2) -> (R) c1.add(c2));
            terms.removeIf(Term::isConstant);
            terms.add(new ConstantTerm<>(aggConst));
        } else {
            // if we're not sure what to do with it, throw an exception
            throw new IllegalArgumentException("This polynomial does not know how to handle a term of type " + term.getClass().getTypeName());
        }
    }

    public void add(UnaryFunction<T, R> alienFunc) {
        if (alienFunc instanceof Product) {
            Product<T, R> prod = (Product<T, R>) alienFunc;
            this.add(termFromProd(prod));
        } else if (alienFunc instanceof Sum) {
            Sum<T, R> sum = (Sum<T, R>) alienFunc;
            sum.stream().forEach(this::add);
        } else if (alienFunc instanceof Reflexive) {
            this.add(new PolyTerm<>(List.of(alienFunc.expectedArguments()[0]), List.of(1L), getReturnType()));
        }
        // add additional cases as-needed

        // if we can't handle the supplied function, throw an exception
        throw new IllegalArgumentException("This polynomial does not know how to handle function " + alienFunc +
                " of type " + alienFunc.getClass().getTypeName());
    }

    public Polynomial<T, R> multiply(Term<T, R> term) {
        List<Term<T, R>> multTerms = terms.stream().map(orig -> orig.multiply(term)).collect(Collectors.toList());
        return new Polynomial<>(multTerms, getReturnType());
    }

    public Polynomial<T, R> multiply(UnaryFunction<T, R> alienFunc) {
        if (alienFunc instanceof Const) {
            Const<T, R> foreignConst = (Const<T, R>) alienFunc;
            List<Term<T, R>> scaleTerms = termStream().map(orig -> orig.scale(foreignConst.inspect()))
                    .collect(Collectors.toList());
            return new Polynomial<>(scaleTerms, getReturnType());
        } else if (alienFunc instanceof Pow) {
            Pow<T, R> powerFunc = (Pow<T, R>) alienFunc;
            List<Term<T, R>> multTerms = termStream().map(orig -> orig.multiply(powerFunc)).collect(Collectors.toList());
            return new Polynomial<>(multTerms, getReturnType());
        } else if (alienFunc instanceof Sum) {
            Sum<T, R> sum = (Sum<T, R>) alienFunc;
            Polynomial<T, R> result = new Polynomial<>(getReturnType());
            sum.stream().map(this::multiply).forEach(result::add);
            return result;
        } else if (alienFunc instanceof Product) {
            Product<T, R> prod = (Product<T, R>) alienFunc;
            if (prod.termCount() == 1L) return multiply(prod.stream().findFirst().orElseThrow());
            Term<T, R> pterm = termFromProd(prod);
            return this.multiply(pterm);
        } else if (alienFunc instanceof Reflexive) {
            PolyTerm<T, R> term = new PolyTerm<>(List.of(alienFunc.expectedArguments()[0]),
                    List.of(1L), getReturnType());
            return this.multiply(term);
        }
        // we can add more cases here as necessary
        throw new UnsupportedOperationException("Currently unable to handle a foreign function of type " +
                alienFunc.getClass().getTypeName());
    }

    // UnaryFunction is another one of those classes that has issues when supplying type arguments under
    // certain conditions.  This compiles, but gets flagged for raw use of a parameterized class.
    private static final List<Class<? extends UnaryFunction>> supported = List.of(Const.class, Pow.class);

    private Term<T, R> termFromProd(Product<T, R> product) {
        if (product.stream().map(Object::getClass).anyMatch(p -> !supported.contains(p))) {
            throw new IllegalArgumentException("Product contains a foreign function that cannot be handled");
        }
        try {
            R coeff = (R) product.stream().filter(Const.class::isInstance).map(Const.class::cast)
                    .map(Const::inspect).reduce(One.getInstance(MathContext.UNLIMITED), Numeric::multiply).coerceTo(getReturnType());
            // Fortunately, we don't need the arg type or return type of Pow here, but it really
            // bothers me that even Pow<?, ?> causes issues.  Still haven't found a good solution.
            List<Pow> subterms = product.stream().filter(Pow.class::isInstance).map(Pow.class::cast)
                    .collect(Collectors.toList());  // Trying to make this a List<Pow<T, R>> or a List<Pow<?, ?>> causes build issues
            List<String> varNames = subterms.stream().map(f -> f.expectedArguments()[0]).collect(Collectors.toList());
            List<Numeric> exponents = subterms.stream().map(Pow::getExponent).collect(Collectors.toList());
            if (exponents.stream().anyMatch(RationalType.class::isInstance)) {
                List<RationalType> rationalExponents = exponents.stream().map(this::safeCoerce).collect(Collectors.toList());
                return new RationalExponentPolyTerm<>(coeff, varNames, rationalExponents);
            }
            List<Long> integerExponents = exponents.stream().map(IntegerType.class::cast).map(IntegerType::asBigInteger)
                    .map(BigInteger::longValueExact).collect(Collectors.toList());
            return new PolyTerm<>(coeff, varNames, integerExponents);
        } catch (CoercionException e) {
            throw new IllegalArgumentException("While calculating aggregate coefficient", e);
        }
    }

    private RationalType safeCoerce(Numeric orig) {
        try {
            return (RationalType) orig.coerceTo(RationalType.class);
        } catch (CoercionException e) {
            throw new IllegalStateException("Unable to coerce " + orig + " to a rational", e);
        }
    }

    public Polynomial<T, R> add(Polynomial<T, R> other) {
        Polynomial<T, R> aggregate = new Polynomial<>(terms, getReturnType());
        other.termStream().forEach(aggregate::add);
        return aggregate;
    }

    public Polynomial<T, R> multiply(Polynomial<T, R> other) {
        Polynomial<T, R> product = new Polynomial<>(getReturnType());
        for (Term<T, R> myterm : terms) {
            Polynomial<T, R> partialProduct = other.multiply(myterm);
            partialProduct.termStream().forEach(product::add);
        }
        return product;
    }

    /**
     * Obtain the truncation of this polynomial to the first N elements.
     *
     * @param N the number of elements to retain
     * @return a new polynomial containing {@code N} elements
     */
    public Polynomial<T, R> firstN(long N) {
        List<Term<T, R>> nTerms = termStream().limit(N).collect(Collectors.toList());
        return new Polynomial<>(nTerms, getReturnType());
    }

    /**
     * Obtain a {@link Stream} of {@code Term}s for this polynomial
     * @return a stream consisting of the terms of this polynomial
     */
    protected Stream<Term<T, R>> termStream() { return terms.stream(); }

    /**
     * Generate a polynomial identical to this one, but with the terms
     * ordered according to the exponent of the given variable.<br>
     * The ordering is from the highest exponent in {@code varName} to the
     * lowest.
     * @param varName the name of the variable to use for sorting terms
     * @return the reordered polynomial
     */
    public Polynomial<T, R> sortByOrderIn(String varName) {
        List<Term<T, R>> sortedTerms = new ArrayList<>(terms);
        sortedTerms.sort((A, B) -> (int) (B.order(varName) - A.order(varName)));
        return new Polynomial<>(sortedTerms, getReturnType());
    }

    /**
     * Obtain a count of the terms in this polynomial.
     * @return the number of terms in this polynomial
     */
    public long countTerms() {
        return terms.stream().count();
    }

    @Override
    public long arity() {
        return getUniqueArgnames().stream().count();
    }

    /**
     * Compute the order of a given variable in this polynomial.
     * The order is the maximum exponent of this variable across
     * all terms.
     * @param argName the name of the variable
     * @return the order of this variable, or 0 if it does not occur
     */
    public long order(String argName) {
        return terms.stream().mapToLong(t -> t.order(argName)).max().orElse(0L);
    }

    @Override
    public String[] expectedArguments() {
        return getUniqueArgnames().toArray(String[]::new);
    }

    @Override
    public Range<RealType> inputRange(String argName) {
        return terms.parallelStream().map(term -> term.inputRange(argName))
                .reduce(RangeUtils.ALL_REALS, Range::chooseNarrowest);
    }

    @Override
    public Class<T> getArgumentType() {
        if (!terms.isEmpty()) {
            return terms.get(0).getArgumentType();
        }
        // the following may return null or fail altogether
        return (Class<T>) ClassTools.getTypeArguments(NumericFunction.class, this.getClass()).get(0);
    }

    private Set<String> getUniqueArgnames() {
        final Set<String> result = new LinkedHashSet<>();
        terms.stream().map(Term::expectedArguments).map(Arrays::asList).forEachOrdered(result::addAll);
        return result;
    }

    @Differentiable
    public Polynomial<T, R> differentiate(String varName) {
        List<Term<T, R>> dterms = new ArrayList<>();
        for (Term<T, R> term : terms) {
            Optional<Method> diff = Arrays.stream(term.getClass().getMethods())
                    .filter(m -> m.isAnnotationPresent(Differentiable.class))
                    .findAny();
            Method diffMethod = diff.orElseThrow(() -> new ArithmeticException("No derivative for term " + term + " in " + varName));
            if (diffMethod.getParameterCount() == 1 && diffMethod.getParameterTypes()[0] == String.class) {
                try {
                    dterms.add((Term<T, R>) diffMethod.invoke(term, varName));
                } catch (IllegalAccessException | InvocationTargetException e) {
                    throw new IllegalStateException("Differentiable method access issue", e);
                }
            } else {
                throw new ArithmeticException("Unable to differentiate unknown Term type: " +
                        term.getClass().getTypeName());
            }
        }
        return new Polynomial<>(dterms, getReturnType());
    }

    @Override
    public String toString() {
        // U+2009 is a thin space
        return terms.stream().map(Object::toString).collect(Collectors.joining("\u2009+\u2009"));
    }
}
