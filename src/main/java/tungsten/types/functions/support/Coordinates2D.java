package tungsten.types.functions.support;

import tungsten.types.numerics.RealType;

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
