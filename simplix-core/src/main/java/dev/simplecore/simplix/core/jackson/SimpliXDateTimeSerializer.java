package dev.simplecore.simplix.core.jackson;

import dev.simplecore.simplix.core.convert.datetime.DateTimeConverter;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.io.IOException;
import java.time.ZoneId;
import java.time.temporal.Temporal;

public class SimpliXDateTimeSerializer extends JsonSerializer<Temporal> {
    private final DateTimeConverter converter;

    public SimpliXDateTimeSerializer(ZoneId zoneId) {
        this.converter = DateTimeConverter.of(zoneId);
    }

    @Override
    public void serialize(Temporal value, JsonGenerator gen, SerializerProvider provider) throws IOException {
        gen.writeString(converter.toString(value));
    }
} 