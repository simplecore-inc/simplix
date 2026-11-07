package dev.simplecore.simplix.core.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The field holds a size in bytes, which a screen draws in the largest unit that keeps the figure
 * readable (KB, MB, GB). SimpliX Meta publishes it as {@code format: "bytes"}. Placed on the entity
 * field, it reaches every DTO field mapped to that entity field; placed on a DTO field, it covers a
 * derived value with no column of its own.
 */
@Documented
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface BytesValue {
}
