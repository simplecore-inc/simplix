package dev.simplecore.simplix.core.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares that an entity field holds the identifier of one of several entities, and that a
 * sibling field of the same entity says which: an owner id that names a user or a team according to
 * an owner type. SimpliX Meta publishes the kind field and the entity each kind value names, so a
 * screen opens the referenced record of the right entity per row.
 *
 * <p>Targets are named by the entity's simple name (a key of SimpliX Meta's {@code entities})
 * rather than by class, because the entities one field may name usually live in modules the
 * holding entity does not depend on. A kind whose entity SimpliX Meta serves no surface for has
 * nothing to open, and a generator draws its records by name alone.
 *
 * <p>No reverse relation is derived from it: a holder list filtered by the id alone would mix the
 * records of every kind.
 */
@Documented
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ReferencesByKind {

    /** The sibling field whose value picks the referenced entity, usually an enum. */
    String kindField();

    /** The entity each kind value names. */
    Kind[] kinds();

    /**
     * The property of the holding record that carries the referenced record's name, whichever kind
     * it is ({@code actorUsername} beside an {@code actorId}); a dotted path reaches into a nested
     * record. Read as {@link References#nameField()} is. Empty when none is carried.
     */
    String nameField() default "";

    /** One kind value and the entity it names. */
    @Documented
    @Retention(RetentionPolicy.RUNTIME)
    @Target({})
    @interface Kind {

        /** The kind field's value, the enum constant's name for an enum. */
        String value();

        /** The referenced entity's simple name. */
        String entity();
    }
}
