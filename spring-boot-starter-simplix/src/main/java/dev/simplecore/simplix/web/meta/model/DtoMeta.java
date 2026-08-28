package dev.simplecore.simplix.web.meta.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Map;

/** Root IR document served by {@code GET /dev/meta/dto}. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DtoMeta(
        int version,
        Map<String, EnumMeta> enums,
        Map<String, TypeMeta> types,
        List<OperationMeta> operations,
        Map<String, Object> extensions) {}
