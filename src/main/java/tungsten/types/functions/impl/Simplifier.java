package tungsten.types.functions.impl;
/*
 * The MIT License
 *
 * Copyright © 2023 Robert Poole <Tarquin.AZ@gmail.com>.
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

import tungsten.types.functions.MetaFunction;
import tungsten.types.functions.NumericFunction;
import tungsten.types.functions.UnaryFunction;
import tungsten.types.functions.support.Simplifiable;
import tungsten.types.numerics.RealType;

import java.util.Optional;

/**
 * A metafunction which recursively traverses the object graph of a function
 * and attempts to simplify that function, either by reducing the size of its
 * object graph, or by rearranging terms for greater computational efficiency.
 * Any function that implements the {@link Simplifiable} interface is
 * automatically eligible for processing.  Other functional patterns may
 * be inferred from the object graph in order to produce additional
 * optimizations. If no transformation is possible, the original function
 * will be returned unaltered.
 * @param <T> the argument and return type of the function being transformed
 * @author Robert Poole, <a href="mailto:tarquin@alum.mit.edu">MIT alumni email</a>
 *   or <a href="mailto:Tarquin.AZ@gmail.com">Gmail</a>
 */
public class Simplifier<T extends RealType> extends MetaFunction<T, T, T> {
    @Override
    public UnaryFunction<T, T> apply(NumericFunction<T, T> inputFunction) {
        final UnaryFunction<T, T> original;
        if (!(inputFunction instanceof UnaryFunction)) {
            // attempt to curry the function so that we only have one variable left
            NumericFunction<T, T> intermediate = curry(inputFunction);
            if (intermediate.arity() != 1L) {
                throw new IllegalStateException("Insufficient curry mappings to reduce argument list; " +
                        "expected a unary function but produced a function with arity " + intermediate.arity());
            }
            original = (UnaryFunction<T, T>) intermediate;
        } else {
            original = (UnaryFunction<T, T>) inputFunction;
        }
        // do a depth-first traversal if possible
        Optional<UnaryFunction<T, T>> core = original.getOriginalFunction();
        if (core.isPresent()) {
            Optional<UnaryFunction<T, T>> inner = core.flatMap(UnaryFunction::getComposedFunction).map(f -> apply((UnaryFunction<T, T>) f));
            UnaryFunction<T, T> simplified = core.get();
            if (simplified instanceof Simplifiable) {
                simplified = (UnaryFunction<T, T>) ((Simplifiable) simplified).simplify();
            }
            if (inner.isPresent()) {
                // inner composition will take care of some additional optimizations
                return (UnaryFunction<T, T>) simplified.composeWith(inner.get());
            }
            Optional<UnaryFunction<T, T>> outer = core.flatMap(UnaryFunction::getComposingFunction).map(f -> apply((UnaryFunction<T, T>) f));
            if (outer.isPresent()) {
                // outer composition will take care of some additional optimizations
                return simplified.andThen(outer.get());
            }
            return simplified;
        }
        // otherwise, attempt to simplify this function as-is
        if (original instanceof Simplifiable) {
            return (UnaryFunction<T, T>) ((Simplifiable) original).simplify();
        }

        // fall through
        return original;
    }
}
