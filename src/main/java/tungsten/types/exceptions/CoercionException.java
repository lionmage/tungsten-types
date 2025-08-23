/* 
 * The MIT License
 *
 * Copyright © 2018 Robert Poole <Tarquin.AZ@gmail.com>.
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
package tungsten.types.exceptions;

import tungsten.types.Numeric;

/**
 * This exception is intended to be thrown when a call to
 * {@link Numeric#coerceTo(Class)} or a similar method fails.
 * It is constructed with information about the
 * class of the object being coerced as well as the class
 * to which the coercion was attempted.
 *
 * @author Robert Poole, <a href="mailto:Tarquin.AZ@gmail.com">Tarquin.AZ@gmail.com</a>
 */
public class CoercionException extends Exception {
    private final Class<? extends Numeric> sourceType;
    private final Class<? extends Numeric> desiredType;

    /**
     * Construct a new {@code CoercionException} with a message as well as
     * the source and target types.
     * @param message the human-readable message associated with this exception
     * @param source  the source type of the {@code Numeric} being coerced
     * @param target  the target type of the coercion
     */
    public CoercionException(String message,
                             Class<? extends Numeric> source, Class<? extends Numeric> target) {
        super(message);
        sourceType = source;
        desiredType = target;
    }

    /**
     * Obtain the source type of the {@code Numeric} being coerced.
     * @return the source type
     */
    public Class<? extends Numeric> getSourceType() {
        return sourceType;
    }

    /**
     * Obtain the target type of the coercion that triggered this exception.
     * @return the target type
     */
    public Class<? extends Numeric> getTargetType() {
        return desiredType;
    }
}
