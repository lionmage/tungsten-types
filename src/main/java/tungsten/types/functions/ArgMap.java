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
import tungsten.types.util.ClassTools;
import tungsten.types.util.OptionalOperations;

import java.util.HashMap;
import java.util.Map;

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
     * <br>
     * The accepted format is: [a:1;b:5;c:2.7]
     * <br>
     * Square brackets are optional.
     *
     * @param init the formatted string specifying variable mappings
     */
    public ArgMap(String init) {
        super();
        // it would be nice to use the 2-arg version of this constructor and just pass in the Class
        // obtained from ClassTools.getTypeArguments(), but among other things, getClass() can't be
        // called before you call the superclass constructor
        final Class<T> clazz = (Class<T>) ClassTools.getTypeArguments(HashMap.class, getClass()).get(0);
        String stripped = init.strip();
        if (stripped.startsWith("[") && stripped.endsWith("]")) {
            stripped = stripped.substring(1, stripped.length() - 1);
        }
        String[] mappings = stripped.split("\\s*;\\s*");
        for (String mapping : mappings) {
            int colonPos = mapping.indexOf(':');
            if (colonPos < 1) throw new IllegalArgumentException("Bad mapping format: " + mapping);
            final String varName = mapping.substring(0, colonPos).strip();
            String strValue = mapping.substring(colonPos + 1);
            put(varName, OptionalOperations.dynamicInstantiate(clazz, strValue));
        }
    }

    /**
     * Construct a map of var names to bound values.
     * This constructor takes a formatted {@link String} and
     * a {@code Class<T>} to explicitly set the element type,
     * then populates this {@link Map} with one or more variable
     * mappings parsed from the input.
     * <br>
     * The accepted format is: [a:1;b:5;c:2.7]<br>
     * Semicolons are used to delimit each mapping to avoid
     * issues with different characters being used as decimal
     * points or grouping separators in different locales.
     * <br>
     * Square brackets are optional.
     *
     * @param init  the formatted string specifying variable mappings
     * @param clazz the {@code Numeric} type of this map's elements
     */
    public ArgMap(String init, Class<T> clazz) {
        super();
        String stripped = init.strip();
        if (stripped.startsWith("[") && stripped.endsWith("]")) {
            stripped = stripped.substring(1, stripped.length() - 1);
        }
        String[] mappings = stripped.split("\\s*;\\s*");
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
