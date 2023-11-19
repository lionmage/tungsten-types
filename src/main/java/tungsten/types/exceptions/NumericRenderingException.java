package tungsten.types.exceptions;
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

import tungsten.types.Numeric;

/**
 * An exception thrown when rendering a numeric value, either stand-alone
 * or as part of a multi-valued structure such as a matrix or vector.
 * @see tungsten.types.util.UnicodeTextEffects
 * @author Robert Poole, <a href="mailto:Tarquin.AZ@gmail.com">Tarquin.AZ@gmail.com</a>
 */
public class NumericRenderingException extends RuntimeException {
    private final Numeric original;

    /**
     * The sole constructor for this exception.
     * @param message a mandatory message describing the problem encountered
     * @param value   the value that was being rendered when the exception occurred
     */
    public NumericRenderingException(String message, Numeric value) {
        super(message);
        original = value;
    }

    /**
     * Obtain the value that was being rendered at the time of the exception.
     * @return a {@link Numeric} value, or that of one of its subclasses
     */
    public Numeric getOriginalValue() {
        return original;
    }
}
