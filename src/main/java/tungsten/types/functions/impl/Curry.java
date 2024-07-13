package tungsten.types.functions.impl;
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
import tungsten.types.functions.ArgMap;
import tungsten.types.functions.MetaFunction;
import tungsten.types.functions.NumericFunction;
import tungsten.types.numerics.ComplexType;
import tungsten.types.numerics.RealType;

import java.util.Arrays;

import static tungsten.types.util.MathUtils.*;

/**
 * A simple meta-function which curries the inputs of a {@link NumericFunction} and
 * generates a new {@link NumericFunction} with the same input types and return type,
 * but with fewer declared arguments.  Functions with only one remaining argument
 * are returned as a {@link tungsten.types.functions.UnaryFunction}, while functions
 * with no remaining arguments are returned as a {@link Const}.
 *
 * @param <T> the input parameter type of both input and returned functions
 * @param <R> the return type of both input and returned functions
 */
public class Curry<T extends Numeric, R extends Numeric> extends MetaFunction<T, R, R> {
    private long numberOfMappings;

    public Curry(ArgMap<T> curriedArguments) {
        super(curriedArguments);
        numberOfMappings = curriedArguments.size();
    }

    public void insertOrUpdateCurryMapping(String varName, T value) {
        boolean update = super.containsCurryMapping(varName);
        super.setCurryMapping(varName, value);
        if (!update) numberOfMappings++;
    }

    @Override
    public NumericFunction<T, R> apply(NumericFunction<T, R> inputFunction) {
        final ArgMap<T> mappedArgs = mappedArgsView();
        // we must check that our curried values all fall within the input range of their respective argument
        if (! mappedArgs.keySet().stream()
                .allMatch(argName -> isInRange(inputFunction, mappedArgs, argName)) ) {
            String argName = mappedArgs.keySet().stream()
                    .filter(arg -> !isInRange(inputFunction, mappedArgs, arg))
                    .findFirst().orElseThrow();
            StringBuilder message = new StringBuilder();
            message.append("Variable ").append(argName).append(" has a curry value ").append(mappedArgs.get(argName));
            message.append(" that is out of range");
            // the range may be undefined for the root argument name, but it may be defined
            // for e.g. the real component of a complex argument, therefore we're only
            // bothering to report the range if we've found one for the root arg name
            if (inputFunction.inputRange(argName) != null) {
                message.append(' ').append(inputFunction.inputRange(argName));
            }
            throw new IllegalArgumentException(message.toString());
        }
        if (numberOfMappings >= inputFunction.arity() &&
                mappedArgs.keySet().containsAll(Arrays.asList(inputFunction.expectedArguments()))) {
            return Const.getInstance(inputFunction.apply(mappedArgs));
        }
        return curry(inputFunction);
    }

    private boolean isInRange(NumericFunction<T, R> function, ArgMap<T> mappedArgs, String argName) {
        Range<RealType> argRange = function.inputRange(argName);
        final T value = mappedArgs.get(argName);
        if (argRange == null && mappedArgs.get(argName) instanceof ComplexType) {
            // check for re, im, arg, or mag
            Range<RealType> reRange = function.inputRange(argName + ".re");
            Range<RealType> imRange = function.inputRange(argName + ".im");
            Range<RealType> magRange = function.inputRange(argName + ".mag");
            Range<RealType> cplxArgRange = function.inputRange(argName + ".arg"); // to avoid confusion with argRange above
            if (reRange != null && !reRange.contains(Re(value))) return false;
            if (imRange != null && !imRange.contains(Im(value))) return false;
            if (magRange != null && !magRange.contains(Re(value.magnitude()))) return false;
            return cplxArgRange == null || cplxArgRange.contains(Arg(value));
        }
        return argRange == null || argRange.contains(Re(value));
    }
}
