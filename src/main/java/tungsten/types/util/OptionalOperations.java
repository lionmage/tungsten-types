package tungsten.types.util;
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

import tungsten.types.Matrix;
import tungsten.types.Numeric;
import tungsten.annotations.Columnar;
import tungsten.annotations.Constant;
import tungsten.annotations.ConstantFactory;
import tungsten.types.exceptions.CoercionException;
import tungsten.types.numerics.*;
import tungsten.types.numerics.impl.*;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A utility class containing useful operations that may be safely
 * invoked on objects, whether or not those objects directly support
 * those operations.
 *
 * @author Robert Poole <a href="mailto:Tarquin.AZ@gmail.com">Tarquin.AZ@gmail.com</a>
 */
public class OptionalOperations {
    private OptionalOperations() {
        // this class should never be instantiable
    }

    public static <T extends Numeric> T dynamicInstantiate(Class<T> clazz, String strValue) {
        final Class<? extends T> toInstantiate = ClassTools.reify(clazz);
        try {
            return toInstantiate.getConstructor(String.class).newInstance(strValue);
        }  catch (InstantiationException | IllegalAccessException | InvocationTargetException fatal) {
            throw new IllegalStateException("Fatal error while obtaining or using constructor for a Numeric subclass", fatal);
        } catch (NoSuchMethodException e) {
            if (clazz.isAnnotationPresent(Constant.class)) {
                // this is a constant, so throw an exception and inform the user
                // that instantiateConstant() is what they need
                Constant constAnnotation = clazz.getAnnotation(Constant.class);
                throw new UnsupportedOperationException("Class " + clazz.getName() + " represents the constant " +
                        constAnnotation.name() + " (" + constAnnotation.representation() + ") and should be " +
                        "instantiated with instantiateConstant() instead");
            }
            throw new IllegalArgumentException("No String-based constructor for class " + toInstantiate.getTypeName(), e);
        }
    }

    public static <T extends Numeric> T dynamicInstantiate(Class<T> clazz, Number quasiPrimitive) {
        NumericHierarchy h = NumericHierarchy.forNumericType(clazz);
        switch (h) {
            case INTEGER:
                return (T) new IntegerImpl(BigInteger.valueOf(quasiPrimitive.longValue()));
            case RATIONAL:
                if (quasiPrimitive instanceof Double || quasiPrimitive instanceof Float) {
                    RealType real = new RealImpl(BigDecimal.valueOf(quasiPrimitive.doubleValue()));
                    try {
                        return (T) real.coerceTo(RationalType.class);
                    } catch (CoercionException e) {
                        throw new IllegalStateException("Cannot rationalize real value obtained from " + quasiPrimitive, e);
                    }
                }
                // otherwise, just assume it's an integer value and return a rational with a denom of 1
                return (T) new RationalImpl(quasiPrimitive.longValue(), 1L, MathContext.UNLIMITED);
            case REAL:
                return (T) new RealImpl(BigDecimal.valueOf(quasiPrimitive.doubleValue()));
            case COMPLEX:
                RealType realVal = new RealImpl(BigDecimal.valueOf(quasiPrimitive.doubleValue()));
                return (T) new ComplexRectImpl(realVal);
        }
        throw new UnsupportedOperationException("No way to construct an instance of " + h + " at this time");
    }

