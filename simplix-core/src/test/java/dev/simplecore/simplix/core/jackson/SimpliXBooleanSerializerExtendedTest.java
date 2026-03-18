package dev.simplecore.simplix.core.jackson;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("SimpliXBooleanSerializer - Extended Coverage")
class SimpliXBooleanSerializerExtendedTest {

    @Mock
    private JsonGenerator gen;

    @Mock
    private SerializerProvider provider;

    private final SimpliXBooleanSerializer serializer = new SimpliXBooleanSerializer();

    @Test
    @DisplayName("should write null for null value")
    void shouldWriteNullForNull() throws IOException {
        serializer.serialize(null, gen, provider);
        verify(gen).writeNull();
    }

    @Test
    @DisplayName("should write true for true value")
    void shouldWriteTrue() throws IOException {
        serializer.serialize(true, gen, provider);
        verify(gen).writeBoolean(true);
    }

    @Test
    @DisplayName("should write false for false value")
    void shouldWriteFalse() throws IOException {
        serializer.serialize(false, gen, provider);
        verify(gen).writeBoolean(false);
    }
}
