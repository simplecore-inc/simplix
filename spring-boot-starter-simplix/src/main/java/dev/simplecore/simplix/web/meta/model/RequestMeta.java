package dev.simplecore.simplix.web.meta.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/** Request side of an operation. {@code searchDto} names the DTO whose
 * {@code @SearchableField}s define this operation's flattened search params.
 *
 * <p>{@code body} is a {@link TypeRef} rather than a type name because a body is frequently a
 * generic container — {@code Set<XUpdateDTO>} for a multi-update, {@code List<XOrderDTO>} for a
 * reorder. A bare name erases the element type, leaving a client generator with nothing to emit. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RequestMeta(TypeRef body, String contentType, List<ParamMeta> query, List<ParamMeta> path, String searchDto) {}
