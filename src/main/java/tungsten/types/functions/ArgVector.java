package tungsten.types.functions;

import tungsten.types.Numeric;
import tungsten.types.Vector;
import tungsten.types.exceptions.CoercionException;
import tungsten.types.numerics.RealType;
import tungsten.types.numerics.impl.ExactZero;
import tungsten.types.util.MathUtils;

import java.lang.reflect.ParameterizedType;
import java.math.MathContext;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class ArgVector<T extends Numeric> implements Vector<T> {
    // a LinkedHashMap will maintain insertion order
    final LinkedHashMap<String, T> args = new LinkedHashMap<>();

    public ArgVector(String[] argLabels, ArgMap<T> source) {
        if (source.arity() < argLabels.length) throw new IllegalArgumentException("Mismatched arity.");
        Arrays.stream(argLabels).forEachOrdered(label -> {
            append(label, source.get(label));
        });
    }

    public ArgVector(String[] argLabels, ArgMap<T> source, T defaultValue) {
        Arrays.stream(argLabels).forEachOrdered(label -> {
            append(label, source.getOrDefault(label, defaultValue));
        });
    }

    public void append(String label, T value) {
        args.put(label, value);
    }

    protected String nextArgName() {
        // if we get an unlabeled argument appended through the Vector API, generate a label
        return "arg" + length();
    }

    public long indexForLabel(String label) {
        long index = 0L;
        for (String toCheck : args.keySet()) {
            if (toCheck.equals(label)) return index;
            index++;
        }
        throw new IllegalArgumentException("No such element: " + label);
    }

    public String labelForIndex(long index) {
        return args.keySet().stream().sequential().skip(index).findFirst().orElseThrow();
    }

    public List<String> getElementLabels() {
        return args.keySet().stream().sequential().collect(Collectors.toList());
    }

    public boolean hasVariableName(String varLabel) {
        return args.containsKey(varLabel);
    }

    @Override
    public long length() {
        return arity();
    }

    public long arity() {
        return args.keySet().stream().count();
    }

    @Override
    public T elementAt(long position) {
        if (position < 0L) throw new IndexOutOfBoundsException("Negative indices are unsupported");
        return args.values().stream().sequential().skip(position).findFirst().orElseThrow(IndexOutOfBoundsException::new);
    }

    public T forVariableName(String label) {
        return args.get(label);
    }

    @Override
    public void setElementAt(T element, long position) {
        final String varLabel = labelForIndex(position);
        T oldval = args.replace(varLabel, element);
        if (oldval == null) {
            Logger.getLogger(ArgVector.class.getName())
                    .warning("Either there was no value set at index " + position +
                            ", or else '" + varLabel + "' was never associated with any index.");
        } else {
            Logger.getLogger(ArgVector.class.getName())
                    .info("Successfully updated " + varLabel + " from " +
                            oldval + " to " + element);
        }
    }

    @Override
    public void append(T element) {
        append(nextArgName(), element);
    }

    @Override
    public ArgVector<T> add(Vector<T> addend) {
        if (addend.length() != this.length()) {
            throw new ArithmeticException("Cannot add two vectors of different length");
        }
        long idx = 0L;
        final String[] varNames = args.keySet().stream().sequential().toArray(String[]::new);
        ArgMap<T> nuMap = new ArgMap<>();
        for (String label : varNames) {
            nuMap.put(label, (T) args.get(label).add(addend.elementAt(idx++)));
        }
        return new ArgVector<>(varNames, nuMap);
    }

    @Override
    public Vector<T> subtract(Vector<T> subtrahend) {
        return this.add(subtrahend.negate());
    }

    @Override
    public ArgVector<T> negate() {
        final String[] varNames = args.keySet().stream().sequential().toArray(String[]::new);
        ArgMap<T> nuMap = new ArgMap<>();
        for (String label : varNames) {
            nuMap.put(label, (T) args.get(label).negate());
        }
        return new ArgVector<>(varNames, nuMap);
    }

    @Override
    public ArgVector<T> scale(T factor) {
        final String[] varNames = args.keySet().stream().sequential().toArray(String[]::new);
        ArgMap<T> nuMap = new ArgMap<>();
        for (String label : varNames) {
            nuMap.put(label, (T) args.get(label).multiply(factor));
        }
        return new ArgVector<>(varNames, nuMap);
    }

    @Override
    public T magnitude() {
        final Class<T> clazz = (Class<T>) ((Class) ((ParameterizedType) getClass()
                .getGenericSuperclass()).getActualTypeArguments()[0]);
        final Numeric zero = ExactZero.getInstance(getMathContext());
        Numeric sumOfSquares = args.values().parallelStream().map(x -> x.multiply(x))
                .reduce(zero, Numeric::add);
        try {
            return (T) sumOfSquares.sqrt().coerceTo(clazz);
        } catch (CoercionException e) {
            throw new ArithmeticException("Problem coercing to " + clazz.getTypeName() +
                    " while computing magnitude.");
        }
    }

    @Override
    public T dotProduct(Vector<T> other) {
        if (this.length() != other.length()) throw new ArithmeticException("Vectors are of different lengths");
        final Class<T> clazz = (Class<T>) ((Class) ((ParameterizedType) getClass()
                .getGenericSuperclass()).getActualTypeArguments()[0]);
        long idx = 0L;
        Numeric accum = ExactZero.getInstance(getMathContext());
        for (T element : args.values()) {
            accum = accum.add(element.multiply(other.elementAt(idx++)));
        }
        try {
            return (T) accum.coerceTo(clazz);
        } catch (CoercionException e) {
            throw new IllegalStateException("Unable to compute dot product.", e);
        }
    }

    @Override
    public Vector<T> crossProduct(Vector<T> other) {
        throw new UnsupportedOperationException("We may never need this.");
    }

    @Override
    public ArgVector<T> normalize() {
        final Class<T> clazz = (Class<T>) ((Class) ((ParameterizedType) getClass()
                .getGenericSuperclass()).getActualTypeArguments()[0]);
        try {
            return this.scale((T) this.magnitude().inverse().coerceTo(clazz));
        } catch (CoercionException ex) {
            Logger.getLogger(ArgVector.class.getName()).log(Level.SEVERE,
                    "Unable to normalize vector for type " + clazz.getTypeName(), ex);
            throw new ArithmeticException("Error computing vector normal.");
        }
    }

    @Override
    public RealType computeAngle(Vector<T> other) {
        throw new UnsupportedOperationException("We may never need this.");
    }

    public <T2 extends Numeric> ArgVector<T2> coerceToArgVectorOf(Class<T2> clazz) {
        ArgMap<T2> coercedArgs = new ArgMap<>();
        List<String> argNames = new LinkedList<>();
        try {
            for (Map.Entry<String, T> entry : args.entrySet()) {
                argNames.add(entry.getKey());
                coercedArgs.put(entry.getKey(), (T2) entry.getValue().coerceTo(clazz));
            }
            return new ArgVector<>(argNames.toArray(String[]::new), coercedArgs);
        } catch (Exception e) {
            throw new IllegalArgumentException("Cannot convert arg vector " + this +
                    " to an arg vector of element type " + clazz.getTypeName());
        }
    }

    @Override
    public MathContext getMathContext() {
        if (args.size() == 0) {
            return MathContext.UNLIMITED;
        }
        return MathUtils.inferMathContext(args.values());
    }

    @Override
    public String toString() {
        return args.entrySet().stream().sequential().map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining(", ", "<", ">"));
    }
}
