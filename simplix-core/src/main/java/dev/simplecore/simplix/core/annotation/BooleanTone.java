package dev.simplecore.simplix.core.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The tone of each value of a boolean flag. Placed on the entity field, it reaches every DTO
 * field mapped to that entity field; placed on a DTO field, it covers a derived flag with no
 * column of its own, and wins over the entity's.
 */
@Documented
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface BooleanTone {

    ToneKind whenTrue();

    ToneKind whenFalse();
}
