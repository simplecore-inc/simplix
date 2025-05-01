package dev.simplecore.simplix.core.jackson;

import dev.simplecore.simplix.core.convert.bool.BooleanConverter;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.io.IOException;

public class SimpliXBooleanSerializer extends JsonSerializer<Boolean> {
    private final BooleanConverter converter = BooleanConverter.getDefault();
    
    @Override
    public void serialize(Boolean value, JsonGenerator gen, SerializerProvider provider) throws IOException {
        String stringValue = converter.toString(value);
        if (stringValue == null) {
            gen.writeNull();
        } else {
            gen.writeString(stringValue);
        }
    }
} 