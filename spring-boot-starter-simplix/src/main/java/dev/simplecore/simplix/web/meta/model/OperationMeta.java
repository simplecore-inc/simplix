package dev.simplecore.simplix.web.meta.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * One handler method. {@code response} is the full container-wrapped TypeRef; null = Void.
 * {@code entity} names the entity of a {@code SimpliXBaseController} subclass (a key of
 * {@link DtoMeta#entities()}); it is absent on a controller that serves no single entity.
 * {@code defaultSort} is the order the list a search operation serves opens in, read from its own
 * controller's {@code @SimpliXDefaultSort}: two controllers over one entity (an operators' list and
 * a person's own) each publish theirs here, where the entity's carries only one. {@code statusCounts}
 * and {@code aggregates} are that controller's list figures from {@code @SimpliXListFigures}, on its
 * search operations for the same reason, and {@code timeAxis} is the field its {@code GET /counts}
 * slices a window on.
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
        String entity,
        DefaultSortMeta defaultSort,
        List<String> statusCounts,
        List<AggregateMeta> aggregates,
        String timeAxis) {

    public OperationMeta(String id, String method, String path, String tag, String summary,
                         RequestMeta request, TypeRef response, AccessMeta access) {
        this(id, method, path, tag, summary, request, response, access, null, null, null, null, null);
    }

    public OperationMeta(String id, String method, String path, String tag, String summary,
                         RequestMeta request, TypeRef response, AccessMeta access, String entity) {
        this(id, method, path, tag, summary, request, response, access, entity, null, null, null, null);
    }

    public OperationMeta withDefaultSort(DefaultSortMeta value) {
        return new OperationMeta(id, method, path, tag, summary, request, response, access, entity, value,
                statusCounts, aggregates, timeAxis);
    }

    public OperationMeta withFigures(List<String> counts, List<AggregateMeta> figures, String axis) {
        return new OperationMeta(id, method, path, tag, summary, request, response, access, entity, defaultSort,
                counts, figures, axis);
    }
}
