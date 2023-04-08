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
package tungsten.types.numerics;

import tungsten.types.Numeric;
import tungsten.types.Set;
import tungsten.types.util.MathUtils;

/**
 * Interface for all implementations of complex numbers.
 *
 * @author Robert Poole
 */
public interface ComplexType extends Numeric {
    String ENABLE_EXTENDED_CPLX = "tungsten.types.numerics.ComplexType.extended.enable";
    @SuppressWarnings("unchecked")
    @Override
    RealType magnitude();
    @Override
    ComplexType negate();
    @Override
    ComplexType inverse();
    ComplexType conjugate();
    RealType real();
    RealType imaginary();
    /**
     * In polar form, the argument of a complex
     * number is the angle with respect to the positive
     * real axis.  The corresponding value, the
     * modulus, is given by {@link #magnitude() }.
     * 
     * @return the argument of the polar form of this complex number
     */
    RealType argument();
    Set<ComplexType> nthRoots(IntegerType n);

    /**
     * Method to determine if the extended complex plane &#x2102;<sub>&infin;</sub>
     * is enabled.
     * @return true if enabled, false otherwise
     */
    static boolean isExtendedEnabled() {
        return Boolean.getBoolean(ENABLE_EXTENDED_CPLX);
    }

    /*
    Methods necessary for Groovy operator overloading follow.
     */
    default ComplexType power(Numeric operand) {
        return MathUtils.generalizedExponent(this, operand, getMathContext());
    }
}
