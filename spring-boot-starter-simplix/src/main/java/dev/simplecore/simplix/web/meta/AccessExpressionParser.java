package dev.simplecore.simplix.web.meta;

import dev.simplecore.simplix.web.meta.model.AccessMeta;
import dev.simplecore.simplix.web.meta.model.PermissionTermMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns the three {@code @PreAuthorize} shapes this convention uses
 * ({@code hasPermission('GROUP','action')} single-quoted, {@code isAuthenticated()} and
 * {@code permitAll()}) into structured {@link AccessMeta}. Anything else travels as a raw
 * expression so the frontend can refuse to guess instead of misreading it as "no permission
 * required"; when that expression is a flat chain of one operator ({@code or} or {@code and}),
 * the {@code hasPermission} terms in the chain are published beside the raw text.
 */
public final class AccessExpressionParser {

    private static final Pattern HAS_PERMISSION =
            Pattern.compile("^hasPermission\\('([^']+)'\\s*,\\s*'([^']+)'\\)$");

    private AccessExpressionParser() {}

    public static AccessMeta parse(String expression) {
        // No @PreAuthorize under an authenticated-by-default filter chain means authenticated access.
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
        return chain(trimmed);
    }

    /**
     * Splits a top-level chain. Parentheses and quoted literals are skipped, so a nested group
     * stays one term; a chain mixing {@code or} and {@code and} at the top level carries no terms,
     * because its meaning depends on precedence the client should not re-derive.
     */
    private static AccessMeta chain(String expression) {
        List<String> terms = new ArrayList<>();
        String operator = null;
        int depth = 0;
        boolean quoted = false;
        int start = 0;
        for (int i = 0; i < expression.length(); i++) {
            char c = expression.charAt(i);
            if (c == '\'') quoted = !quoted;
            if (quoted) continue;
            if (c == '(') depth++;
            else if (c == ')') depth--;
            else if (depth == 0 && Character.isWhitespace(c)) {
                String found = operatorAt(expression, i);
                if (found != null) {
                    if (operator != null && !operator.equals(found)) {
                        return AccessMeta.expression(expression);
                    }
                    operator = found;
                    terms.add(expression.substring(start, i).trim());
                    i += found.length() + 1;
                    start = i + 1;
                }
            }
        }
        if (operator == null) return AccessMeta.expression(expression);
        terms.add(expression.substring(start).trim());

        List<PermissionTermMeta> permissions = new ArrayList<>();
        for (String term : terms) {
            Matcher matcher = HAS_PERMISSION.matcher(term);
            if (matcher.matches()) {
                permissions.add(new PermissionTermMeta(matcher.group(1), matcher.group(2)));
            }
        }
        return AccessMeta.expression(expression, operator, permissions);
    }

    /** {@code or} / {@code and} framed by whitespace at {@code index}, case-insensitive as SpEL is. */
    private static String operatorAt(String expression, int index) {
        for (String candidate : new String[] {"or", "and"}) {
            int end = index + 1 + candidate.length();
            if (end < expression.length()
                    && expression.substring(index + 1, end).toLowerCase(Locale.ROOT).equals(candidate)
                    && Character.isWhitespace(expression.charAt(end))) {
                return candidate;
            }
        }
        return null;
    }
}
