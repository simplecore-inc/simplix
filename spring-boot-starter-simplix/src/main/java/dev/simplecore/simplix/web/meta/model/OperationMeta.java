package dev.simplecore.simplix.web.meta.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/** One handler method. {@code response} is the full container-wrapped TypeRef; null = Void. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OperationMeta(
        String id,
        String method,
        String path,
        String tag,
        String summary,
        RequestMeta request,
        TypeRef response,
        AccessMeta access) {}
