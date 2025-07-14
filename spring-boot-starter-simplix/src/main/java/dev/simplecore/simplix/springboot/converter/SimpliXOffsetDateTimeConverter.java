package dev.simplecore.simplix.springboot.converter;

import dev.simplecore.simplix.springboot.autoconfigure.SimpliXDateTimeAutoConfiguration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

import javax.persistence.AttributeConverter;
import javax.persistence.Converter;
import java.time.OffsetDateTime;

/**
 * JPA AttributeConverter for automatic OffsetDateTime timezone conversion.
 * This converter automatically handles timezone normalization for database operations:
 * - When saving to database: converts to UTC if useUtcForDatabase is enabled
 * - When reading from database: converts from UTC to application timezone if useUtcForDatabase is enabled
 */
@Slf4j
@Converter(autoApply = true)
public class SimpliXOffsetDateTimeConverter implements AttributeConverter<OffsetDateTime, OffsetDateTime> {

    private SimpliXDateTimeAutoConfiguration.SimpliXTimezoneService timezoneService;

    @Autowired(required = false)
    public void setTimezoneService(SimpliXDateTimeAutoConfiguration.SimpliXTimezoneService timezoneService) {
        this.timezoneService = timezoneService;
    }

    @Override
    public OffsetDateTime convertToDatabaseColumn(OffsetDateTime attribute) {
        if (attribute == null) {
            return null;
        }

        if (timezoneService != null && timezoneService.isUseUtcForDatabase()) {
            OffsetDateTime normalized = timezoneService.normalizeForDatabase(attribute);
            log.trace("Converting OffsetDateTime to database: {} -> {}", attribute, normalized);
            return normalized;
        }

        return attribute;
    }

    @Override
    public OffsetDateTime convertToEntityAttribute(OffsetDateTime dbData) {
        if (dbData == null) {
            return null;
        }

        if (timezoneService != null && timezoneService.isUseUtcForDatabase()) {
            OffsetDateTime normalized = timezoneService.normalizeFromDatabase(dbData);
            log.trace("Converting OffsetDateTime from database: {} -> {}", dbData, normalized);
            return normalized;
        }

        return dbData;
    }
} 