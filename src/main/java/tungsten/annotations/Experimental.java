package tungsten.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * An annotation intended to mark a method or class
 * as experimental, meaning it is subject to change
 * or removal.  There is a similar annotation present
 * in the Oracle JDK, but it is intended for use
 * specifically with Java Flight Recorder.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface Experimental {
}
