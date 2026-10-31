package dev.simplecore.simplix.core.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The field holds a code a reader types or compares character by character (a role code, a
 * username, a setting key), which a screen draws in the monospaced face. SimpliX Meta publishes
 * it as {@code format: "code"}. Placed on the entity field, it reaches every DTO field mapped to
 * that entity field; placed on a DTO field, it covers a derived value with no column of its own.
 */
@Documented
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface CodeValue {
}
