package dev.simplecore.simplix.web.meta.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/** One validation constraint. {@code custom} carries the annotation's simple name. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ConstraintMeta(String kind, Object value, String name) {

    public static ConstraintMeta of(String kind) { return new ConstraintMeta(kind, null, null); }

    public static ConstraintMeta of(String kind, Object value) { return new ConstraintMeta(kind, value, null); }

    public static ConstraintMeta custom(String name) { return new ConstraintMeta("custom", null, name); }
}
