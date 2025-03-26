package tungsten.types.functions;
/*
 * The MIT License
 *
 * Copyright © 2022 Robert Poole <Tarquin.AZ@gmail.com>.
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

import tungsten.types.Numeric;
import tungsten.types.Vector;
import tungsten.types.exceptions.CoercionException;
import tungsten.types.numerics.ComplexType;
import tungsten.types.numerics.RealType;
import tungsten.types.numerics.impl.ExactZero;
import tungsten.types.util.MathUtils;
import tungsten.types.vector.impl.ComplexVector;

import java.math.MathContext;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * A vector of named function arguments.
 * @param <T> the type of the argument values
 */
public class ArgVector<T extends Numeric> implements Vector<T> {
    // a LinkedHashMap will maintain insertion order
    final LinkedHashMap<String, T> args = new LinkedHashMap<>();

    /**
     * Construct an argument vector with an array of argument names
     * and a mapping from variable names to values.
     * The order of arguments is determined by the order of the argument names.
     * @param argLabels an array of argument names
     * @param source    a mapping from argument names to values
     */
    public ArgVector(String[] argLabels, ArgMap<T> source) {
        if (source.arity() < argLabels.length) throw new IllegalArgumentException("Mismatched arity");
        Arrays.stream(argLabels).forEachOrdered(label -> append(label, source.get(label)));
    }

    /**
     * Construct an argument vector with an array of argument names,
     * a mapping from variable names to values, and a default value.
     * The order of arguments is determined by the order of the argument names.
     * Any argument name for which no mapping exists is assigned the default value.
     * @param argLabels    an array of argument names
     * @param source       a mapping from argument names to values
     * @param defaultValue the default value for unspecified mappings
     */
    public ArgVector(String[] argLabels, ArgMap<T> source, T defaultValue) {
        Arrays.stream(argLabels).forEachOrdered(label -> append(label, source.getOrDefault(label, defaultValue)));
    }

    /**
     * Append a name-value pair to this argument vector.
     * @param label a variable name
     * @param value the value bound to this variable
     */
    public void append(String label, T value) {
        args.put(label, value);
    }

    /**
     * Used to generate a variable name automatically for dynamically created mappings.
     * @return a variable name based on the current size of the arg vector
     */
    protected String nextArgName() {
        // if we get an unlabeled argument appended through the Vector API, generate a label
        return "arg" + length();
    }

    /**
     * Obtain the index for a given variable name.
     * @param label a variable name
     * @return the index of the bound variable
     * @throws IllegalArgumentException if {@code label} does not correspond to a bound variable
     */
    public long indexForLabel(String label) {
        long index = 0L;
        for (String toCheck : args.keySet()) {
            if (toCheck.equals(label)) return index;
            index++;
        }
        throw new IllegalArgumentException("No such element: " + label);
    }

    /**
     * Given an index, obtain the name of the variable bound at that index.
     * @param index a non-negative index into this vector
     * @return the name of the variable bound at {@code index}
     * @throws IndexOutOfBoundsException if the index is negative or &ge;&nbsp;{@link #length()}
     */
    public String labelForIndex(long index) {
        if (index < 0L) throw new IndexOutOfBoundsException("index cannot be negative");
        return args.keySet().stream().skip(index).findFirst().orElseThrow(() -> new IndexOutOfBoundsException("No vector element at idx=" + index));
    }

    /**
     * Obtain a {@code List} of all variable names bound within
     * this argument vector.
     * @return a {@code List<String>} of variable names
     */
    public List<String> getElementLabels() {
        return new ArrayList<>(args.keySet());
    }

    /**
     * Returns true if this arg vector contains a mapping
     * for the given variable name.
     * @param varLabel the variable name for which we are checking
     * @return true if and only if there is a mapping present, false otherwise
     */
    public boolean hasVariableName(String varLabel) {
        return args.containsKey(varLabel);
    }

    @Override
    public long length() {
        return arity();
    }

    /**
     * Obtain the arity or dimension of this vector.
     * @return the arity
     */
    public long arity() {
        return args.keySet().stream().count();
    }

    @Override
    public T elementAt(long position) {
        if (position < 0L) throw new IndexOutOfBoundsException("Negative indices are unsupported");
        return args.values().stream().skip(position).findFirst().orElseThrow(IndexOutOfBoundsException::new);
    }

    /**
     * Given a variable name, obtain the bound value.
     * @param label a variable name
     * @return the value bound to the named variable, or {@code null} if no binding exists
     */
    public T forVariableName(String label) {
        return args.get(label);
    }

