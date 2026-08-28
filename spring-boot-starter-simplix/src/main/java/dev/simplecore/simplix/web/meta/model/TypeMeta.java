package dev.simplecore.simplix.web.meta.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/** One DTO type; carries ONLY its own fields — inherited ones live on {@code extends}. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TypeMeta(
        String javaClass,
        @JsonProperty("extends") String extendsType,
        List<String> typeParams,
        String description,
        List<FieldMeta> fields) {}
