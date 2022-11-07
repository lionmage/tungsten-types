package tungsten.types.functions;

import tungsten.types.Numeric;
import tungsten.types.exceptions.CoercionException;

import java.math.MathContext;
import java.util.Collections;
import java.util.logging.Level;
import java.util.logging.Logger;

public class UnaryArgVector<T extends Numeric> extends ArgVector<T> {
    final String argname;

    public UnaryArgVector(String argName, T value) {
        super(new String[] {argName}, new ArgMap<>(Collections.singletonMap(argName, value)));
        if (argName == null || argName.length() == 0) {
            throw new IllegalArgumentException("Argument name must not be empty.");
        }
        this.argname = argName;  // unfortunately, this can't be set before super()
    }

    public void set(T value) {
        super.setElementAt(value, 0L);
    }

    @Override
    public void append(String label, T value) {
        if ((argname != null && !argname.equals(label)) || this.length() > 0L) {
            throw new UnsupportedOperationException("This vector cannot have more than 1 element, and it must be named " + argname);
        }
        super.append(label, value);
    }

    @Override
    public UnaryArgVector<T> normalize() {
        T originalValue = super.forVariableName(argname);
        try {
            T updated = (T) originalValue.divide(originalValue.magnitude()).coerceTo(originalValue.getClass());
            return new UnaryArgVector<>(argname, updated);
        } catch (CoercionException e) {
            // we shouldn't get here normally, but for integer vectors, this might fail
            Logger.getLogger(UnaryArgVector.class.getName()).log(Level.SEVERE,
                    "Failure during vector normalization", e);
            throw new ArithmeticException("Failed to normalize.");
        }
    }

    @Override
    public MathContext getMathContext() {
        return elementAt(0L).getMathContext();
    }
}
