package dev.simplecore.simplix.core.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A field a record is named by on a screen: the first column of its list, the name in a delete
 * confirmation, the title of its detail. SimpliX Meta publishes the marked fields as the entity's
 * {@code nameFields}, ordered by {@link #order()}, so a client reads the name from the entity
 * instead of guessing at the first text field.
 *
 * <p>A record whose one field repeats across records (a file name shared by every version) marks
 * each part of its name, and a client joins the parts in order. Two marks with the same order are
 * refused. A field with an {@code …I18n} sibling is named by its base field, and the screen shows
 * the text in the reader's language.
 *
 * <pre>{@code
 * @Entity
 * public class ReleaseArtifact extends BaseEntity<String> {
 *     @NameField
 *     @Column(name = "artifact_name")
 *     private String artifactName;
 *
 *     @NameField(order = 1)
 *     @Column(name = "release_version")
 *     private String releaseVersion;
 * }
 * }</pre>
 */
@Documented
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface NameField {

    /** The position of this field in the record's name, lowest first. */
    int order() default 0;
}
