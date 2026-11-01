package dev.simplecore.simplix.web.meta.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Map;

/**
 * One entity served by a {@code SimpliXBaseController}. The display name, plural and description
 * come from the entity label bundles ({@code entities.<Entity>}, {@code entities.<Entity>._plural}
 * and {@code entities.<Entity>._description}), resolved for every locale the bundles ship; a
 * locale whose bundle lacks the key is omitted, and a key no locale carries is omitted whole.
 * {@code nameFields} are the entity fields marked {@code @NameField} in their declared order, the
 * parts a record is named by on a screen; it is absent when the entity marks none.
 * {@code defaultSort} is the order its list opens in, absent when the controller declares none.
 * {@code statusCounts} are the fields its {@code GET /status-counts} accepts and {@code aggregates}
 * the figures its {@code GET /aggregates} returns, in order; each is absent when none is declared.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record EntityMeta(
        String javaClass,
        String idField,
        List<String> nameFields,
        String labelKey,
        String pluralKey,
        String descriptionKey,
        Map<String, String> label,
        Map<String, String> plural,
        Map<String, String> description,
        List<HolderMeta> holders,
        DefaultSortMeta defaultSort,
        List<String> statusCounts,
        List<AggregateMeta> aggregates) {}