    @Override
    public void setElementAt(T element, long position) {
        final Logger logger = Logger.getLogger(ArgVector.class.getName());
        final String varLabel = labelForIndex(position);
        T oldval = args.replace(varLabel, element);
        if (oldval == null) {
            logger.log(Level.WARNING,
                    "Either there was no value set at index {0}" +
                            ", or else '{1}' was never associated with any index.",
                    new Object[] {position, varLabel});
        } else {
            logger.log(Level.INFO,
                    "Successfully updated {0} from {1} to {2}",
                    new Object[] {varLabel, oldval, element});
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
        final String[] varNames = args.keySet().toArray(String[]::new);
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
        final String[] varNames = args.keySet().toArray(String[]::new);
        ArgMap<T> nuMap = new ArgMap<>();
        for (String label : varNames) {
            nuMap.put(label, (T) args.get(label).negate());
        }
        return new ArgVector<>(varNames, nuMap);
    }

    @Override
    public ArgVector<T> scale(T factor) {
        final String[] varNames = args.keySet().toArray(String[]::new);
        ArgMap<T> nuMap = new ArgMap<>();
        for (String label : varNames) {
            nuMap.put(label, (T) args.get(label).multiply(factor));
        }
        return new ArgVector<>(varNames, nuMap);
    }

    @Override
    public RealType magnitude() {
        final Numeric zero = ExactZero.getInstance(getMathContext());
        Numeric sumOfSquares = args.values().parallelStream().map(x -> {
            T r = x.magnitude();
            return r.multiply(r);
        }).reduce(zero, Numeric::add);
        try {
            return (RealType) sumOfSquares.sqrt().coerceTo(RealType.class);
        } catch (CoercionException e) {
            throw new ArithmeticException("Problem coercing sqrt(" + sumOfSquares +
                    ") while computing magnitude");
        }
    }

    @Override
    public T dotProduct(Vector<T> other) {
        if (this.length() != other.length()) throw new ArithmeticException("Vectors are of different lengths");
        long idx = 0L;
        Numeric accum = ExactZero.getInstance(getMathContext());
        if (ComplexType.class.isAssignableFrom(other.getElementType())) {
            List<T> copyOf = new ArrayList<>((int) other.length());
            for (long k = 0L; k < other.length(); k++) copyOf.add(other.elementAt(k));
            other = (Vector<T>) new ComplexVector((List<ComplexType>) copyOf).conjugate();
        }
        for (T element : args.values()) {
            accum = accum.add(element.multiply(other.elementAt(idx++)));
        }
        try {
            return (T) accum.coerceTo(getElementType());
        } catch (CoercionException e) {
            throw new IllegalStateException("Unable to compute dot product", e);
        }
    }

    @Override
    public Vector<T> crossProduct(Vector<T> other) {
        if (!(other instanceof ArgVector)) {
            // cross product is anti-commutative
            return other.crossProduct(this).negate();
        }
        throw new UnsupportedOperationException("ArgVector does not currently support this method");
    }

    @Override
    public ArgVector<T> normalize() {
        final Class<T> clazz = getElementType();
        try {
            return this.scale((T) this.magnitude().inverse().coerceTo(clazz));
        } catch (CoercionException ex) {
            Logger.getLogger(ArgVector.class.getName()).log(Level.SEVERE,
                    "Unable to normalize vector of type " + clazz.getTypeName(), ex);
            throw new ArithmeticException("Error computing vector normal");
        }
    }

    @Override
    public RealType computeAngle(Vector<T> other) {
        if (!(other instanceof ArgVector)) {
            return other.computeAngle(this);
        }
        throw new UnsupportedOperationException("ArgVector does not currently support this method");
    }

    /**
     * Safely coerce this {@code ArgVector} to an equivalent {@code ArgVector}
     * of the given type.
     * @param clazz the target type of this coercion, a subtype of {@link Numeric}
     * @return an {@code ArgVector} with elements of type {@code clazz}
     * @param <T2> the type of the elements of the converted {@code ArgVector}
     * @throws IllegalArgumentException if this vector cannot be converted to an {@code ArgVector<T2>}
     */
    public <T2 extends Numeric> ArgVector<T2> coerceToArgVectorOf(Class<T2> clazz) {
        ArgMap<T2> coercedArgs = new ArgMap<>();
        List<String> argNames = new LinkedList<>();
        try {
            for (Map.Entry<String, T> entry : args.entrySet()) {
                argNames.add(entry.getKey());
                coercedArgs.put(entry.getKey(), (T2) entry.getValue().coerceTo(clazz));
            }
            return new ArgVector<>(argNames.toArray(String[]::new), coercedArgs);
        } catch (CoercionException e) {
            throw new IllegalArgumentException("Cannot convert arg vector " + this +
                    " to an arg vector of element type " + clazz.getTypeName());
        }
    }

    @Override
    public MathContext getMathContext() {
        if (args.isEmpty()) {
            return MathContext.UNLIMITED;
        }
        return MathUtils.inferMathContext(args.values());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        if (o instanceof ArgVector) {
            ArgVector<?> other = (ArgVector<?>) o;
            return Objects.equals(args, other.args);
        } else if (o instanceof Vector) {
            // for comparison with regular vectors, we only care about
            // the actual elements and their ordering
            Vector<?> other = (Vector<?>) o;
            if (other.length() != this.length()) return false;
            for (long k = 0L; k < length(); k++) {
                if (!Objects.equals(this.elementAt(k), other.elementAt(k))) return false;
            }
            return true;
        }
        // any other case, return false
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(args, getElementType());
    }

    @Override
    public String toString() {
        return args.entrySet().stream().map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining(",\u2009", "\u27E8", "\u27E9"));
    }
}
