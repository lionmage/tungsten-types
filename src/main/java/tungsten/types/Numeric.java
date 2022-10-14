package tungsten.types;/*
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

import tungsten.types.exceptions.CoercionException;

import java.math.MathContext;

/**
 * Root interface for all numeric types.
 *
 * @author tarquin
 */
public interface Numeric {
    public boolean isExact();
    public boolean isCoercibleTo(Class<? extends Numeric> numtype);
    default public Numeric coerceTo(Class<? extends Numeric> numtype) throws CoercionException {
        if (numtype == Numeric.class) return this;
        throw new CoercionException("coerceTo() not implemented for " +
                numtype.getTypeName() + " in class " + this.getClass().getName(),
                this.getClass(), numtype);
    };
    public Numeric magnitude();
    public Numeric negate();
    
    public Numeric add(Numeric addend);
    public Numeric subtract(Numeric subtrahend);
    public Numeric multiply(Numeric multiplier);
    public Numeric divide(Numeric divisor);
    public Numeric inverse();
    public Numeric sqrt();
    
    public MathContext getMathContext();
}
