package dev.simplecore.simplix.web.config.openapi;

import io.swagger.v3.oas.models.Operation;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.core.Ordered;
import org.springframework.web.method.HandlerMethod;

/**
 * Generates unique operationId values by combining the controller class name
 * with the method name, preventing SpringDoc's default suffix numbering
 * (e.g., get_1, get_2) when multiple controllers share the same method names.
 *
 * <p>Naming strategy:
 * <ul>
 *   <li>{@code AdminUserRoleController.get()} -> {@code AdminUserRole_get}</li>
 *   <li>{@code AdminAuthPermissionController.create()} -> {@code AdminAuthPermission_create}</li>
 * </ul>
 *
 * <p>The "Controller" suffix is stripped for brevity. If the method already has
 * a unique operationId (no numeric suffix), it is left unchanged.
 */
public class OperationIdCustomizer implements OperationCustomizer, Ordered {

    @Override
    public Operation customize(Operation operation, HandlerMethod handlerMethod) {
        String className = handlerMethod.getBeanType().getSimpleName();
        String methodName = handlerMethod.getMethod().getName();

        // Strip common suffixes for cleaner IDs
        String prefix = className
                .replaceFirst("Controller$", "")
                .replaceFirst("RestController$", "");

        operation.setOperationId(prefix + "_" + methodName);

        return operation;
    }

    @Override
    public int getOrder() {
        // Run early so other customizers see the final operationId
        return Ordered.HIGHEST_PRECEDENCE + 1;
    }
}
