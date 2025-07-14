package dev.simplecore.simplix.springboot.autoconfigure;

import lombok.extern.slf4j.Slf4j;
import org.modelmapper.Converter;
import org.modelmapper.ModelMapper;
import org.modelmapper.convention.MatchingStrategies;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;

/**
 * Auto-configuration for ModelMapper with timezone-aware datetime conversion.
 * Provides automatic conversion between LocalDateTime and OffsetDateTime during DTO mapping.
 */
@Slf4j
@AutoConfiguration
@ConditionalOnClass(ModelMapper.class)
@ConditionalOnProperty(prefix = "simplix.core", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SimpliXModelMapperAutoConfiguration {

    private SimpliXDateTimeAutoConfiguration.SimpliXTimezoneService timezoneService;

    @Autowired(required = false)
    public void setTimezoneService(SimpliXDateTimeAutoConfiguration.SimpliXTimezoneService timezoneService) {
        this.timezoneService = timezoneService;
    }

    @Bean
    @Primary
    @ConditionalOnMissingBean
    public ModelMapper modelMapper() {
        ModelMapper mapper = new ModelMapper();
        
        // Configure ModelMapper for optimal mapping
        mapper.getConfiguration()
            .setMatchingStrategy(MatchingStrategies.STRICT)
            .setFieldMatchingEnabled(true)
            .setFieldAccessLevel(org.modelmapper.config.Configuration.AccessLevel.PRIVATE);

        // Add timezone-aware datetime converters
        configureTimezoneConverters(mapper);
        
        log.info("Configured ModelMapper with timezone-aware datetime conversion");
        return mapper;
    }

    private void configureTimezoneConverters(ModelMapper mapper) {
        if (timezoneService == null) {
            log.debug("TimezoneService not available, skipping timezone converters");
            return;
        }

        // LocalDateTime -> OffsetDateTime converter
        Converter<LocalDateTime, OffsetDateTime> localToOffsetConverter = context -> {
            LocalDateTime source = context.getSource();
            if (source == null) {
                return null;
            }
            
            if (timezoneService.isNormalizeTimezone()) {
                OffsetDateTime result = timezoneService.normalizeToApplicationTimezone(source);
                log.trace("ModelMapper: Converting LocalDateTime to OffsetDateTime: {} -> {}", source, result);
                return result;
            }
            
            return source.atOffset(timezoneService.getApplicationZoneOffset());
        };

        // OffsetDateTime -> LocalDateTime converter
        Converter<OffsetDateTime, LocalDateTime> offsetToLocalConverter = context -> {
            OffsetDateTime source = context.getSource();
            if (source == null) {
                return null;
            }
            
            // Convert to application timezone before extracting LocalDateTime
            OffsetDateTime normalized = source.withOffsetSameInstant(timezoneService.getApplicationZoneOffset());
            LocalDateTime result = normalized.toLocalDateTime();
            log.trace("ModelMapper: Converting OffsetDateTime to LocalDateTime: {} -> {}", source, result);
            return result;
        };

        // OffsetDateTime -> OffsetDateTime converter (for timezone normalization)
        Converter<OffsetDateTime, OffsetDateTime> offsetToOffsetConverter = context -> {
            OffsetDateTime source = context.getSource();
            if (source == null) {
                return null;
            }
            
            // Normalize to application timezone
            OffsetDateTime result = source.withOffsetSameInstant(timezoneService.getApplicationZoneOffset());
            log.trace("ModelMapper: Normalizing OffsetDateTime: {} -> {}", source, result);
            return result;
        };

        // Register converters with ModelMapper
        mapper.addConverter(localToOffsetConverter);
        mapper.addConverter(offsetToLocalConverter);
        mapper.addConverter(offsetToOffsetConverter);
        
        log.debug("Registered timezone-aware datetime converters with ModelMapper");
    }
} 