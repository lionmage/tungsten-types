/*
 * The MIT License
 *
 * Copyright © 2019 Robert Poole <Tarquin.AZ@gmail.com>.
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
package tungsten.types.annotations;

import tungsten.types.Numeric;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.math.MathContext;

/**
 * Annotation for factory methods of classes annotated with {@link Constant}.
 *
 * @author Robert Poole <Tarquin.AZ@gmail.com>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface ConstantFactory {
    /**
     * Most constant factory methods take a single argument,
     * and for most of those it's a {@link MathContext}.  For
     * these cases, this annotation value will suffice &mdash; and
     * it defaults to {@code MathContext.class} for convenience.
     * @return the {@link Class} describing the single argument of this annotated factory method
     */
    Class<?> argType() default MathContext.class;

    /**
     * An optional array of argument types for when the factory method being annotated
     * has more than one argument.  If specified, this value is used in preference
     * to {@link #argType()}.  The ordering of the array must match the order in
     * which method parameters are encountered, left-to-right.
     * @return the array of {@link Class}es describing the arguments of this annotated factory method in proper order
     */
    Class<?>[] argTypes() default {}; // note: clients/processors will need to test for arrayLength > 0

    /**
     * This can be used to report back the concrete type expected from
     * the factory class, which may be a subtype of the declared return type.
     * @return the {@link Numeric} subinterface or concrete type of the return value
     */
    Class<? extends Numeric> returnType() default Numeric.class;
}
