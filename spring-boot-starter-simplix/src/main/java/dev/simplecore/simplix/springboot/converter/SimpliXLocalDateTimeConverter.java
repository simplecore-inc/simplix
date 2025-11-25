package dev.simplecore.simplix.springboot.converter;

import dev.simplecore.simplix.springboot.autoconfigure.SimpliXDateTimeAutoConfiguration;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;

/**
 * JPA AttributeConverter for automatic LocalDateTime to OffsetDateTime conversion.
 * This converter automatically handles timezone normalization for LocalDateTime fields:
 * - When saving to database: converts LocalDateTime to OffsetDateTime using application timezone
 * - When reading from database: converts OffsetDateTime back to LocalDateTime
 * 
 * This is useful when you have legacy LocalDateTime fields but want to store them with timezone information.
 */
@Slf4j
@Converter(autoApply = true)
public class SimpliXLocalDateTimeConverter implements AttributeConverter<LocalDateTime, OffsetDateTime> {

    private SimpliXDateTimeAutoConfiguration.SimpliXTimezoneService timezoneService;

    @Autowired(required = false)
    public void setTimezoneService(SimpliXDateTimeAutoConfiguration.SimpliXTimezoneService timezoneService) {
        this.timezoneService = timezoneService;
    }

    @Override
    public OffsetDateTime convertToDatabaseColumn(LocalDateTime attribute) {
        if (attribute == null) {
            return null;
        }

        if (timezoneService != null && timezoneService.isNormalizeTimezone()) {
            OffsetDateTime normalized = timezoneService.normalizeToApplicationTimezone(attribute);
            
            // If useUtcForDatabase is enabled, convert to UTC
            if (timezoneService.isUseUtcForDatabase()) {
                normalized = timezoneService.normalizeForDatabase(normalized);
            }
            
            log.trace("Converting LocalDateTime to database: {} -> {}", attribute, normalized);
            return normalized;
        }

        // Fallback: use system default timezone
        return attribute.atOffset(timezoneService != null ? 
            timezoneService.getApplicationZoneOffset() : 
            java.time.ZoneOffset.systemDefault().getRules().getOffset(attribute));
    }

    @Override
    public LocalDateTime convertToEntityAttribute(OffsetDateTime dbData) {
        if (dbData == null) {
            return null;
        }

        OffsetDateTime result = dbData;
        
        // If data was stored in UTC, convert back to application timezone
        if (timezoneService != null && timezoneService.isUseUtcForDatabase()) {
            result = timezoneService.normalizeFromDatabase(dbData);
        }
        
        LocalDateTime localDateTime = result.toLocalDateTime();
        log.trace("Converting OffsetDateTime from database to LocalDateTime: {} -> {}", dbData, localDateTime);
        return localDateTime;
    }
} 