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
 * A specialization of {@link Coordinates} for a 3-dimensional
 * datum, consisting of two independent variables and
 * a value (dependent variable).
 */
public class Coordinates3D extends Coordinates {
    /**
     * Construct a datum consisting of independent variables x and y and
     * a dependent variable z.
     * @param x the first independent variable
     * @param y the second independent variable
     * @param z the dependent variable or value of this datum
     */
    public Coordinates3D(RealType x, RealType y, RealType z) {
        inputs = new RealType[2];
        inputs[0] = x;
        inputs[1] = y;
        value = z;
    }

    /**
     * Construct a datum consisting of independent variables x and y and
     * a dependent variable z, as well as a relative error (typically a standard deviation).
     * @param x             the first independent variable
     * @param y             the second independent variable
     * @param z             the dependent variable or value of this datum
     * @param relativeError the relative error in {@code z}, e.g. a standard deviation
     */
    public Coordinates3D(RealType x, RealType y, RealType z, RealType relativeError) {
        this(x, y, z);
        highError = relativeError;  // error is symmetric
    }

    public RealType getX() {
        return inputs[0];
    }

    public RealType getY() {
        return inputs[1];
    }

    public RealType getZ() {
        return getValue();
    }

    /**
     * Obtain a comparator that compares {@link Coordinates3D} or an equivalent by
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
                return sortableBy(1);
            case Z_AXIS:
                return sortableByValue();
            default:
                throw new IllegalArgumentException("Coordinates in 3 dimensions not applicable to " + dimension);
        }
    }

    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder();
        buf.append('(').append("x:").append(getX())
                .append(", ").append("y:").append(getY())
                .append(", ").append("z:").append(getZ());
        if (lowError == null && highError != null) {
            // only bother showing symmetric error
            buf.append("\u2009\u00B1\u2009").append(highError);
        }
        buf.append(')');
        return buf.toString();
    }
}
