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
import tungsten.types.exceptions.CoercionException;
import tungsten.types.numerics.IntegerType;
import tungsten.types.numerics.impl.ExactZero;
import tungsten.types.numerics.impl.IntegerImpl;
import tungsten.types.util.OptionalOperations;
import tungsten.types.util.UnicodeTextEffects;

import java.math.BigInteger;
import java.math.MathContext;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The summation of one or more indexed functions.
 * @param <R> the return type of this sum
 * @author Robert Poole, <a href="mailto:tarquin@alum.mit.edu">MIT alumni e-mail</a> or
 *   <a href="mailto:Tarquin.AZ+Tungsten@gmail.com">Gmail</a>
 * @since 0.8
 */
public class Summation<R extends Numeric> {
    private final IndexFunction<R> function;
    private MathContext mctx = MathContext.DECIMAL128;

    /**
     * Generate a summation using the given indexed function.
     * @param func the indexed function
     */
    public Summation(IndexFunction<R> func) {
        function = func;
    }

    /**
     * Generate a summation using the given indexed function and
     * {@code MathContext}.
     * @param func the indexed function
     * @param mctx the math context to use
     */
    public Summation(IndexFunction<R> func, MathContext mctx) {
        this(func);
        this.mctx = mctx;
    }

    private static final IntegerType PARALLEL_THRESHOLD = new IntegerImpl(BigInteger.valueOf(1000L));

    /**
     * Evaluate this sum over the given range of index values.
     * @param range the range of indices
     * @return the resulting sum
     */
    public R evaluate(IndexRange range) {
        final Class<R> clazz = function.getReturnType();
        IntegerType delta = (IntegerType) range.getUpperBound().subtract(range.getLowerBound());
        if (delta.compareTo(PARALLEL_THRESHOLD) > 0) {
            return fastEval(range, clazz);
        }

        Numeric accum = ExactZero.getInstance(mctx);

        for (IntegerType index : range) {
            accum = accum.add(function.compute(index));
        }
        try {
            OptionalOperations.setMathContext(accum, mctx);
            return (R) accum.coerceTo(clazz);
        } catch (CoercionException e) {
            Logger.getLogger(Summation.class.getName()).log(Level.SEVERE,
                    "Return type should be closed under addition, but is not.", e);
            throw new ArithmeticException("Failed to convert sum to " + clazz.getTypeName());
        }
    }

    private R fastEval(IndexRange range, Class<R> clazz) {
        try {
            R result = (R) range.parallelStream().map(function::compute)
                    .map(Numeric.class::cast)
                    .reduce(ExactZero.getInstance(mctx), Numeric::add)
                    .coerceTo(clazz);
            OptionalOperations.setMathContext(result, mctx);
            return result;
        } catch (CoercionException e) {
            throw new IllegalStateException("Failed to convert sum", e);
        }
    }

    /**
     * Set the {@code MathContext} for this summation.
     * @param ctx the new math context to use
     */
    public void setMathContext(MathContext ctx) {
        this.mctx = ctx;
    }

    @Override
    public String toString() {
        // U+2211 is the N-ary summation
        return "\u2211" + UnicodeTextEffects.convertToSubscript(function.expectedArguments()[0])
                + "\u202F" + function;
    }

    /**
     * Render this summation for a given range of indices.
     * @param range the range over which we wish to render the summation expression
     * @return a string representing the summation over the given range
     */
    public String toStringWithBounds(IndexRange range) {
        StringBuilder buf = new StringBuilder();
        buf.append('\u23B2')  // U+23B2 is the upper part of a summation symbol
                .append(UnicodeTextEffects.numericSuperscript(range.getUpperBound().asBigInteger().intValueExact()))
                .append('\n');
        buf.append('\u23B3')  // U+23B3 is the lower part of a summation symbol
                .append(UnicodeTextEffects.convertToSubscript(function.expectedArguments()[0] + "="))
                .append(UnicodeTextEffects.numericSubscript(range.getLowerBound().asBigInteger().intValueExact()))
                .append(function);
        return buf.toString();
    }
}
