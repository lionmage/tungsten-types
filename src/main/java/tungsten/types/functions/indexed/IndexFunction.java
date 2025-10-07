/*
 * The MIT License
 *
 * Copyright © 2025 Robert Poole <Tarquin.AZ@gmail.com>.
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
 *
 */

package tungsten.types.functions.indexed;

import tungsten.types.Numeric;
import tungsten.types.numerics.IntegerType;
import tungsten.types.Range;
import tungsten.types.functions.ArgVector;
import tungsten.types.functions.UnaryFunction;
import tungsten.types.numerics.RealType;
import tungsten.types.util.RangeUtils;

/**
 * A function that takes an integer argument and computes a value of any {@code Numeric}
 * subtype.  Subclasses should override the {@link #compute(IntegerType)} and {@code toString()}
 * methods.  This class is especially suited for use with anonymous subclasses.
 * @param <R> the return type of this function
 * @author Robert Poole, <a href="mailto:tarquin@alum.mit.edu">MIT alumni e-mail</a> or
 *   <a href="mailto:Tarquin.AZ+Tungsten@gmail.com">Gmail</a>
 * @since 0.8
 */
public abstract class IndexFunction<R extends Numeric> extends UnaryFunction<IntegerType, R> {
    /**
     * Instantiate an index function for a given index variable name and a
     * given return type.
     * @param indexVariable the name of the index variable
     * @param rtnClass      the return type, any subclass or subinterface of {@code Numeric}
     */
    public IndexFunction(String indexVariable, Class<R> rtnClass) {
        super(indexVariable, rtnClass);
    }

    /**
     * Instantiate an index function with an index variable named &ldquo;k&rdquo;
     * and a given return type.
     * @param rtnClass the return type
     */
    public IndexFunction(Class<R> rtnClass) {
        super("k", rtnClass);
    }

    @Override
    public R apply(ArgVector<IntegerType> arguments) {
        if (!arguments.hasVariableName(getArgumentName()) && arguments.length() > 1L) {
            throw new IllegalArgumentException("Expected an arg vector containing a single index");
        }
        IntegerType arg = arguments.hasVariableName(getArgumentName()) ?
                arguments.forVariableName(getArgumentName()) : arguments.elementAt(0L);
        return compute(arg);
    }

    @Override
    public Range<RealType> inputRange(String argName) {
        if (argName.equals(getArgumentName())) {
            return RangeUtils.ALL_REALS;
        }
        return null;
    }

    @Override
    public Class<IntegerType> getArgumentType() {
        return IntegerType.class;
    }

    @Override
    public R apply(IntegerType argument) {
        return compute(argument);
    }

    /**
     * The method which actually computes this term for
     * the given index value.
     * @param index the index to use
     * @return the value computed for {@code index}
     */
    protected abstract R compute(IntegerType index);
}
