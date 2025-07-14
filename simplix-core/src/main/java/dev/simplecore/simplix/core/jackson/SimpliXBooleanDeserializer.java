package dev.simplecore.simplix.core.jackson;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import dev.simplecore.simplix.core.convert.bool.BooleanConverter;

import java.io.IOException;

public class SimpliXBooleanDeserializer extends JsonDeserializer<Boolean> {
    private final BooleanConverter converter = BooleanConverter.getDefault();
    
    @Override
    public Boolean deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        String value = p.getValueAsString();
        try {
            return converter.fromString(value);
        } catch (IllegalArgumentException e) {
            throw new IOException("Unable to deserialize boolean value: " + value, e);
        }
    }
} 