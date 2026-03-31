package dev.simplecore.simplix.egov.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "simplix.egov")
@Getter
@Setter
public class SimpliXEgovProperties {
    /**
     * Whether to enable SimpliX eGov auto-configuration.
     * Default: true
     */
    private boolean enabled = true;
}
