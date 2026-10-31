package dev.simplecore.simplix.web.meta.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Structured {@code @PreAuthorize} so the frontend never parses SpEL.
 *
 * <p>A {@code kind: expression} keeps its {@code raw} SpEL and additionally carries the
 * {@code hasPermission} terms it contains in {@code permissions}, joined by {@code operator}
 * ({@code or} / {@code and}) when the expression is a flat chain of one operator. With
 * {@code operator: or} any one permission term opens the operation, and the remaining
 * non-permission terms (an ownership check, say) can only widen it, never narrow it.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AccessMeta(String kind, String group, String action, String raw,
                         String operator, List<PermissionTermMeta> permissions) {

    public AccessMeta(String kind, String group, String action, String raw) {
        this(kind, group, action, raw, null, null);
    }

    public static AccessMeta permission(String group, String action) {
        return new AccessMeta("permission", group, action, null);
    }

    public static AccessMeta authenticated() { return new AccessMeta("authenticated", null, null, null); }

    public static AccessMeta publicAccess() { return new AccessMeta("public", null, null, null); }

    public static AccessMeta expression(String raw) { return new AccessMeta("expression", null, null, raw); }

    public static AccessMeta expression(String raw, String operator, List<PermissionTermMeta> permissions) {
        return new AccessMeta("expression", null, null, raw, operator,
                permissions == null || permissions.isEmpty() ? null : List.copyOf(permissions));
    }
}
