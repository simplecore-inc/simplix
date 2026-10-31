package dev.simplecore.simplix.core.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares that an entity field holds the identifier, or a collection of identifiers, of another
 * entity, where the JPA mapping does not say so itself (for example an {@code @ElementCollection}
 * of id strings). SimpliX Meta reads it to publish the field's reference target, and from the
 * reference derives the reverse relation that lists the records holding a target.
 *
 * <p>A scalar column paired with a {@code @ManyToOne} over the same join column needs no
 * annotation; the emitter reads that pairing from JPA.
 */
@Documented
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface References {

    /** The referenced entity class. */
    Class<?> value();
}
