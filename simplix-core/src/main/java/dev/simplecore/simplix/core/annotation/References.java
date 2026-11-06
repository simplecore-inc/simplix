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
 * <p>The target is named in exactly one of two ways: by class ({@link #value()}), or by the
 * entity's simple name ({@link #entity()}), a key of SimpliX Meta's {@code entities}. The name
 * form exists because the entity a field names may live in a module the holding entity does not
 * depend on, and depending on it would close a cycle (an account module naming a billing plan the
 * billing module already reads accounts from). SimpliX Meta resolves the name among the entities
 * it serves; a name it serves none of, or serves more than one of, and a declaration naming both
 * or neither form, fail the Meta build with a message naming the annotated field.
 *
 * <p>A scalar column paired with a {@code @ManyToOne} over the same join column needs no
 * annotation; the emitter reads that pairing from JPA.
 */
@Documented
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface References {

    /** The referenced entity class; {@code void.class} when the target is named by {@link #entity()}. */
    Class<?> value() default void.class;

    /**
     * The referenced entity's simple name, where the holding entity's module cannot see its class.
     * Empty when the target is named by {@link #value()}.
     */
    String entity() default "";

    /**
     * The referenced entity's field the value matches, where it is not the identifier: a voice
     * code that names a catalogue voice by its code. Empty means the referenced entity's identifier.
     */
    String field() default "";
}
