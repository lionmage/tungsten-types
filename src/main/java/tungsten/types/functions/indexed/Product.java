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
import tungsten.types.numerics.impl.IntegerImpl;
import tungsten.types.numerics.impl.One;
import tungsten.types.util.OptionalOperations;
import tungsten.types.util.UnicodeTextEffects;

import java.math.BigInteger;
import java.math.MathContext;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The product of one or more indexed functions.  Not to be confused with
 * {@link tungsten.types.functions.impl.Product}, which is a product
 * of arbitrary functions.
 * @param <R> the return type of this product
 * @author Robert Poole, <a href="mailto:tarquin@alum.mit.edu">MIT alumni e-mail</a> or
 *   <a href="mailto:Tarquin.AZ+Tungsten@gmail.com">Gmail</a>
 * @since 0.8
 */
public class Product<R extends Numeric> {
    private final IndexFunction<R> function;
    private MathContext mctx = MathContext.DECIMAL128;

    /**
     * Generate a product using the given indexed function.
     * @param func the indexed function
     */
    public Product(IndexFunction<R> func) {
        function = func;
    }

    /**
     * Generate a product using the given indexed function and
     * {@code MathContext}.
     * @param func the indexed function
     * @param mctx the math context to use
     */
    public Product(IndexFunction<R> func, MathContext mctx) {
        this(func);
        this.mctx = mctx;
    }

    private static final IntegerType PARALLEL_THRESHOLD = new IntegerImpl(BigInteger.valueOf(1000L));

    /**
     * Evaluate this product over the given range of index values.
     * @param range the range of indices
     * @return the resulting product
     */
    public R evaluate(IndexRange range) {
        final Class<R> clazz = function.getReturnType();
        IntegerType delta = (IntegerType) range.getUpperBound().subtract(range.getLowerBound());
        if (delta.compareTo(PARALLEL_THRESHOLD) > 0) {
            return fastEval(range, clazz);
        }

        Numeric accum = One.getInstance(mctx);

        for (IntegerType index : range) {
            accum = accum.multiply(function.compute(index));
        }
        try {
            OptionalOperations.setMathContext(accum, mctx);
            return (R) accum.coerceTo(clazz);
        } catch (CoercionException e) {
            Logger.getLogger(Product.class.getName()).log(Level.SEVERE,
                    "Return type should be closed under multiplication, but is not.", e);
            throw new ArithmeticException("Failed to convert product to " + clazz.getTypeName());
        }
    }

    private R fastEval(IndexRange range, Class<R> clazz) {
        try {
            R result = (R) range.parallelStream().map(function::compute)
                    .map(Numeric.class::cast)
                    .reduce(One.getInstance(mctx), Numeric::multiply)
                    .coerceTo(clazz);
            OptionalOperations.setMathContext(result, mctx);
            return result;
        } catch (CoercionException e) {
            throw new IllegalStateException("Failed to convert product", e);
        }
    }

    /**
     * Set the {@code MathContext} for this product.
     * @param ctx the new math context to use
     */
    public void setMathContext(MathContext ctx) {
        this.mctx = ctx;
    }

    @Override
    public String toString() {
        // U+220F is the N-ary product
        return "\u220F" + UnicodeTextEffects.convertToSubscript(function.expectedArguments()[0])
                + "\u202F" + function; // U+202F is a narrow non-breaking space
    }

    /**
     * Render this product for a given range of indices.
     * @param range the range over which we wish to render the product expression
     * @return a string representing the product over the given range
     */
    public String toStringWithBounds(IndexRange range) {
        StringBuilder buf = new StringBuilder();
        // Note: there is no multi-line version of the Unicode N-ary product, so use a curly brace to help.
        buf.append(" \u23A7")  // U+23A7 is the upper left curly brace hook
                .append(UnicodeTextEffects.numericSuperscript(range.getUpperBound().asBigInteger().intValueExact()))
                .append('\n');
        buf.append("\u220F\u23A8")  // U+220F is the N-ary product symbol, U+23A8 is the middle piece of a left curly brace
                .append(function).append('\n');
        buf.append(" \u23A9")  // U+23A9 is the lower left curly brace hook
                .append(UnicodeTextEffects.convertToSubscript(function.expectedArguments()[0] + "="))
                .append(UnicodeTextEffects.numericSubscript(range.getLowerBound().asBigInteger().intValueExact()));
        return buf.toString();
    }
}
