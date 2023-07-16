package tungsten.annotations;

import java.lang.annotation.*;

/**
 * Annotation to denote that an implementation of {@link tungsten.types.numerics.ComplexType}
 * is stored internally as or should be treated as a polar instead of rectangular complex value.
 * This may be useful for cases where a polar form is preferable for a given operation
 * (e.g., exponentiation, multiplication).
 */
@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Polar {
    // Currently, there are no values associated with this annotation.
}
