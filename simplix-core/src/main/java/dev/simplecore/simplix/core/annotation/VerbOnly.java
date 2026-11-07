package dev.simplecore.simplix.core.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The field's value moves only through a verb of its own (enable and disable, publish and
 * withdraw), never through the record's create or update form.
 *
 * <p>Placed on the entity field, it reaches every DTO field mapped to that entity field and SimpliX
 * Meta publishes {@code verbOnly: true} on each, so a client leaves the field out of its forms and
 * offers the verb instead. The server keeps the promise on its side too: the mapper SimpliX
 * configures never copies a request onto a verb-only entity field, so a create starts the field at
 * the entity's own default (which Meta publishes as {@code defaultValue}) and an update keeps the
 * stored value. The verb writes the field through its setter.
 *
 * <p>Placed on a DTO field, it marks a derived value the same way for Meta only. A request DTO that
 * requires a verb-only field ({@code @NotNull}, {@code @NotBlank}, {@code @NotEmpty}) fails the Meta
 * build naming the field, since a client cannot be required to send a value the server ignores.
 */
@Documented
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface VerbOnly {
}
