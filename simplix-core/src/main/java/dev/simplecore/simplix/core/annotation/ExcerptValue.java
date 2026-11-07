package dev.simplecore.simplix.core.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The field holds text written by a machine for a reader to copy rather than read as prose: an
 * error message, a stack trace, a log tail, an idempotency key, a hash, a storage path. A screen
 * draws it whole in the monospaced face with a copy action instead of cutting it to a cell. SimpliX
 * Meta publishes it as {@code format: "excerpt"}. Placed on the entity field, it reaches every DTO
 * field mapped to that entity field; placed on a DTO field, it covers a derived value with no column
 * of its own.
 */
@Documented
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ExcerptValue {
}
