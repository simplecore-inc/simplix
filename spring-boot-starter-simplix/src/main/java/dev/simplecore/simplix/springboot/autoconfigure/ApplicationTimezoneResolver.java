package dev.simplecore.simplix.springboot.autoconfigure;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;

import java.time.ZoneId;

/** Single source of truth for the application timezone, resolved from one canonical property. */
@Slf4j
final class ApplicationTimezoneResolver {

    static final String CANONICAL_KEY = "simplix.date-time.default-timezone";
    private static final String[] DEPRECATED_ALIASES = {"spring.jackson.time-zone", "user.timezone"};

    private ApplicationTimezoneResolver() {
    }

    static ZoneId resolve(Environment environment) {
        ZoneId canonical = parse(environment.getProperty(CANONICAL_KEY));
        if (canonical != null) {
            return canonical;
        }
        for (String alias : DEPRECATED_ALIASES) {
            ZoneId z = parse(environment.getProperty(alias));
            if (z != null) {
                log.warn("Application timezone resolved from deprecated key '{}'; set '{}' instead.",
                        alias, CANONICAL_KEY);
                return z;
            }
        }
        ZoneId systemDefault = ZoneId.systemDefault();
        log.warn("No '{}' configured; falling back to JVM default timezone {}. Configure the canonical key explicitly.",
                CANONICAL_KEY, systemDefault);
        return systemDefault;
    }

    private static ZoneId parse(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        try {
            return ZoneId.of(value);
        } catch (Exception e) {
            log.warn("Invalid timezone value '{}', ignoring.", value);
            return null;
        }
    }
}