    public static <R extends Numeric> R instantiateConstant(String constName, MathContext mctx) {
        Collection<Class<?>> constClasses =
                ClassTools.findClassesInPackage("tungsten.types.numerics.impl", Constant.class);
        for (Class<?> type : constClasses) {
            Constant constAnno = type.getAnnotation(Constant.class);
            if (!constAnno.name().equals(constName)) continue;

            Optional<Method> method = Arrays.stream(type.getMethods()).filter(m -> m.isAnnotationPresent(ConstantFactory.class))
                    .filter(m -> (m.getModifiers() & Modifier.STATIC) != 0)  // we expect the factory method to be static
                    .filter(m -> m.getReturnType().isAssignableFrom(type)).findFirst();
            if (method.isPresent()) {
                final Method m = method.get();
                Logger.getLogger(OptionalOperations.class.getName()).log(Level.INFO,
                        "Constant factory method {0} will be invoked on {1} to obtain {2}",
                        new Object[]{m.getName(), type.getName(), constAnno.representation()});
                ConstantFactory factoryAnno = m.getAnnotation(ConstantFactory.class);
                try {
                    if (factoryAnno.noArgs()) {
                        return (R) m.invoke(null);
                    } else if (m.getParameterCount() == 1 && MathContext.class.isAssignableFrom(m.getParameterTypes()[0])) {
                        if (factoryAnno.argTypes().length > 1) {
                            throw new IllegalStateException("Mismatch in method parameter count between annotation and declaration");
                        }
                        return (R) m.invoke(null, mctx);
                    } else if (m.getParameterCount() == 2 &&
                            MathContext.class.isAssignableFrom(m.getParameterTypes()[0]) &&
                            Sign.class.isAssignableFrom(m.getParameterTypes()[1])) {
                        if (factoryAnno.argTypes().length != 2) {
                            throw new IllegalStateException("Mismatch in method parameter count between annotation and declaration");
                        }
                        // we don't have enough info for this version of this utility method, so throw an exception
                        throw new UnsupportedOperationException("Factory method " + m.getName() +
                                " requires MathContext and Sign, but we have only MathContext");
                    } else {
                        throw new UnsupportedOperationException("instantiateConstant() cannot currently handle > 1 factory args");
                    }
                } catch (IllegalAccessException | InvocationTargetException e) {
                    throw new IllegalStateException("While attempting to instantiate constant " + constName, e);
                }
            }
        }
        // if we fall through, we failed -- should we return null instead of throwing an exception?
        throw new IllegalArgumentException("No constant found named " + constName);
    }

    public static <R extends Numeric> R instantiateConstant(String constName, MathContext mctx, Sign sign) {
        Collection<Class<?>> constClasses =
                ClassTools.findClassesInPackage("tungsten.types.numerics.impl", Constant.class);
        for (Class<?> type : constClasses) {
            Constant constAnno = type.getAnnotation(Constant.class);
            if (!constAnno.name().equals(constName)) continue;

            Optional<Method> method = Arrays.stream(type.getMethods()).filter(m -> m.isAnnotationPresent(ConstantFactory.class))
                    .filter(m -> (m.getModifiers() & Modifier.STATIC) != 0)  // we expect the factory method to be static
                    .filter(m -> m.getReturnType().isAssignableFrom(type)).findFirst();
            if (method.isPresent()) {
                final Method m = method.get();
                Logger.getLogger(OptionalOperations.class.getName()).log(Level.INFO,
                        "Constant factory method {0} will be invoked on {1} to obtain {2}",
                        new Object[]{m.getName(), type.getName(), constAnno.representation()});
                ConstantFactory factoryAnno = m.getAnnotation(ConstantFactory.class);
                try {
                    if (factoryAnno.noArgs()) {
                        return (R) m.invoke(null);
                    } else if (m.getParameterCount() == 1 && MathContext.class.isAssignableFrom(m.getParameterTypes()[0])) {
                        if (factoryAnno.argTypes().length > 1) {
                            throw new IllegalStateException("Mismatch in method parameter count between annotation and declaration");
                        }
                        return (R) m.invoke(null, mctx);
                    } else if (m.getParameterCount() == 2 &&
                            MathContext.class.isAssignableFrom(m.getParameterTypes()[0]) &&
                            Sign.class.isAssignableFrom(m.getParameterTypes()[1])) {
                        if (factoryAnno.argTypes().length != 2) {
                            throw new IllegalStateException("Mismatch in method parameter count between annotation and declaration");
                        }
                        return (R) m.invoke(null, mctx, sign);
                    } else {
                        throw new UnsupportedOperationException("instantiateConstant() cannot currently handle > 2 factory args");
                    }
                } catch (IllegalAccessException | InvocationTargetException e) {
                    throw new IllegalStateException("While attempting to instantiate constant " + constName, e);
                }
            }
        }
        // if we fall through, we failed -- should we return null instead of throwing an exception?
        throw new IllegalArgumentException("No constant found named " + constName);
    }

