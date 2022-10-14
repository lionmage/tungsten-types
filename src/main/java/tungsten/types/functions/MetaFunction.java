package tungsten.types.functions;

import tungsten.types.Numeric;
import tungsten.types.Range;
import tungsten.types.numerics.RealType;

import java.util.*;
import java.util.function.Function;

public abstract class MetaFunction<T extends Numeric, R extends Numeric, R2 extends Numeric>
        implements Function<NumericFunction<T, R>, NumericFunction<T, R2>> {
    private ArgMap<T> curryMap = new ArgMap<>();

    protected MetaFunction() {
        // default, don't do much for now
    }//        UnaryFunction<T, T> numFunc = new Sum<>()


    protected MetaFunction(Map<String, T> sourceArgs) {
        curryMap.putAll(sourceArgs);
    }

    @Override
    public abstract NumericFunction<T, R2> apply(NumericFunction<T, R> inputFunction);

    public void setCurryMapping(String varName, T value) {
        curryMap.put(varName, value);
    }

    public void clearCurryMappings() {
        curryMap.clear();
    }

    public boolean containsCurryMapping(String varName) {
        return curryMap.containsKey(varName);
    }

    public void retainOnly(String[] argNames) {
        final Set<String> args = new TreeSet<>();
        Collections.addAll(args, argNames);
        curryMap.keySet().removeIf(name -> !args.contains(name));
    }

    protected NumericFunction<T, R> curry(NumericFunction<T, R> inputFunction) {
        final ArgMap<T> argsCopy = new ArgMap<>(curryMap);
        final String[] argNames = inputFunction.expectedArguments();
        List<String> argList = Arrays.asList(argNames);
        if (inputFunction.arity() == curryMap.size() + 1L) {
            // only one argument left, so return a UnaryFunction
            final String varName = curryMap.keySet().stream().filter(n -> !argList.contains(n))
                    .findFirst().orElseThrow();
            return new UnaryFunction<T, R>(varName) {
                @Override
                public R apply(ArgVector<T> arguments) {
                    if (!arguments.hasVariableName(varName)) throw new ArithmeticException("Argument not found: " + varName);
                    argsCopy.put(varName, arguments.forVariableName(varName));
                    ArgVector<T> allArgs = new ArgVector<>(inputFunction.expectedArguments(), argsCopy);
                    return inputFunction.apply(allArgs);
                }

                @Override
                public Range<RealType> inputRange(String argName) {
                    if (varName.equals(argName)) {
                        return inputFunction.inputRange(varName);
                    }
                    return null;
                }
            };
        } else {
            return new NumericFunction<T, R>() {
                @Override
                public R apply(ArgVector<T> arguments) {
                    for (String varName : this.expectedArguments()) {
                        argsCopy.put(varName, arguments.forVariableName(varName));
                    }
                    ArgVector<T> allArgs = new ArgVector<>(argNames, argsCopy);
                    return inputFunction.apply(allArgs);
                }

                private final long numCurriedArgs = curryMap.size();

                @Override
                public long arity() {
                    return inputFunction.arity() - numCurriedArgs;
                }

                private final String[] requiredArgs = Arrays.stream(argNames)
                        .filter(argName -> !curryMap.containsKey(argName)).toArray(String[]::new);

                @Override
                public String[] expectedArguments() {
                    return requiredArgs;
                }

                @Override
                public Range<RealType> inputRange(String argName) {
                    return inputFunction.inputRange(argName);
                }
            };
        }
    }
}
