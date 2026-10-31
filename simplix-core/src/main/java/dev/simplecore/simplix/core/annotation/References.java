package dev.simplecore.simplix.core.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares that an entity field holds the identifier, or a collection of identifiers, of another
 * entity (or another unique field of it, named by {@link #field()}), where the JPA mapping does not say so itself (for example an {@code @ElementCollection}
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

    /**
     * The referenced entity's field the value matches, where it is not the identifier: a voice
     * code that names a catalogue voice by its code. Empty means the referenced entity's identifier.
     */
    String field() default "";
}
