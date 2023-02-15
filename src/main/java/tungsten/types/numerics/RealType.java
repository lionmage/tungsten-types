/* 
 * The MIT License
 *
 * Copyright © 2018 Robert Poole <a href="mailto:Tarquin.AZ@gmail.com">Tarquin.AZ@gmail.com</a>.
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
package tungsten.types.numerics;

import tungsten.types.Numeric;
import tungsten.types.Set;
import tungsten.types.util.MathUtils;

import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * General interface for types representing real values.
 *
 * @author tarquin
 */
public interface RealType extends Numeric, Comparable<RealType> {
    boolean isIrrational();
    @Override
    RealType magnitude();
    @Override
    RealType negate();
    BigDecimal asBigDecimal();
    Sign sign();
    IntegerType floor();
    IntegerType ceil();
    Set<ComplexType> nthRoots(IntegerType n);

    /*
    Methods necessary for Groovy operator overloading follow.
     */
    default RealType power(Numeric operand) {
        return MathUtils.generalizedExponent(this, operand, getMathContext());
    }
    default RealType positive() {
        return magnitude();
    }
    default Object asType(Class<?> clazz) {
        if (BigDecimal.class.isAssignableFrom(clazz)) {
            return this.asBigDecimal();
        }
        if (BigInteger.class.isAssignableFrom(clazz)) {
            return this.asBigDecimal().toBigInteger();
        }
        return Numeric.super.asType(clazz);
    }
}
