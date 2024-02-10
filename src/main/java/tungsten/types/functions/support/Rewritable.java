package tungsten.types.functions.support;
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
import tungsten.types.functions.UnaryFunction;

/**
 * An interface for {@link UnaryFunction}s that allows them
 * to be rewritten in terms of a different variable name.
 */
public interface Rewritable {
    /**
     * This method takes a new argument name as its parameter and returns
     * an instance of {@link UnaryFunction} which performs the same function
     * as this (the original) function, rewritten in terms of the new variable
     * name.<br>
     * It is strongly encouraged that implementing classes take advantage of
     * covariant return types to make the return signature as specific as possible.
     * @param argName the new variable name
     * @return this function, rewritten in terms of {@code argName}
     */
    UnaryFunction<? extends Numeric, ? extends Numeric> forArgName(String argName);
}
