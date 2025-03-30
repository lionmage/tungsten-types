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

import tungsten.types.Axis;
import tungsten.types.numerics.RealType;

import java.util.Comparator;

public class Coordinates2D extends Coordinates {
    public Coordinates2D(RealType x, RealType y) {
        inputs = new RealType[1];
        inputs[0] = x;
        value = y;
    }

    public Coordinates2D(RealType x, RealType y, RealType relativeError) {
        this(x, y);
        highError = relativeError;  // error is symmetric
    }

    public RealType getX() {
        return inputs[0];
    }

    public RealType getY() {
        return getValue();
    }

    /**
     * Obtain a comparator that compares {@link Coordinates2D} or an equivalent by
     * the ordinate that correlates with the given {@link Axis}.<br>
     * Note that the return type's parameter is {@code Coordinates} because
     * Java's support for covariant return types does not extend to type parameters.
     * @param dimension the ordinate axis
     * @return a comparator that compares by the ordinate that corresponds to {@code dimension}
     */
    public static Comparator<Coordinates> sortableBy(Axis dimension) {
        switch (dimension) {
            case X_AXIS:
                return sortableBy(0);
            case Y_AXIS:
                return sortableByValue();
            case Z_AXIS:
            default:
                throw new IllegalArgumentException("Coordinates in 2 dimensions not applicable to " + dimension);
        }
    }

    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder();
        buf.append('(').append("x:").append(getX())
                .append(", ").append("y:").append(getY());
        if (lowError == null && highError != null) {
            // only bother showing symmetric error
            buf.append("\u2009\u00B1\u2009").append(highError);
        }
        buf.append(')');
        return buf.toString();
    }
}
