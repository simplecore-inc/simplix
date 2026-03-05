package dev.simplecore.simplix.core.jackson;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import java.io.IOException;

public class SimpliXBooleanSerializer extends JsonSerializer<Boolean> {

    @Override
    public void serialize(Boolean value, JsonGenerator gen, SerializerProvider provider) throws IOException {
        if (value == null) {
            gen.writeNull();
        } else {
            gen.writeBoolean(value);
        }
    }
} 