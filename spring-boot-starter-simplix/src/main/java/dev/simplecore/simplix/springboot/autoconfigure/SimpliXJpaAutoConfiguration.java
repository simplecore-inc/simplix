package dev.simplecore.simplix.springboot.autoconfigure;

import dev.simplecore.simplix.springboot.converter.SimpliXLocalDateTimeConverter;
import dev.simplecore.simplix.springboot.converter.SimpliXOffsetDateTimeConverter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.context.annotation.Bean;

import javax.persistence.AttributeConverter;
import javax.persistence.EntityManagerFactory;

/**
 * Auto-configuration for JPA with SimpliX datetime converters.
 * Ensures that SimpliX AttributeConverters are automatically registered with JPA.
 */
@Slf4j
@AutoConfiguration(after = HibernateJpaAutoConfiguration.class)
@ConditionalOnClass({EntityManagerFactory.class, AttributeConverter.class})
@ConditionalOnProperty(prefix = "simplix.core", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SimpliXJpaAutoConfiguration {

    /**
     * Ensures SimpliX OffsetDateTime converter is available as a bean.
     * This helps with proper dependency injection and registration.
     */
    @Bean
    public SimpliXOffsetDateTimeConverter simplixOffsetDateTimeConverter() {
        SimpliXOffsetDateTimeConverter converter = new SimpliXOffsetDateTimeConverter();
        log.debug("Registered SimpliXOffsetDateTimeConverter for automatic OffsetDateTime timezone conversion");
        return converter;
    }

    /**
     * Ensures SimpliX LocalDateTime converter is available as a bean.
     * This helps with proper dependency injection and registration.
     */
    @Bean
    public SimpliXLocalDateTimeConverter simplixLocalDateTimeConverter() {
        SimpliXLocalDateTimeConverter converter = new SimpliXLocalDateTimeConverter();
        log.debug("Registered SimpliXLocalDateTimeConverter for automatic LocalDateTime to OffsetDateTime conversion");
        return converter;
    }
} 