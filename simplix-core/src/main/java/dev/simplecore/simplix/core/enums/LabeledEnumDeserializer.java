package dev.simplecore.simplix.core.enums;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.BeanProperty;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.deser.ContextualDeserializer;

import java.io.IOException;

/**
 * Custom deserializer for {@link LabeledEnum} that handles both string and
 * object formats.
 *
 * <p>{@code @JsonFormat(shape = OBJECT)} on {@link LabeledEnum} causes Jackson
 * to serialize enums as objects. {@code SimpliXEnumSerializer} produces
 * {@code {"type":"EventCategory","value":"DOOR","label":"Door"}} while plain
 * Jackson produces {@code {"value":"DOOR","label":"Door"}} via
 * {@code @JsonProperty("value")} on {@link LabeledEnum#name()}.
 * Hibernate's {@code JsonType} deep-copy mechanism serializes and then deserializes
 * these values, but the default enum deserializer cannot parse objects back to enums.
 *
 * <p>This deserializer handles both:
 * <ul>
 *   <li>String: {@code "SECURE"} → resolved by name</li>
 *   <li>Object: {@code {"value":"SECURE",...}} → extracts "value" and resolves</li>
 * </ul>
 */
public class LabeledEnumDeserializer extends JsonDeserializer<LabeledEnum>
        implements ContextualDeserializer {

    private Class<? extends Enum<?>> enumClass;

    public LabeledEnumDeserializer() {
    }

    private LabeledEnumDeserializer(Class<? extends Enum<?>> enumClass) {
        this.enumClass = enumClass;
    }

    @SuppressWarnings("unchecked")
    @Override
    public JsonDeserializer<?> createContextual(DeserializationContext ctxt, BeanProperty property) {
        JavaType type = property != null ? property.getType() : ctxt.getContextualType();
        // Collection properties (Set<Enum>, List<Enum>) contextualize with the
        // container type; unwrap to the element type so enumClass is the enum
        // itself, never the collection class (whose getEnumConstants() is null).
        while (type != null && type.isContainerType()) {
            type = type.getContentType();
        }
        if (type == null || !type.isEnumType()) {
            return this;
        }
        return new LabeledEnumDeserializer((Class<? extends Enum<?>>) type.getRawClass());
    }

    @Override
    public LabeledEnum deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        if (p.currentToken() == JsonToken.START_OBJECT) {
            return deserializeFromObject(p);
        }
        return resolveByName(p.getText());
    }

    private LabeledEnum deserializeFromObject(JsonParser p) throws IOException {
        String name = null;
        while (p.nextToken() != JsonToken.END_OBJECT) {
            String field = p.currentName();
            p.nextToken();
            if ("value".equals(field) || "name".equals(field)) {
                name = p.getText();
            }
        }
        return resolveByName(name);
    }

    private LabeledEnum resolveByName(String name) {
        if (name == null || enumClass == null) {
            return null;
        }
        for (Enum<?> constant : enumClass.getEnumConstants()) {
            if (constant.name().equals(name)) {
                return (LabeledEnum) constant;
            }
        }
        return null;
    }
}
