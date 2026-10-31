package dev.simplecore.simplix.web.meta.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Map;

/**
 * One entity served by a {@code SimpliXBaseController}. The display name, plural and description
 * come from the entity label bundles ({@code entities.<Entity>}, {@code entities.<Entity>._plural}
 * and {@code entities.<Entity>._description}), resolved for every locale the bundles ship; a
 * locale whose bundle lacks the key is omitted, and a key no locale carries is omitted whole.
 * {@code nameField} is the entity field marked {@code @NameField}, the field a record is named
 * by on a screen; it is absent when the entity marks none.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record EntityMeta(
        String javaClass,
        String idField,
        String nameField,
        String labelKey,
        String pluralKey,
        String descriptionKey,
        Map<String, String> label,
        Map<String, String> plural,
        Map<String, String> description,
        List<HolderMeta> holders) {}
