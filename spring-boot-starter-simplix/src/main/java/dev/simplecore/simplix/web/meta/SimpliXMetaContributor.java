package dev.simplecore.simplix.web.meta;

import dev.simplecore.simplix.web.meta.model.OperationMeta;
import org.springframework.web.method.HandlerMethod;

import java.util.Map;

/**
 * Extension point for applications to add their own annotation data to SimpliX Meta.
 * Implementations register as Spring beans; whatever they put into {@code extensions} is served
 * under SimpliX Meta's top-level {@code extensions} object.
 */
public interface SimpliXMetaContributor {

    default void contributeType(Class<?> type, String typeName, Map<String, Object> extensions) {}

    default void contributeOperation(HandlerMethod handler, OperationMeta operation,
                                     Map<String, Object> extensions) {}
}
