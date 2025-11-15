package tungsten.types.functions;
/*
 * The MIT License
 *
 * Copyright © 2022 Robert Poole <Tarquin.AZ@gmail.com>.
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
import tungsten.types.Range;
import tungsten.types.numerics.RealType;

import java.util.*;
import java.util.function.Function;

/**
 * A basic implementation of a metafunction, which takes a function as an argument
 * and returns another, transformed function.
 *
 * @param <T>  the input parameter type
 * @param <R>  the return type of the original function
 * @param <R2> the return type of the generated function
 * @author Robert Poole, <a href="mailto:Tarquin.AZ@gmail.com">Gmail</a>
 */
public abstract class MetaFunction<T extends Numeric, R extends Numeric, R2 extends Numeric>
        implements Function<NumericFunction<T, R>, NumericFunction<T, R2>> {
    private final ArgMap<T> curryMap = new ArgMap<>();

    /**
     * Default no-args constructor, intended for use by subclasses.
     */
    protected MetaFunction() {
        // default, don't do much for now
    }

    /**
     * Constructor which takes a mapping from argument names
     * to values.
     * @param sourceArgs a {@code Map} of argument names to values
     */
    protected MetaFunction(Map<String, T> sourceArgs) {
        curryMap.putAll(sourceArgs);
    }

    @Override
    public abstract NumericFunction<T, R2> apply(NumericFunction<T, R> inputFunction);

    /**
     * Add a single curry mapping, a mapping from a variable name
     * to a value.
     * @param varName the name of the variable to map
     * @param value   the mapped value for this variable
     */
    public void setCurryMapping(String varName, T value) {
        curryMap.put(varName, value);
    }

    /**
     * Remove all variable name to value mappings.
     */
    public void clearCurryMappings() {
        curryMap.clear();
    }

    /**
     * Bulk-add mappings from variable names to values.
     * @param mappings a {@code Map} containing a mapping of variable names to values
     * @throws IllegalArgumentException if {@code mappings} contains any keys
     *   that match an existing variable name mapping
     */
    public void addCurryMappings(Map<String, T> mappings) {
        if (mappings.keySet().stream().anyMatch(curryMap::containsKey)) {
            throw new IllegalArgumentException("New curry mappings conflict with existing mappings");
        }
        curryMap.putAll(mappings);
    }

    /**
     * Determine if a curry mapping exists for the given variable name.
     * @param varName the variable name
     * @return true if a mapping exists for {@code varName}, false otherwise
     */
    public boolean containsCurryMapping(String varName) {
        return curryMap.containsKey(varName);
    }

    /**
     * Obtain an unmodifiable map of argument names to values.
     * @return an unmodifiable map of argument names to values
     */
    protected ArgMap<T> mappedArgsView() { return new ArgMap<>(Collections.unmodifiableMap(curryMap)); }

    /**
     * Only retain the argument name to value mappings for the given
     * array of argument names.
     * @param argNames an array of argument names
     */
    public void retainOnly(String[] argNames) {
        final Set<String> args = new TreeSet<>();
        Collections.addAll(args, argNames);
        curryMap.keySet().removeIf(name -> !args.contains(name));
    }

    /**
     * Given an input function, apply curry mappings to generate a new
     * function with fewer free variables (arguments).
     * @param inputFunction any {@code NumericFunction} of <em>N</em> arguments
     * @return a reduced function with &lt;&nbsp;<em>N</em> arguments
     */
    protected NumericFunction<T, R> curry(NumericFunction<T, R> inputFunction) {
        final ArgMap<T> argsCopy = new ArgMap<>(curryMap);
        final String[] argNames = inputFunction.expectedArguments();
        List<String> argList = Arrays.asList(argNames);
        if (inputFunction.arity() == curryMap.size() + 1L) {
            // only one argument left, so return a UnaryFunction
            final String varName = argList.stream().filter(argName -> !curryMap.containsKey(argName))
                    .findFirst().orElseThrow();
            return new UnaryFunction<>(varName, inputFunction.getReturnType()) {
                @Override
                public R apply(ArgVector<T> arguments) {
                    if (!arguments.hasVariableName(varName))
                        throw new ArithmeticException("Argument not found: " + varName);
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

                @Override
                public Class<T> getArgumentType() {
                    return inputFunction.getArgumentType();
                }
            };
        } else {
            return new NumericFunction<>(inputFunction.getReturnType()) {
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
                    if (Arrays.asList(requiredArgs).contains(argName)) {
                        return inputFunction.inputRange(argName);
                    }
                    return null;
                }

                @Override
                public Class<T> getArgumentType() {
                    return inputFunction.getArgumentType();
                }
            };
        }
    }
}
