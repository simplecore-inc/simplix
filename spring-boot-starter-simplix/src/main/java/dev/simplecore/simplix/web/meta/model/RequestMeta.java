package dev.simplecore.simplix.web.meta.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/** Request side of an operation. {@code searchDto} names the DTO whose
 * {@code @SearchableField}s define this operation's flattened search params. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RequestMeta(String body, String contentType, List<ParamMeta> query, List<ParamMeta> path, String searchDto) {}
