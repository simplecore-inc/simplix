package dev.simplecore.simplix.encryption.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
@DisplayName("SimpliXEncryptionAutoConfiguration Tests")
class SimpliXEncryptionAutoConfigurationTest {

    @Mock
    private SimpliXEncryptionProperties encryptionProperties;

    @InjectMocks
    private SimpliXEncryptionAutoConfiguration autoConfiguration;

    @Nested
    @DisplayName("init()")
    class InitTests {

        @Test
        @DisplayName("Should not throw on initialization")
        void shouldNotThrowOnInit() {
            // init() just logs, should not throw
            autoConfiguration.init();
        }
    }

    @Nested
    @DisplayName("Constructor")
    class ConstructorTests {

        @Test
        @DisplayName("Should create with encryption properties")
        void shouldCreateWithProperties() {
            assertThat(autoConfiguration).isNotNull();
        }
    }
}
