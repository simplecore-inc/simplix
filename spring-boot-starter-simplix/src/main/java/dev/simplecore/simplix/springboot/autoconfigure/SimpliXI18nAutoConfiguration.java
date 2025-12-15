package dev.simplecore.simplix.springboot.autoconfigure;

import dev.simplecore.simplix.core.config.SimpliXI18nConfigHolder;
import dev.simplecore.simplix.core.config.SimpliXI18nProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * Auto-configuration for SimpliX I18n translation.
 * <p>
 * Initializes the I18n configuration holder with properties from application.yml.
 * <p>
 * Configuration example:
 * <pre>{@code
 * simplix:
 *   i18n:
 *     default-locale: en
 *     supported-locales:
 *       - en
 *       - ko
 *       - ja
 * }</pre>
 */
@Slf4j
@AutoConfiguration
@EnableConfigurationProperties(SimpliXI18nProperties.class)
@RequiredArgsConstructor
public class SimpliXI18nAutoConfiguration {

    private final SimpliXI18nProperties properties;

    @PostConstruct
    public void initialize() {
        SimpliXI18nConfigHolder.initialize(properties);
        log.info("SimpliX I18n configured - defaultLocale: {}, supportedLocales: {}",
                properties.getDefaultLocale(),
                properties.getSupportedLocales());
    }
}