package dev.simplecore.simplix.web.meta.model;

/** One {@code hasPermission('GROUP', 'action')} term found inside an access expression. */
public record PermissionTermMeta(String group, String action) {}
