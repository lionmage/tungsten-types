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
import tungsten.types.Numeric;
import tungsten.types.Range;
import tungsten.types.numerics.RealType;
import tungsten.types.numerics.Sign;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class Coordinates {
    private static final String MISMATCHED_ARITY = "Mismatched arity";
    protected RealType[] inputs;
    protected RealType value;
    /**
     * If {@code lowError == null}, this represents the symmetric error bounds
     * around {@code value}.  Otherwise, it represents the upper error bound
     * on {@code value}.
     */
    protected RealType highError;
    /**
     * If not {@code null}, this represents the lower error bound on {@code value}.
     */
    protected RealType lowError;

    /**
     * Constructor which takes a tuple of values as a {@link List}.
     * It is assumed that the final value of the tuple is the value
     * associated with the parameters or constraints specified before it.
     * For a function f(x<sub>0</sub>,&thinsp;x<sub>1</sub>,&thinsp;&hellip;), the first
     * n&nbsp;&minus;&nbsp;1 values of the tuple would represent the function's arguments,
     * while the n<sup>th</sup> value would represent the value of f()
     * evaluated at those arguments.  For experimental data,
     * the final value would represent some measurement taken with
     * respect to the parameters/constraints occurring before it.
     * @param coordinateValues a {@link List} of {@link RealType} values representing
     *                         a single set of coordinates
     */
    public Coordinates(List<RealType> coordinateValues) {
        inputs = coordinateValues.subList(0, coordinateValues.size() - 1)
                .toArray(RealType[]::new);
        value  = coordinateValues.get(coordinateValues.size() - 1);
    }

    protected Coordinates() {
        // this is intended for subclasses only
    }

    /**
     * Obtain the arity of this datum.  The arity is the number of constraints
     * or parameters, thus it does not include the value itself.
     * @return the number of constraints for this data point
     */
    public long arity() {
        return inputs.length;
    }

    /**
     * Obtain a comparator that compares {@link Coordinates} by
     * their i<sup>th</sup> constraint or parameter.
     * @param i the 0-based index of the ordinate to compare
     * @return a comparator that uses the given ordinate for comparison
     */
    public static Comparator<Coordinates> sortableBy(int i) {
        return new Comparator<>() {
            @Override
            public int compare(Coordinates o1, Coordinates o2) {
                if (o1.arity() != o2.arity()) throw new IllegalArgumentException(MISMATCHED_ARITY);
                return o1.getOrdinate(i).compareTo(o2.getOrdinate(i));
            }
        };
    }

    /**
     * Obtain a comparator that compares {@link Coordinates} by
     * their value.
     * @return a comparator that compares by value
     */
    public static Comparator<Coordinates> sortableByValue() {
        return new Comparator<>() {
            @Override
            public int compare(Coordinates o1, Coordinates o2) {
                if (o1.arity() != o2.arity()) throw new IllegalArgumentException(MISMATCHED_ARITY);
                return o1.getValue().compareTo(o2.getValue());
            }
        };
    }

    /**
     * Obtain a comparator that compares {@link Coordinates} by
     * the ordinate that correlates with the given {@link Axis}.
     * @param dimension the ordinate axis
     * @return a comparator that compares by the ordinate that corresponds to {@code dimension}
     */
    public static Comparator<Coordinates> sortableBy(Axis dimension) {
        // this logic is dumb since we don't know the arity of the coordinate objects
        // in advance -- for 2D coordinates, you might want to sort on value, for instance
        switch (dimension) {
            case X_AXIS:
                return sortableBy(0);
            case Y_AXIS:
                return sortableBy(1);
            case Z_AXIS:
                return sortableBy(2);
            default:
                Logger.getLogger(Coordinates.class.getName()).log(Level.SEVERE,
                        "Unknown axis: {0}.", dimension);
                break;
        }
        throw new IllegalArgumentException("Supplied axis is not valid");
    }

    /**
     * Obtain the i<sup>th</sup> constraint or parameter for this datum. Note
     * that negative indexing is supported.
     * @param i the index of the constraint to obtain
     * @return the constraint
     * @throws IndexOutOfBoundsException if {@code i} points to a non-existent constraint
     */
    public RealType getOrdinate(int i) {
        if (i < 0) return inputs[inputs.length + i];  // negative indexing
        if (i > inputs.length) throw new IndexOutOfBoundsException("Index " + i + " exceeds arity " + arity());
        return inputs[i];
    }

    public RealType getValue() {
        return value;
    }

    /**
     * When the error bounds for a value are asymmetric, use this method to set the
     * bounds for a given datum.  Note that these error values are relative to the
     * value, not absolute.
     * @param lowError  the lower bound of the error relative to {@link #getValue()}
     * @param highError the upper bound of the error relative to {@link #getValue()}
     */
    public void setAsymmetricRelativeError(RealType lowError, RealType highError) {
        if (lowError.sign() != Sign.NEGATIVE || highError.sign() != Sign.POSITIVE) {
            throw new IllegalArgumentException("Invalid bounds");
        }
        this.lowError = lowError;
        this.highError = highError;
    }

    /**
     * Obtain the error range for this datum. Note that the {@link Range} returned
     * is absolute; thus, the values contained therein bracket the value
     * returned by {@link #getValue()}.
     * @return the error bounds for this datum
     */
    public Range<RealType> getErrorBounds() {
        if (lowError == null) {
            return new Range<>((RealType) highError.negate().add(value),
                    (RealType) highError.add(value), Range.BoundType.EXCLUSIVE);
        }
        return new Range<>((RealType) lowError.add(value), (RealType) highError.add(value), Range.BoundType.EXCLUSIVE);
    }

    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder();

        buf.append(Arrays.stream(inputs).map(Numeric::toString).collect(Collectors.joining(",\u2009")));
        buf.append(":\u2009").append(value);
        if (lowError == null) {
            if (highError != null) buf.append("\u2009\u00B1\u2009").append(highError);
        } else {
            buf.append(" (").append(lowError).append(",\u2009").append(highError).append(')');
        }

        return buf.toString();
    }
}
