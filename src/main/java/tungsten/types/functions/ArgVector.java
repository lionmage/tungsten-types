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
        return args.keySet().stream().skip(index).findFirst().orElseThrow();
    }

    public List<String> getElementLabels() {
        return new ArrayList<>(args.keySet());
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
        return args.values().stream().skip(position).findFirst().orElseThrow(IndexOutOfBoundsException::new);
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
    public T magnitude() {
        final Numeric zero = ExactZero.getInstance(getMathContext());
        Numeric sumOfSquares = args.values().parallelStream().map(x -> {
            T r = x.magnitude();
            return r.multiply(r);
        }).reduce(zero, Numeric::add);
        try {
            return (T) sumOfSquares.sqrt().coerceTo(getElementType());
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
        return args.entrySet().stream().map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining(", ", "<", ">"));
    }
}
