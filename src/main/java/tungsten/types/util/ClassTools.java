package tungsten.types.util;

import tungsten.types.Numeric;
import tungsten.types.numerics.ComplexType;
import tungsten.types.numerics.IntegerType;
import tungsten.types.numerics.RationalType;
import tungsten.types.numerics.RealType;
import tungsten.types.numerics.impl.*;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.annotation.Annotation;
import java.lang.reflect.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Derived from the work of Ian Robertson.
 *
 * @see <a href="https://www.artima.com/weblogs/viewpost.jsp?thread=208860">Ian Robertson's blog entry regarding this technique.</a>
 */
public class ClassTools {
    /**
     * Get the underlying class for a type, or null if the type is a variable type.
     * @param type the type
     * @return the underlying class
     */
    public static Class<?> getClass(Type type) {
        if (type instanceof Class) {
            return (Class) type;
        }
        else if (type instanceof ParameterizedType) {
            return getClass(((ParameterizedType) type).getRawType());
        }
        else if (type instanceof GenericArrayType) {
            Type componentType = ((GenericArrayType) type).getGenericComponentType();
            Class<?> componentClass = getClass(componentType);
            if (componentClass != null ) {
                return Array.newInstance(componentClass, 0).getClass();
            }
            else {
                return null;
            }
        }
        else {
            return null;
        }
    }

    /**
     * Get the actual type arguments a child class has used to extend a generic base class.
     *
     * @param baseClass the base class
     * @param childClass the child class
     * @return a list of the raw classes for the actual type arguments.
     */
    public static <T> List<Class<?>> getTypeArguments(
            Class<T> baseClass, Class<? extends T> childClass) {
        Map<Type, Type> resolvedTypes = new HashMap<>();
        Type type = childClass;
        // start walking up the inheritance hierarchy until we hit baseClass
        while (!Objects.equals(getClass(type), baseClass)) {  // getClass() can return null
            if (type instanceof Class) {
                // there is no useful information for us in raw types, so just keep going.
                type = ((Class) type).getGenericSuperclass();
            }
            else {
                ParameterizedType parameterizedType = (ParameterizedType) type;
                Class<?> rawType = (Class) parameterizedType.getRawType();

                Type[] actualTypeArguments = parameterizedType.getActualTypeArguments();
                TypeVariable<?>[] typeParameters = rawType.getTypeParameters();
                for (int i = 0; i < actualTypeArguments.length; i++) {
                    resolvedTypes.put(typeParameters[i], actualTypeArguments[i]);
                }

                if (!rawType.equals(baseClass)) {
                    type = rawType.getGenericSuperclass();
                }
            }
        }

        // finally, for each actual type argument provided to baseClass, determine (if possible)
        // the raw class for that type argument.
        Type[] actualTypeArguments;
        if (type instanceof Class) {
            actualTypeArguments = ((Class) type).getTypeParameters();
        }
        else {
            actualTypeArguments = ((ParameterizedType) type).getActualTypeArguments();
        }
        List<Class<?>> typeArgumentsAsClasses = new ArrayList<>();
        // resolve types by chasing down type variables.
        for (Type baseType: actualTypeArguments) {
            while (resolvedTypes.containsKey(baseType)) {
                baseType = resolvedTypes.get(baseType);
            }
            typeArgumentsAsClasses.add(getClass(baseType));
        }
        return typeArgumentsAsClasses;
    }

    private static final Map<Class<? extends Numeric>, Class<? extends Numeric>> reificationMap;

    static {
        reificationMap = new HashMap<>();
        reificationMap.put(IntegerType.class, IntegerImpl.class);
        reificationMap.put(RationalType.class, RationalImpl.class);
        reificationMap.put(RealType.class, RealImpl.class);
        reificationMap.put(ComplexType.class, ComplexRectImpl.class);
        reificationMap.put(Zero.class, ExactZero.class);
    }

    public static <T extends Numeric> Class<? extends T> reify(Class<T> potential) {
        if (!potential.isInterface()) {
            // it's already a concrete type, so return it
            return potential;
        }
        Class<? extends Numeric> ourClass = reificationMap.get(potential);
        if (ourClass == null) {
            throw new IllegalArgumentException("There is no concrete class for type " + potential.getTypeName());
        }
        return (Class<? extends T>) ourClass;
    }

    public static Collection<Class<?>> findClassesInPackage(String packageName, Class<? extends Annotation> withAnnotation) {
        InputStream stream = ClassLoader.getSystemClassLoader()
                .getResourceAsStream(packageName.replaceAll("[.]", "/"));
        if (stream == null) throw new IllegalStateException("Cannot open InputStream of class resources");
        BufferedReader reader = new BufferedReader(new InputStreamReader(stream));
        return reader.lines()
                .filter(line -> line.endsWith(".class"))
                .map(line -> obtainClass(line, packageName))
                .filter(c -> c.isAnnotationPresent(withAnnotation))
                .collect(Collectors.toSet());
    }

    private static Class<?> obtainClass(String className, String packageName) {
        try {
            // className will always end in .class here, so no need to add extra error checking
            return Class.forName(packageName + "." + className.substring(0, className.lastIndexOf('.')));
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("While attempting to load " + className, e);
        }
    }
}
