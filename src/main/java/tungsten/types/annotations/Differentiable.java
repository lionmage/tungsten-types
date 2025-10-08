package tungsten.types.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation used to mark a method as computing
 * the derivative of a function.
 * This annotation should only be used on methods
 * belonging to a {@code NumericFunction}.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Differentiable {
}
