package tungsten.types.annotations;

import java.lang.annotation.*;

@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Polar {
    // Currently, there are no values associated with this annotation.
}
