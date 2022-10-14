package tungsten.types.functions;

import tungsten.types.Numeric;
import tungsten.types.numerics.ComplexType;
import tungsten.types.numerics.IntegerType;
import tungsten.types.numerics.RationalType;
import tungsten.types.numerics.RealType;
import tungsten.types.numerics.impl.ComplexRectImpl;
import tungsten.types.numerics.impl.IntegerImpl;
import tungsten.types.numerics.impl.RationalImpl;
import tungsten.types.numerics.impl.RealImpl;
import tungsten.types.util.OptionalOperations;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.ParameterizedType;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ArgMap<T extends Numeric> extends HashMap<String, T> {
    public ArgMap() {
        super();
    }

    public ArgMap(Map<String, T> source) {
        super(source);
    }

    /**
     * Construct a map of var names to bound values.
     * This constructor takes a formatted {@link String} and
     * populates this {@link Map} with one or more variable
     * mappings parsed from the input.
     * <br/>
     * The accepted format is: [a:1,b:5,c:2.7]
     * <br/>
     * Square brackets are optional.
     *
     * @param init the formatted string specifying variable mappings
     */
    public ArgMap(String init) {
        super();
        final Class<T> clazz = (Class<T>) ((Class) ((ParameterizedType) getClass()
                .getGenericSuperclass()).getActualTypeArguments()[0]);
        String stripped = init.strip();
        if (stripped.startsWith("[") && stripped.endsWith("]")) {
            stripped = stripped.substring(1, stripped.length() - 1);
        }
        String[] mappings = stripped.split("\\s*,\\s*");
        for (String mapping : mappings) {
            int colonPos = mapping.indexOf(':');
            if (colonPos < 1) throw new IllegalArgumentException("Bad mapping format: " + mapping);
            final String varName = mapping.substring(0, colonPos).strip();
            String strValue = mapping.substring(colonPos + 1);
            put(varName, OptionalOperations.dynamicInstantiate(clazz, strValue));
        }
    }

    public long arity() {
        // size may not be accurate for very large maps, so do it this way
        return this.keySet().stream().count();
    }
}
