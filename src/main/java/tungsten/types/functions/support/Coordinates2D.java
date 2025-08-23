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

/**
 * A specialization of {@link Coordinates} for a 2-dimensional
 * datum, consisting of a single independent variable and
 * a value (dependent variable).
 */
public class Coordinates2D extends Coordinates {
    /**
     * Constructor that takes a real-valued constraint
     * and a real value.
     * @param x the constraint or independent variable
     * @param y the value or dependent variable
     */
    public Coordinates2D(RealType x, RealType y) {
        inputs = new RealType[1];
        inputs[0] = x;
        value = y;
    }

    /**
     * Constructor that takes a real-valued constraint
     * and a real value, as well as an error value (typically a standard deviation).
     * @param x             the constraint or independent variable
     * @param y             the value or dependent variable
     * @param relativeError the error associated with {@code y}, e.g. a standard deviation
     */
    public Coordinates2D(RealType x, RealType y, RealType relativeError) {
        this(x, y);
        highError = relativeError;  // error is symmetric
    }

    /**
     * Convenience method to obtain the sole independent variable.
     * @return the x value of this datum
     */
    public RealType getX() {
        return inputs[0];
    }

    /**
     * Convenience method to obtain the dependent variable.
     * @return the y value of this datum
     */
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
