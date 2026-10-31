package dev.simplecore.simplix.core.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The field a record is named by on a screen: the first column of its list, the name in a delete
 * confirmation, the title of its detail. SimpliX Meta publishes it as the entity's
 * {@code nameField}, so a client reads the name from the entity instead of guessing at the first
 * text field.
 *
 * <p>An entity declares at most one. A field with an {@code …I18n} sibling is named by its base
 * field, and the screen shows the text in the reader's language.
 *
 * <pre>{@code
 * @Entity
 * public class ReleaseArtifact extends BaseEntity<String> {
 *     @NameField
 *     @Column(name = "artifact_name")
 *     private String artifactName;
 * }
 * }</pre>
 */
@Documented
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface NameField {
}
