package dev.simplecore.simplix.web.meta.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/** One serialized property of a DTO. {@code name} is the wire name after Jackson. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record FieldMeta(
        String name,
        TypeRef type,
        boolean required,
        boolean nullable,
        String description,
        String labelKey,
        String label,
        List<ConstraintMeta> constraints,
        SearchableMeta searchable) {}
