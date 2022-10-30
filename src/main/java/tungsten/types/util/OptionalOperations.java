package tungsten.types.util;

import tungsten.types.Numeric;
import tungsten.types.numerics.*;
import tungsten.types.numerics.impl.*;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class OptionalOperations {
    private OptionalOperations() {
        // this class should never be instantiable
    }

    public static <T extends Numeric> T dynamicInstantiate(Class<T> clazz, String strValue) {
        final Class<? extends T> toInstantiate = ClassTools.reify(clazz);
        try {
            return (T) toInstantiate.getConstructor(String.class).newInstance(strValue);
        }  catch (InstantiationException | IllegalAccessException | InvocationTargetException fatal) {
            throw new IllegalStateException("Fatal error while obtaining or using constructor for a Numeric subclass", fatal);
        } catch (NoSuchMethodException e) {
            throw new IllegalArgumentException("No String-based constructor for class " + toInstantiate.getTypeName(), e);
        }
    }

    public static Class<? extends Numeric> findCommonType(Class<? extends Numeric> type1, Class<? extends Numeric> type2) {
        List<Class<? extends Numeric>> if1 = new ArrayList<>();
        for (Class<?> clazz : type1.getInterfaces()) {
            if (Numeric.class.isAssignableFrom(clazz)) {
                if1.add((Class<? extends Numeric>) clazz);
            }
        }
        if (type1.isInterface()) if1.add(type1);

        List<Class<? extends Numeric>> if2 = new ArrayList<>();
        for (Class<?> clazz : type2.getInterfaces()) {
            if (Numeric.class.isAssignableFrom(clazz)) {
                if2.add((Class<? extends Numeric>) clazz);
            }
        }
        if (type2.isInterface()) if2.add(type2);

        if (if1.retainAll(if2)) {
            switch (if1.size()) {
                case 0:
                    Logger.getLogger(OptionalOperations.class.getName()).log(Level.INFO, "No common type found between {} and {}",
                            new Object[] { type1.getTypeName(), type2.getTypeName() });
                    break;
                case 1:
                    return if1.get(0);
                default:
                    if1.sort(NumericHierarchy.obtainTypeComparator());
                    return if1.get(if1.size() - 1);
            }
        }
        // fall-through default
        return Numeric.class;
    }

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
                    .log(Level.SEVERE, "Unable to compute signum for " + value, ex);
            throw new IllegalStateException("Failed to obtain sign for " + value, ex);
        }
    }
}
