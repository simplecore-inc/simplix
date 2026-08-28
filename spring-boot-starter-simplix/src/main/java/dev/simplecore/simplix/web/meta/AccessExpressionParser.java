package dev.simplecore.simplix.web.meta;

import dev.simplecore.simplix.web.meta.model.AccessMeta;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns the three {@code @PreAuthorize} shapes this convention uses into structured
 * {@link AccessMeta}; anything else travels as a raw expression so the frontend can refuse to
 * guess instead of misreading it as "no permission required".
 */
public final class AccessExpressionParser {

    private static final Pattern HAS_PERMISSION =
            Pattern.compile("^hasPermission\\('([^']+)'\\s*,\\s*'([^']+)'\\)$");

    private AccessExpressionParser() {}

    public static AccessMeta parse(String expression) {
        if (expression == null || expression.isBlank()) {
            return AccessMeta.authenticated();
        }
        String trimmed = expression.trim();
        Matcher permission = HAS_PERMISSION.matcher(trimmed);
        if (permission.matches()) {
            return AccessMeta.permission(permission.group(1), permission.group(2));
        }
        if (trimmed.equals("isAuthenticated()")) {
            return AccessMeta.authenticated();
        }
        if (trimmed.equals("permitAll()")) {
            return AccessMeta.publicAccess();
        }
        return AccessMeta.expression(trimmed);
    }
}