    public static <R extends Numeric> R instantiateConstant(String constName, Object... arguments) {
        Collection<Class<?>> constClasses =
                ClassTools.findClassesInPackage("tungsten.types.numerics.impl", Constant.class);
        for (Class<?> type : constClasses) {
            Constant constAnno = type.getAnnotation(Constant.class);
            if (!constAnno.name().equals(constName)) continue;

            Optional<Method> method = Arrays.stream(type.getMethods()).filter(m -> m.isAnnotationPresent(ConstantFactory.class))
                    .filter(m -> (m.getModifiers() & Modifier.STATIC) != 0)  // we expect the factory method to be static
                    .filter(m -> m.getReturnType().isAssignableFrom(type))
                    .filter(m -> m.getParameterCount() == (arguments == null ? 0 : arguments.length)).findFirst();
            if (method.isPresent()) {
                final Method m = method.get();
                Logger.getLogger(OptionalOperations.class.getName()).log(Level.INFO,
                        "Constant factory method {0} will be invoked on {1} to obtain {2}",
                        new Object[] {m.getName(), type.getName(), constAnno.representation()});
                ConstantFactory factoryAnno = m.getAnnotation(ConstantFactory.class);
                try {
                    if (factoryAnno.noArgs()) {
                        if (arguments != null && arguments.length > 0) {
                            throw new IllegalArgumentException("Factory method " + m.getName() + " requires no arguments");
                        }
                        return (R) m.invoke(null);
                    } else {
                        final int argLen = arguments == null ? 0 : arguments.length;
                        if (m.getParameterCount() != argLen) {
                            throw new IllegalArgumentException("Factory method expected " + m.getParameterCount() +
                                    " arguments, but got " + argLen);
                        }
                        if (argLen > 0) {
                            Class<?>[] parameterTypes = factoryAnno.argTypes().length == 0 ? new Class[1] : new Class[argLen];
                            if (factoryAnno.argTypes().length == 0) parameterTypes[0] = factoryAnno.argType();
                            else System.arraycopy(factoryAnno.argTypes(), 0, parameterTypes, 0, argLen);
                            for (int k = 0; k < argLen; k++) {
                                assert m.getParameterTypes()[k].isAssignableFrom(parameterTypes[k]);
                                assert m.getParameterTypes()[k].isAssignableFrom(arguments[k].getClass());
                            }
                            return (R) m.invoke(null, arguments);
                        } else {
                            Logger.getLogger(OptionalOperations.class.getName()).log(Level.WARNING,
                                    "No arguments supplied to instantiateConstant(), nor are they " +
                                            "required by {0}, but noArgs is not set true in " +
                                            "@ConstantFactory annotation.", m.getName());
                            throw new IllegalStateException("Missing noArgs param on @ConstantFactory");
                        }
                    }
                } catch (IllegalAccessException | InvocationTargetException e) {
                    throw new IllegalStateException("While attempting to instantiate constant " + constName, e);
                }
            }
        }
        // if we fall through, we failed -- should we return null instead of throwing an exception?
        throw new IllegalArgumentException("No constant found named " + constName);
    }

    public static Class<? extends Numeric> findCommonType(Class<? extends Numeric> type1, Class<? extends Numeric> type2) {
        SortedSet<Class<? extends Numeric>> if1 = new TreeSet<>(NumericHierarchy.obtainTypeComparator());
        expandHierarchy(if1, type1);

        SortedSet<Class<? extends Numeric>> if2 = new TreeSet<>(NumericHierarchy.obtainTypeComparator());
        expandHierarchy(if2, type2);

        if (if1.retainAll(if2)) {
            // if if1 changed, log if1 ∩ if2
            Logger.getLogger(OptionalOperations.class.getName()).log(Level.FINE,
                    "Intersection between type hierarchies of {0} and {1} is {2}",
                    new Object[] { type1.getTypeName(), type2.getTypeName(), if1 });
        }
        if (if1.containsAll(if2)) {
            Logger.getLogger(OptionalOperations.class.getName()).log(Level.FINE,
                    "Types {0} and {1} have identical type hierarchies of size {2}, or {0}'s hierarchy is a superset of {1}'s",
                    new Object[] { type1.getTypeName(), type2.getTypeName(), if1.size() });
        }
        switch (if1.size()) {
            case 0:
                Logger.getLogger(OptionalOperations.class.getName()).log(Level.INFO, "No common type found between {0} and {1}",
                        new Object[] { type1.getTypeName(), type2.getTypeName() });
                break;
            case 1:
                return if1.first();
            default:
                return if1.last();
        }
        // fall-through default
        return Numeric.class;
    }

