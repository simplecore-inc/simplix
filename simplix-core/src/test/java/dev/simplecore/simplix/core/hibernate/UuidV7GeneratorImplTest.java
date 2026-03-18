package dev.simplecore.simplix.core.hibernate;

import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
@DisplayName("UuidV7GeneratorImpl")
class UuidV7GeneratorImplTest {

    private UuidV7GeneratorImpl generator;

    @Mock
    private SharedSessionContractImplementor session;

    @BeforeEach
    void setUp() {
        generator = new UuidV7GeneratorImpl();
    }

    @Test
    @DisplayName("should generate a non-null UUID string")
    void shouldGenerateNonNullUuid() {
        Object result = generator.generate(session, new Object());

        assertThat(result).isNotNull();
        assertThat(result).isInstanceOf(String.class);
    }

    @Test
    @DisplayName("should generate valid UUID format")
    void shouldGenerateValidUuidFormat() {
        String uuid = (String) generator.generate(session, new Object());

        assertThat(uuid).matches("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");
    }

    @Test
    @DisplayName("should generate unique UUIDs on consecutive calls")
    void shouldGenerateUniqueUuids() {
        Set<String> uuids = new HashSet<>();
        for (int i = 0; i < 50; i++) {
            uuids.add((String) generator.generate(session, new Object()));
        }

        assertThat(uuids).hasSize(50);
    }
}
