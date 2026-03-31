package dev.simplecore.simplix.egov.autoconfigure;

import dev.simplecore.simplix.egov.config.SimpliXEgovConfiguration;
import dev.simplecore.simplix.egov.config.SimpliXEgovProperties;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;

/**
 * Auto-configuration for SimpliX eGov module.
 * Activated when EgovAbstractServiceImpl is on the classpath.
 */
@AutoConfiguration
@ConditionalOnClass(name = "org.egovframe.rte.fdl.cmmn.EgovAbstractServiceImpl")
@ConditionalOnProperty(name = "simplix.egov.enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(SimpliXEgovProperties.class)
@Import(SimpliXEgovConfiguration.class)
@Slf4j
public class SimpliXEgovAutoConfiguration {

    @PostConstruct
    public void init() {
        log.info("SimpliX eGovFrame module initialized");
    }
}
