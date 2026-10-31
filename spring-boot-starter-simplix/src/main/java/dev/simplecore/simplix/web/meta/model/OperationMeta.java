package dev.simplecore.simplix.web.meta.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * One handler method. {@code response} is the full container-wrapped TypeRef; null = Void.
 * {@code entity} names the entity of a {@code SimpliXBaseController} subclass (a key of
 * {@link DtoMeta#entities()}); it is absent on a controller that serves no single entity.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OperationMeta(
        String id,
        String method,
        String path,
        String tag,
        String summary,
        RequestMeta request,
        TypeRef response,
        AccessMeta access,
        String entity) {

    public OperationMeta(String id, String method, String path, String tag, String summary,
                         RequestMeta request, TypeRef response, AccessMeta access) {
        this(id, method, path, tag, summary, request, response, access, null);
    }
}
