package dev.simplecore.simplix.springboot.autoconfigure;

import dev.simplecore.simplix.springboot.properties.SimpliXProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.core.annotation.Order;

/**
 * Root auto-configuration for SimpliX Framework.
 *
 * <p>The {@code autoconfigure} package is excluded from component scanning because
 * those classes are registered via {@code AutoConfiguration.imports} with explicit ordering.
 * Dual registration (component scan + auto-configuration import) causes ordering attributes
 * like {@code @AutoConfiguration(before = ...)} to be ignored for the component-scanned instance.
 */
@AutoConfiguration
@EnableConfigurationProperties(SimpliXProperties.class)
@ComponentScan(
        basePackages = {"dev.simplecore.simplix.web", "dev.simplecore.simplix.springboot"},
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.REGEX,
                pattern = "dev\\.simplecore\\.simplix\\.springboot\\.autoconfigure\\..*"
        )
)
@Order(0)
public class SimpliXAutoConfiguration {
    private static final Logger log = LoggerFactory.getLogger(SimpliXAutoConfiguration.class);
    private static boolean initialized = false;

    public SimpliXAutoConfiguration() {
        if (!initialized) {
            log.info("\n" +
                    "+------------------------------------------------+\n" +
                    "|          SimpliX Framework Auto Configuration    |\n" +
                    "+------------------------------------------------+");
            initialized = true;
        }
    }
} 

