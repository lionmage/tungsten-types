package tungsten.types.util;

import tungsten.types.Numeric;
import tungsten.types.numerics.*;
import tungsten.types.numerics.impl.*;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.MathContext;
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
            return toInstantiate.getConstructor(String.class).newInstance(strValue);
        }  catch (InstantiationException | IllegalAccessException | InvocationTargetException fatal) {
            throw new IllegalStateException("Fatal error while obtaining or using constructor for a Numeric subclass", fatal);
        } catch (NoSuchMethodException e) {
            // attempt to recover if at all possible
            Optional<Method> generator = Arrays.stream(toInstantiate.getMethods()).filter(m -> "getInstance".equals(m.getName()))
                    .filter(m -> m.getParameterCount() == 1)
                    .filter(m -> MathContext.class.isAssignableFrom(m.getParameterTypes()[0]))
                    .findFirst();
            if (generator.isPresent()) {
                MathContext ctx = new MathContext(strValue);  // in this case, we interpret the String as a constructor arg for MathContext
                try {
                    generator.get().invoke(null, ctx);  // method should be static, so first argument of invoke is null
                } catch (IllegalAccessException | InvocationTargetException ex) {
                    throw new IllegalStateException("While attempting to recover from failed constructor lookup", ex);
                }
            }
            throw new IllegalArgumentException("No String-based constructor for class " + toInstantiate.getTypeName(), e);
        }
    }

    public static Class<? extends Numeric> findCommonType(Class<? extends Numeric> type1, Class<? extends Numeric> type2) {
        SortedSet<Class<? extends Numeric>> if1 = new TreeSet<>(NumericHierarchy.obtainTypeComparator());
        expandHierarchy(if1, type1);

        SortedSet<Class<? extends Numeric>> if2 = new TreeSet<>(NumericHierarchy.obtainTypeComparator());
        expandHierarchy(if2, type2);

        if (if1.retainAll(if2)) {
            // if if1 changed, log if1 ∩ if2
            Logger.getLogger(OptionalOperations.class.getName()).log(Level.FINE,
                    "Intersection between type hierarchies of {} and {} is {}",
                    new Object[] { type1.getTypeName(), type2.getTypeName(), if1 });
        }
        if (if1.containsAll(if2)) {
            Logger.getLogger(OptionalOperations.class.getName()).log(Level.FINE,
                    "Types {0} and {1} have identical type hierarchies of size {2}, or {0}'s hierarchy is a superset of {1}'s",
                    new Object[] { type1.getTypeName(), type2.getTypeName(), if1.size() });
        }
        switch (if1.size()) {
            case 0:
                Logger.getLogger(OptionalOperations.class.getName()).log(Level.INFO, "No common type found between {} and {}",
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
}
