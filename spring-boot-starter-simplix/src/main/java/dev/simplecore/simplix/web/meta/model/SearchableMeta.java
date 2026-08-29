package dev.simplecore.simplix.web.meta.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/** Search capability of a field, read from {@code @SearchableField}. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SearchableMeta(List<String> operators, boolean sortable, String entityField, String sortField) {}
