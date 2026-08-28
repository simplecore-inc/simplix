package dev.simplecore.simplix.web.meta.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Structured {@code @PreAuthorize} so the frontend never parses SpEL. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AccessMeta(String kind, String group, String action, String raw) {

    public static AccessMeta permission(String group, String action) {
        return new AccessMeta("permission", group, action, null);
    }

    public static AccessMeta authenticated() { return new AccessMeta("authenticated", null, null, null); }

    public static AccessMeta publicAccess() { return new AccessMeta("public", null, null, null); }

    public static AccessMeta expression(String raw) { return new AccessMeta("expression", null, null, raw); }
}
