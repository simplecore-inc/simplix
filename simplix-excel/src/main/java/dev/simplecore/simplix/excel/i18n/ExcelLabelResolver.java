/*
 * Copyright (c) 2025 SimpleCORE
 * Licensed under the SimpleCORE License 1.0 (see LICENSE)
 * Use allowed in own products. Redistribution or resale requires permission.
 */
package dev.simplecore.simplix.excel.i18n;

import java.util.Locale;

/**
 * Resolves one message key an exported file needs, for applications whose label bundles are not
 * reachable through the application {@code MessageSource}.
 *
 * <p>Entity-label and enum-label bundles are commonly merged across the classpath from a wildcard
 * pattern, which a {@code ReloadableResourceBundleMessageSource} basename list cannot express. An
 * application that keeps its labels that way registers one of these as a bean; every registered
 * resolver is consulted, in bean order, before the {@code MessageSource}.
 *
 * <p>Implementations return {@code null} for a key they do not own, so several resolvers can share
 * the space by prefix.
 */
@FunctionalInterface
public interface ExcelLabelResolver {

    /**
     * @param key the message key, without braces
     * @param locale the language the file is being written in
     * @return the text, or {@code null} when this resolver does not own the key
     */
    String resolve(String key, Locale locale);
}
