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
package tungsten.types.vector.impl;

import tungsten.types.numerics.ComplexType;
import tungsten.types.numerics.RealType;
import tungsten.types.numerics.impl.ComplexRectImpl;
import tungsten.types.numerics.impl.RealImpl;

import java.math.BigDecimal;

/**
 * Package private class encapsulating a mapping from an index to a coefficient.
 * This is intended for doing lookups of vector indices for things like cross
 * products.
 *
 * @author Robert Poole <a href="mailto:Tarquin.AZ@gmail.com">Tarquin.AZ@gmail.com</a>
 */
record TableElement(int index, RealType coeff) {
    private static final RealType ZERO = new RealImpl(BigDecimal.ZERO);

    TableElement(int index, int coeff) {
        this(index, switch (coeff) {
            case 0 -> new RealImpl(BigDecimal.ZERO);
            case 1 -> new RealImpl(BigDecimal.ONE);
            case -1 -> new RealImpl(BigDecimal.ONE.negate());
            default -> throw new IllegalArgumentException("Only allowed coefficients are -1, 0, or 1");
        });
    }

    public ComplexType getCplxCoeff() {
        return new ComplexRectImpl(coeff, ZERO);
    }

    public long getIndex() {
        return index;
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof TableElement that) {
            // we're only comparing the index because different instances
            // may have different coefficients depending on where they're stored
            // in our table, but we still need to match only on the index
            return this.index == that.index;
        }
        return false;
    }
}
