package dev.simplecore.simplix.core.jackson;

import dev.simplecore.simplix.core.convert.datetime.DateTimeConverter;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import java.io.IOException;
import java.time.ZoneId;
import java.time.temporal.Temporal;

public class SimpliXDateTimeDeserializer<T extends Temporal> extends JsonDeserializer<T> {
    private final DateTimeConverter converter;
    private final Class<? extends Temporal> targetType;

    public SimpliXDateTimeDeserializer(ZoneId zoneId, Class<? extends Temporal> targetType) {
        this.converter = DateTimeConverter.of(zoneId);
        this.targetType = targetType;
    }

    @Override
    @SuppressWarnings("unchecked")
    public T deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        String value = p.getValueAsString();
        try {
            return (T) converter.fromString(value, targetType);
        } catch (IllegalArgumentException e) {
            throw new IOException("Unable to deserialize datetime value: " + value, e);
        }
    }
} 