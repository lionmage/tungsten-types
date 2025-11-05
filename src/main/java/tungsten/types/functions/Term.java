package tungsten.types.functions;

import tungsten.types.Numeric;
import tungsten.types.Range;
import tungsten.types.functions.impl.Pow;
import tungsten.types.numerics.RealType;

import java.util.*;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * An abstract representation of a polynomial term.
 * @param <T> the type of the argument or arguments (variables) of this term
 * @param <R> the return type of this term, i.e., the {@code Numeric} type of the result of evaluation
 */
public abstract class Term<T extends Numeric, R extends Numeric> extends NumericFunction<T, R> {
    private final Map<String, Range<RealType>> rangeMap = new TreeMap<>();
    protected final List<String> varNames;

    /**
     * Instantiate a term with no arguments (variables) and
     * a given return type.
     * @param clazz the return type of this term
     */
    protected Term(Class<R> clazz) {
        super(clazz);
        varNames = Collections.emptyList();
    }

    /**
     * Instantiate a term with a set of known variable names but no bindings.
     *
     * @param variableNames a collection of the variable names used by this term
     * @param clazz         the return type of this term
     */
    protected Term(Collection<String> variableNames, Class<R> clazz) {
        super(clazz);
        varNames = new ArrayList<>(variableNames);
    }

    /**
     * Instantiate a term with a set of known variable names and mappings for the
     * input ranges for each variable.
     *
     * @param variableNames a collection of variable names
     * @param inputRanges   a {@code Map} of variable names to {@code Range}s
     * @param clazz         the return type of this term
     */
    protected Term(Collection<String> variableNames, Map<String, Range<RealType>> inputRanges, Class<R> clazz) {
        this(variableNames, clazz);
        varNames.forEach(varName -> rangeMap.put(varName, inputRanges.get(varName)));
    }

    /**
     * Convenience constructor using varargs to specify variable names.
     * @param clazz         the return type of this term
     * @param variableNames the variable name or names for this term
     */
    protected Term(Class<R> clazz, String... variableNames) {
        super(clazz);
        HashSet<String> tester = new HashSet<>();
        if (Arrays.stream(variableNames).map(tester::add).anyMatch(b -> b == Boolean.FALSE)) {
            throw new IllegalArgumentException("Argument names must be unique");
        }
        this.varNames = Arrays.asList(variableNames);
    }

    /**
     * Convenience constructor using varargs to specify variable names and
     * a mapping of variable names to input ranges.
     * @param clazz         the return type of this term
     * @param inputRanges   the {@code Map} of variable names to {@code Range}s of input
     * @param variableNames the variable name or names for this term
     */
    protected Term(Class<R> clazz, Map<String, Range<RealType>> inputRanges, String... variableNames) {
        this(clazz, variableNames);
        // only copy the ranges that apply
        varNames.forEach(varName -> rangeMap.put(varName, inputRanges.get(varName)));
    }

    @Override
    public abstract R apply(ArgVector<T> arguments);

    @Override
    public <V> Function<V, R> compose(Function<? super V, ? extends ArgVector<T>> before) {
        Logger.getLogger(Term.class.getName()).log(Level.INFO,
                "Term({0}) composing with before = {1}",
                new Object[] { varNames, before });
        // this may need to be enhanced or tracked in the future
        return super.compose(before);
    }

    @Override
    public <V> Function<ArgVector<T>, V> andThen(Function<? super R, ? extends V> after) {
        Logger.getLogger(Term.class.getName()).log(Level.INFO,
                "Term({0}) composing with after = {1}",
                new Object[] { varNames, after });
        // another candidate for enhancement or further tracking
        return super.andThen(after);
    }

    /**
     * Compute the product of two terms.
     * @param multiplier the term by which to multiply {@code this}
     * @return the product of {@code this} and {@code multiplier}
     */
    public abstract Term<T, R> multiply(Term<T, R> multiplier);

    /**
     * Compute the product of this term and a power function.
     * @param multiplier the function by which to multiply {@code this}
     * @return the product of {@code this} and {@code multiplier}
     */
    public abstract Term<T, R> multiply(Pow<T, R> multiplier);

    /**
     * Scale this term by the given value.
     * @param multiplier the value by which to multiply {@code this}
     * @return a new term that is {@code this} scaled by {@code multiplier}
     */
    public abstract Term<T, R> scale(R multiplier);

    /**
     * Determines whether this term is a constant term.
     * @return true if this term is constant, false otherwise
     */
    public boolean isConstant() {
        return arity() == 0L;
    }

    /**
     * Obtain the numeric coefficient of this term.
     * @return the coefficient
     */
    public abstract R coefficient();

    @Override
    public long arity() {
        return varNames.size();
    }

    /**
     * Obtain the polynomial order of this term in the variable
     * denoted by {@code varName}.
     *
     * @param varName the name of the variable
     * @return the polynomial order of this term
     */
    public abstract long order(String varName);

    @Override
    public String[] expectedArguments() {
        return varNames.toArray(String[]::new);
    }

    @Override
    public Range<RealType> inputRange(String argName) {
        return rangeMap.get(argName);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Term<?, ?> term)) return false;
        return rangeMap.equals(term.rangeMap) && varNames.equals(term.varNames);
    }

    @Override
    public int hashCode() {
        return Objects.hash(rangeMap, varNames);
    }
}
