package dev.simplecore.simplix.web.meta.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Map;

/**
 * Search capability of a field, read from {@code @SearchableField}.
 *
 * <p>{@code sortAlias} maps a language code to the sortable sibling that orders this field in
 * that language ({@code roleName} to {@code roleNameSortEn}, {@code roleNameSortKo}, ...). Its
 * presence says the visible field is sorted through those siblings: a client sorts by the
 * visible field's own name, and the server moves the order onto the reader's language.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SearchableMeta(List<String> operators, boolean sortable, String entityField, String sortField,
                             Map<String, String> sortAlias) {

    public SearchableMeta(List<String> operators, boolean sortable, String entityField, String sortField) {
        this(operators, sortable, entityField, sortField, null);
    }

    public SearchableMeta withSortAlias(Map<String, String> value) {
        return new SearchableMeta(operators, sortable, entityField, sortField, value);
    }
}