    private static void expandHierarchy(SortedSet<Class<? extends Numeric>> accumulator, Class<? extends Numeric> type) {
        for (Class<?> clazz : type.getInterfaces()) {
            if (Numeric.class.isAssignableFrom(clazz)) {
                expandHierarchy(accumulator, (Class<? extends Numeric>) clazz);
                accumulator.add((Class<? extends Numeric>) clazz);
            }
        }
        if (type.isInterface()) accumulator.add(type);
    }

    public static Class<? extends Numeric> findTypeFor(Matrix<? extends Numeric> M) {
        if (M.getClass().isAnnotationPresent(Columnar.class)) {
            return M.getColumn(0L).getElementType();
        } else {
            return M.getRow(0L).getElementType();
        }
    }

    /**
     * A convenience method for obtaining a {@link Sign} for any
     * {@link Numeric} instance that also implements {@link Comparable}.
     * This is useful for implementations that lack a {@code sign()}
     * method.
     * @param value any {@link Numeric} value
     * @return the sign of {@code value}, if it exists
     * @throws IllegalArgumentException if the input value is not comparable
     *  (e.g., any instance of {@link ComplexType})
     */
    public static Sign sign(Numeric value) {
        try {
            Method m = value.getClass().getMethod("sign");
            return (Sign) m.invoke(value);
        } catch (NoSuchMethodException ex) {
            if (value instanceof Comparable) {
                final Zero zero = (Zero) ExactZero.getInstance(value.getMathContext());
                switch (Integer.signum(zero.compareTo(value))) {
                    case 1:
                        return Sign.NEGATIVE;
                    case 0:
                        return Sign.ZERO;
                    case -1:
                        return Sign.POSITIVE;
                    default:
                        throw new IllegalStateException("Signum failed");
                }
            } else {
                throw new IllegalArgumentException("Cannot obtain sign for " + value);
            }
        } catch (SecurityException | IllegalAccessException | InvocationTargetException ex) {
            Logger.getLogger(OptionalOperations.class.getName())
                    .log(Level.SEVERE, "Unable to compute sign for " + value, ex);
            throw new IllegalStateException("Failed to obtain sign for " + value, ex);
        }
    }

    public static boolean setMathContext(Numeric number, MathContext updated) {
        try {
            Method m = number.getClass().getMethod("setMathContext", MathContext.class);
            m.invoke(number, updated);  // setMathContext() doesn't have a return value
            return true;
        } catch (NoSuchMethodException e) {
            Logger.getLogger(OptionalOperations.class.getName()).log(Level.INFO,
                    "{0} does not have a setMathContext() method, not updating", number.getClass().getTypeName());
            return false;
        } catch (IllegalAccessException | InvocationTargetException e) {
            // this is a warning because these exceptions are unexpected (and potentially problematic)
            Logger.getLogger(OptionalOperations.class.getName()).log(Level.WARNING,
                    "While attempting to invoke setMathContext() on " + number, e);
            return false;
        }
    }

    /**
     * A convenience method for obtaining a {@link BigDecimal} equivalent from
     * an instance of {@link Numeric}.  Useful since not all implementations
     * have an {@code asBigDecimal()} method.
     * @param number the {@link Numeric} instance from which to extract a BigDecimal value
     * @return the {@link BigDecimal} equivalent of the value
     */
    public static BigDecimal asBigDecimal(Numeric number) {
        Optional<Method> asBigDec = Arrays.stream(number.getClass().getMethods())
                .filter(m -> BigDecimal.class.isAssignableFrom(m.getReturnType()))
                .filter(m -> m.getName().equals("asBigDecimal")).findAny();
        try {
            if (asBigDec.isPresent()) {
                return (BigDecimal) asBigDec.get().invoke(number);
            } else if (number.isCoercibleTo(RealType.class)) {
                RealType temp = (RealType) number.coerceTo(RealType.class);
                return temp.asBigDecimal();
            }
        } catch (IllegalAccessException | InvocationTargetException | CoercionException fatal) {
            Logger.getLogger(OptionalOperations.class.getName()).log(Level.SEVERE,
                    "While computing asBigDecimal for " + number, fatal);
            throw new IllegalStateException("We should not have gotten here", fatal);
        }
        throw new IllegalArgumentException(number + " cannot be represented as a BigDecimal");
    }
}
