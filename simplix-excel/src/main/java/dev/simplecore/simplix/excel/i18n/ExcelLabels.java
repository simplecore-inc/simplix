/*
 * Copyright (c) 2025 SimpleCORE
 * Licensed under the SimpleCORE License 1.0 (see LICENSE)
 * Use allowed in own products. Redistribution or resale requires permission.
 */
package dev.simplecore.simplix.excel.i18n;

import dev.simplecore.simplix.core.enums.SimpliXLabeledEnum;
import dev.simplecore.simplix.excel.properties.SimplixExcelProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;

import java.util.List;
import java.util.Locale;

/**
 * The words an exported file is written with.
 *
 * <p>A file is read by the same person who read the screen, so it has to say what the screen
 * said. A header row of message codes and a status column of constant names are a database dump,
 * not the list somebody asked to take away.
 *
 * <p>Static rather than injected because the exporters are plain objects an application creates
 * with {@code new}, one per export, and threading a message source through every factory call
 * would put the burden on every caller. This mirrors {@code ValueFormatter.configure} and
 * {@code ExcelConverter.configure}, which the module's auto-configuration already drives the same
 * way.
 *
 * <p>Resolution order is: every registered {@link ExcelLabelResolver}, then the application
 * {@code MessageSource}, then the caller's own literal. Nothing here throws — a file that fails
 * because one label is untranslated is worse than one that carries the key.
 */
@Slf4j
public final class ExcelLabels {

    /** Where an enum constant's label lives, by convention across SimpliX applications. */
    private static final String ENUM_KEY_PREFIX = "enums.";

    /** What a column name is wrapped in when it is a key rather than the words themselves. */
    private static final char PLACEHOLDER_OPEN = '{';

    /** The closing half of the same wrapper. */
    private static final char PLACEHOLDER_CLOSE = '}';

    /** What a flag reads as when nothing configures it, which is what CSV has always written. */
    private static final String DEFAULT_TRUE = "Y";

    /** The other half of the same default. */
    private static final String DEFAULT_FALSE = "N";

    private static volatile MessageSource messageSource;
    private static volatile List<ExcelLabelResolver> resolvers = List.of();
    private static volatile String trueLabel = DEFAULT_TRUE;
    private static volatile String falseLabel = DEFAULT_FALSE;

    private ExcelLabels() {
        // Prevent instantiation
    }

    /**
     * Points the resolver at an application's own messages.
     *
     * @param source the application message source, or null when there is none
     * @param labelResolvers resolvers consulted before the message source, in order
     * @param format the module's format settings, whose boolean words flags are written with
     */
    public static void configure(MessageSource source, List<ExcelLabelResolver> labelResolvers,
                                 SimplixExcelProperties.FormatProperties format) {
        messageSource = source;
        resolvers = labelResolvers == null ? List.of() : List.copyOf(labelResolvers);
        if (format != null) {
            trueLabel = format.getBooleanTrueValue();
            falseLabel = format.getBooleanFalseValue();
        }
        log.debug("Excel labels configured: messageSource={}, resolvers={}",
                source != null, resolvers.size());
    }

    /**
     * Puts the resolver back to how it starts, so one test cannot decide another's answers.
     */
    public static void reset() {
        messageSource = null;
        resolvers = List.of();
        trueLabel = DEFAULT_TRUE;
        falseLabel = DEFAULT_FALSE;
    }

    /**
     * The words one column is headed with.
     *
     * @param name the {@code @ExcelColumn} name — a {@code {key}} placeholder is resolved in the
     *             current locale, and anything else is the heading itself
     * @return the heading
     */
    public static String columnName(String name) {
        if (name == null) {
            return "";
        }
        String key = placeholderKey(name);
        if (key == null) {
            return name;
        }
        String resolved = resolve(key);
        // A key nobody translated reads as the key rather than as the key in braces: the braces
        // say "this is a placeholder", and by here nothing is going to expand it.
        return resolved == null ? key : resolved;
    }

    /**
     * What one enum constant reads as.
     *
     * @param value the constant, which may be absent
     * @return its label in the current locale, or its own name when nothing labels it
     */
    public static String enumLabel(Enum<?> value) {
        if (value == null) {
            return "";
        }
        // getDeclaringClass, not getClass: a constant with a body is an anonymous subclass whose
        // simple name is empty, and every such constant would ask for the same broken key.
        String key = ENUM_KEY_PREFIX + value.getDeclaringClass().getSimpleName() + "." + value.name();
        String resolved = resolve(key);
        if (resolved != null) {
            return resolved;
        }
        if (value instanceof SimpliXLabeledEnum labeled) {
            return labeled.getLabel();
        }
        return value.name();
    }

    /**
     * What one flag reads as.
     *
     * <p>Written the same way in every format the module produces. A spreadsheet cell holding
     * {@code TRUE} beside a comma-separated file holding {@code Y} is one list read two ways.
     *
     * @param value the flag
     * @return the configured word, itself resolvable as a {@code {key}} placeholder
     */
    public static String booleanLabel(boolean value) {
        return columnName(value ? trueLabel : falseLabel);
    }

    /**
     * @param name any column name
     * @return the key inside its braces, or null when it is not a placeholder
     */
    private static String placeholderKey(String name) {
        if (name.length() < 3
                || name.charAt(0) != PLACEHOLDER_OPEN
                || name.charAt(name.length() - 1) != PLACEHOLDER_CLOSE) {
            return null;
        }
        return name.substring(1, name.length() - 1);
    }

    /**
     * @param key the message key
     * @return the text, or null when nothing has it
     */
    private static String resolve(String key) {
        Locale locale = LocaleContextHolder.getLocale();
        for (ExcelLabelResolver resolver : resolvers) {
            String text = resolver.resolve(key, locale);
            if (usable(text, key)) {
                return text;
            }
        }
        MessageSource source = messageSource;
        if (source != null) {
            String text = source.getMessage(key, null, null, locale);
            if (usable(text, key)) {
                return text;
            }
        }
        return null;
    }

    /**
     * @param text what a resolver answered
     * @param key what was asked for
     * @return whether that answer is a translation rather than a resolver echoing the key back,
     *         which is how the merged-bundle resolvers report a miss
     */
    private static boolean usable(String text, String key) {
        return text != null && !text.isEmpty() && !text.equals(key);
    }
}
