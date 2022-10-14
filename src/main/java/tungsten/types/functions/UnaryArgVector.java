package tungsten.types.functions;

import tungsten.types.Numeric;

import java.util.Collections;

public class UnaryArgVector<T extends Numeric> extends ArgVector<T> {
    final String argname;

    public UnaryArgVector(String argName, T value) {
        super(new String[] {argName}, new ArgMap<>(Collections.singletonMap(argName, value)));
        if (argName == null || argName.length() == 0) {
            throw new IllegalArgumentException("Argument name must not be empty.");
        }
        this.argname = argName;
    }

    public void set(T value) {
        super.setElementAt(value, 0L);
    }

    @Override
    public void append(String label, T value) {
        if (!argname.equals(label) || this.length() > 0L) {
            throw new UnsupportedOperationException("This vector cannot have more than 1 element, and it must be named " + argname);
        }
        super.append(label, value);
    }

    @Override
    public UnaryArgVector<T> normalize() {
        T originalValue = super.forVariableName(argname);
        T updated = (T) originalValue.divide(originalValue.magnitude());
        return new UnaryArgVector<>(argname, updated);
    }
}
