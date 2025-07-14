package dev.simplecore.simplix.core.jackson;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import dev.simplecore.simplix.core.convert.enumeration.EnumConverter;

import java.io.IOException;
import java.util.Map;

public class SimpliXEnumSerializer extends JsonSerializer<Enum<?>> {
    private final EnumConverter converter = EnumConverter.getDefault();
    
    @Override
    public void serialize(Enum<?> value, JsonGenerator gen, SerializerProvider provider) throws IOException {
        try {
            Map<String, Object> fields = converter.toMap(value);
            if (fields == null) {
                gen.writeNull();
                return;
            }
            
            gen.writeStartObject();
            for (Map.Entry<String, Object> entry : fields.entrySet()) {
                gen.writeObjectField(entry.getKey(), entry.getValue());
            }
            gen.writeEndObject();
        } catch (Exception e) {
            throw new IOException("Failed to serialize enum", e);
        }
    }
} 