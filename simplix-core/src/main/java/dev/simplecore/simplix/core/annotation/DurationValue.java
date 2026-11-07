package dev.simplecore.simplix.core.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The field holds a length of time in milliseconds, which a screen draws in the units that keep it
 * readable (1.2 s, 3 min 4 s). SimpliX Meta publishes it as {@code format: "duration"}. Placed on
 * the entity field, it reaches every DTO field mapped to that entity field; placed on a DTO field,
 * it covers a derived value with no column of its own.
 */
@Documented
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface DurationValue {
}
