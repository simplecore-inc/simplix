package dev.simplecore.simplix.core.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The fields a record is named by, in order, declared on the entity rather than on each field. It
 * is the form for a name that takes a field the entity inherits (a creation time on a shared base
 * class), which a {@link NameField} mark on that field would give every entity sharing the base.
 * SimpliX Meta publishes the list as the entity's {@code nameFields}, exactly as it publishes
 * {@link NameField} marks; an entity declares one form or the other, never both.
 *
 * <pre>{@code
 * @Entity
 * @NameFields({"jobType", "createdAt"})
 * public class BackgroundJob extends BaseEntity<String> { ... }
 * }</pre>
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface NameFields {

    /** The names of the fields, own or inherited, in the order a screen joins them. */
    String[] value();
}
