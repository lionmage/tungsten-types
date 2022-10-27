package tungsten.types.functions;

import tungsten.types.Numeric;
import tungsten.types.Range;
import tungsten.types.numerics.RealType;
import tungsten.types.util.ClassTools;

import java.util.*;
import java.util.function.Function;

public abstract class Term<T extends Numeric, R extends Numeric> extends NumericFunction<T, R> {
    private final Map<String, Range<RealType>> rangeMap = new TreeMap<>();
    protected final List<String> varNames;

    protected Term() {
        varNames = Collections.emptyList();
    }

    protected Term(Collection<String> variableNames) {
        varNames = new ArrayList<>(variableNames);
    }

    protected Term(Collection<String> variableNames, Map<String, Range<RealType>> inputRanges) {
        this(variableNames);
        varNames.stream().forEach(varName -> rangeMap.put(varName, inputRanges.get(varName)));
    }

    protected Term(String... variableNames) {
        final List<String> vars = Arrays.asList(variableNames);
        HashSet<String> tester = new HashSet<>();
        if (vars.stream().map(tester::add).anyMatch(b -> b == Boolean.FALSE)) {
            throw new IllegalArgumentException("Argument names must be unique");
        }
        this.varNames = vars;
    }

    protected Term(Map<String, Range<RealType>> inputRanges, String... variableNames) {
        this(variableNames);
        // only copy the ranges that apply
        varNames.stream().forEach(varName -> rangeMap.put(varName, inputRanges.get(varName)));
    }

    @Override
    public abstract R apply(ArgVector<T> arguments);

    @Override
    public <V> Function<V, R> compose(Function<? super V, ? extends ArgVector<T>> before) {
        // TODO enhance or track this somehow
        return super.compose(before);
    }

    @Override
    public <V> Function<ArgVector<T>, V> andThen(Function<? super R, ? extends V> after) {
        // TODO enhance or track this somehow
        return super.andThen(after);
    }

    public abstract Term<T, R> multiply(Term<T, R> multiplier);

    public boolean isConstant() {
        return arity() == 0L;
    }

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
        if (o == null || getClass() != o.getClass()) return false;
        Term<?, ?> term = (Term<?, ?>) o;
        return rangeMap.equals(term.rangeMap) && varNames.equals(term.varNames);
    }

    @Override
    public int hashCode() {
        return Objects.hash(rangeMap, varNames);
    }

    protected Class<R> getReturnClass() {
        List<Class<?>> argClasses = ClassTools.getTypeArguments(NumericFunction.class, this.getClass());
        return (Class<R>) argClasses.get(1);
    }
}
