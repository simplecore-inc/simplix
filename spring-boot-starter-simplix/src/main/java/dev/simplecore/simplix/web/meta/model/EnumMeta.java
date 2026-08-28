package dev.simplecore.simplix.web.meta.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/** Enum registry entry. {@code labeled} marks the {@code {"value":"...","label":"..."}} wire shape. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record EnumMeta(boolean labeled, List<Value> values) {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Value(String name, String labelKey) {}
}
