package tungsten.types.functions.support;

import tungsten.types.Numeric;
import tungsten.types.Range;
import tungsten.types.numerics.RealType;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class Coordinates {
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
     * For a function f(x<sub>0</sub>, x<sub>1</sub>, &hellip;), the first
     * n - 1 values of the tuple would represent the function's arguments,
     * while the n<sup>th</sup> value would represent the value of f()
     * evaluated at those arguments.  For experimental data,
     * the final value would represent some measurement taken with
     * respect to the parameters occurring before it.
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

    public long arity() {
        return inputs.length;
    }

    public RealType getOrdinate(int i) {
        if (i < 0) return inputs[inputs.length + i];  // negative indexing
        if (i > inputs.length) throw new IndexOutOfBoundsException("Index " + i + " exceeds arity " + arity());
        return inputs[i];
    }

    public RealType getValue() {
        return value;
    }

    public void setAsymmetricRelativeError(RealType lowError, RealType highError) {
        this.lowError = lowError;
        this.highError = highError;
    }

    public Range<RealType> getErrorBounds() {
        if (lowError == null) {
            return new Range<>((RealType) highError.negate().add(value),
                    (RealType) highError.add(value), Range.BoundType.EXCLUSIVE);
        }
        return new Range<>((RealType) lowError.add(value), (RealType) highError.add(value), Range.BoundType.EXCLUSIVE);
    }

    public String toString() {
        StringBuilder buf = new StringBuilder();

        buf.append(Arrays.stream(inputs).map(Numeric::toString).collect(Collectors.joining(",\u2009")));
        buf.append(":\u2009").append(value);
        if (lowError == null) {
            if (highError != null) buf.append("\u2009\u00B1\u2009").append(highError);
        } else {
            buf.append(" (").append(lowError).append(", ").append(highError).append(')');
        }

        return buf.toString();
    }
}
