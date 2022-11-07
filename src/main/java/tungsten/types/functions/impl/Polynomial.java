package tungsten.types.functions.impl;

import tungsten.types.Numeric;
import tungsten.types.Range;
import tungsten.types.annotations.Differentiable;
import tungsten.types.exceptions.CoercionException;
import tungsten.types.functions.ArgVector;
import tungsten.types.functions.NumericFunction;
import tungsten.types.functions.Term;
import tungsten.types.numerics.RationalType;
import tungsten.types.numerics.RealType;
import tungsten.types.numerics.impl.ExactZero;
import tungsten.types.util.RangeUtils;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.math.MathContext;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Polynomial<T extends Numeric, R extends Numeric> extends NumericFunction<T, R> {
    private final List<Term<T, R>> terms = new ArrayList<>();
    private final Class<R> rtnClass = (Class<R>) ((Class) ((ParameterizedType) getClass()
            .getGenericSuperclass()).getActualTypeArguments()[1]);

    protected Polynomial(List<Term<T, R>> supplied) {
        terms.addAll(supplied);
    }

    public Polynomial() {}

    @SafeVarargs
    public Polynomial(Term<T, R>... initialTerms) {
        Arrays.stream(initialTerms).forEachOrdered(terms::add);
    }

    private static final Pattern constPattern = Pattern.compile("^\\s*([+-]?\\d+[./]?\\d*)");

    /**
     * Given a string representation of a polynomial, parse it into its
     * component terms and assemble them into a new {@code Polynomial}.
     * <br/>
     * Note that terms are separated by a + (plus) surrounded by at least
     * one whitespace character on either side. This is to avoid confusing
     * the regex-based parser.  Parsing the contents of individual terms is up
     * to the {@link String}-based constructor for that term type.
     *
     * @param init a textual representation of a polynomial
     */
    public Polynomial(String init) {
        String[] strTerms = init.split("\\s+\\+\\s+");
        for (String termInit : strTerms) {
            Matcher m = constPattern.matcher(termInit);
            if (m.matches()) {
                terms.add(new ConstantTerm<>(termInit.stripLeading(), rtnClass));
                continue;
            }
            if (termInit.contains("/")) {
                // probably a rational exp term
                terms.add(new RationalExponentPolyTerm<>(termInit, rtnClass));
                continue;
            }
            terms.add(new PolyTerm<>(termInit, rtnClass));
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
            return (R) result.coerceTo(rtnClass);
        } catch (CoercionException e) {
            throw new IllegalStateException("Unable to convert result of polynomial evaluation to " +
                    rtnClass.getTypeName(), e);
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
                RationalExponentPolyTerm<T, R> updated = new RationalExponentPolyTerm<T, R>(sum,
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

    /**
     * Obtain the truncation of this polynomial to the first N elements.
     *
     * @param N the number of elements to retain
     * @return a new polynomial containing {@code N} elements
     */
    public Polynomial<T, R> firstN(long N) {
        List<Term<T, R>> nTerms = termStream().limit(N).collect(Collectors.toList());
        return new Polynomial<>(nTerms);
    }

    protected Stream<Term<T, R>> termStream() { return terms.stream(); }

    /**
     * Generate a polynomial identical to this one, but with the terms
     * ordered according to the exponent of the given variable.
     * <br/>
     * The ordering is from the highest exponent in {@code varName to the
     * lowest.
     * }
     * @param varName the name of the variable to use for sorting terms
     * @return the reordered polynomial
     */
    public Polynomial<T, R> sortByOrderIn(String varName) {
        List<Term<T, R>> sortedTerms = new ArrayList<>(terms);
        sortedTerms.sort((A, B) -> (int) (B.order(varName) - A.order(varName)));
        return new Polynomial<>(sortedTerms);
    }

    public long countTerms() {
        return terms.stream().count();
    }

    @Override
    public long arity() {
        return getUniqueArgnames().stream().count();
    }

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
        return new Polynomial<>(dterms);
    }

    @Override
    public String toString() {
        // U+2009 is a thin space
        return terms.stream().map(Object::toString).collect(Collectors.joining("\u2009+\u2009"));
    }
}
