/* 
 * The MIT License
 *
 * Copyright © 2018 Robert Poole <Tarquin.AZ@gmail.com>.
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
package tungsten.types.numerics;

import tungsten.types.Numeric;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

/**
 * The enum that defines the hierarchy of most numeric types.
 *
 * @author Robert Poole, <a href="mailto:Tarquin.AZ+Tungsten@gmail.com">Gmail</a>
 */
public enum NumericHierarchy {
    INTEGER(IntegerType.class),
    RATIONAL(RationalType.class),
    REAL(RealType.class),
    COMPLEX(ComplexType.class);
    private final Class<? extends Numeric> clazz;
    private static final Map<Class<? extends Numeric>, NumericHierarchy> typeMap =
            new HashMap<>();

    static {
        for (NumericHierarchy hval : NumericHierarchy.values()) {
            typeMap.put(hval.getNumericType(), hval);
        }
    }
    
    NumericHierarchy(Class<? extends Numeric> clazz) {
        this.clazz = clazz;
    }

    /**
     * Obtain the base {@code Numeric} type for this value.
     * @return a {@code Class<? extends Numeric>} associated with this hierarchy value
     */
    public Class<? extends Numeric> getNumericType() {
        return clazz;
    }

    /**
     * Obtain a value for a given {@code Numeric} subtype.
     * @param clazz the {@code Numeric} subtype
     * @return the associated value
     */
    public static NumericHierarchy forNumericType(Class<? extends Numeric> clazz) {
        NumericHierarchy retval;
        
        retval = typeMap.get(clazz);
        if (retval == null && clazz != Numeric.class) {
            Class<?>[] interfaces = clazz.getInterfaces();
            for (Class<?> iclass : interfaces) {
                retval = typeMap.get(iclass);
                if (retval != null) break;
            }
            // if there were no interfaces, or we found no matches,
            // recurse up the class hierarchy
            if (interfaces.length == 0 || retval == null) {
                if (clazz.getSuperclass() != null) {
                    retval = forNumericType((Class<? extends Numeric>) clazz.getSuperclass());
                }
            }
        }
        
        return retval;
    }

    /**
     * Obtain an instance of a {@code Comparator} for comparison of
     * type information to determine which type is lower/higher in
     * the numeric hierarchy.
     * @return a {@code Comparator} instance
     */
    public static Comparator<Class<? extends Numeric>> obtainTypeComparator() {
        return new Comparator<>() {
            @Override
            public int compare(Class<? extends Numeric> o1, Class<? extends Numeric> o2) {
                NumericHierarchy htype1 = forNumericType(o1);
                NumericHierarchy htype2 = forNumericType(o2);
                // if o1 or o2 is Numeric itself, we get a null back, so handle the null case
                if (htype1 == null && htype2 == null) return 0;
                else if (htype1 == null) return -1;
                else if (htype2 == null) return 1;
                return htype1.compareTo(htype2);
            }

            @Override
            public boolean equals(Object obj) {
                if (obj instanceof Comparator) {
                    return obj.getClass() == this.getClass();
                }
                return false;
            }
        };
    }
}
