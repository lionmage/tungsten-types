package tungsten.types.functions.support;

import tungsten.types.numerics.RealType;

public class Coordinates3D extends Coordinates {
    public Coordinates3D(RealType x, RealType y, RealType z) {
        inputs = new RealType[2];
        inputs[0] = x;
        inputs[1] = y;
        value = z;
    }

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
