package dev.simplecore.simplix.springboot.autoconfigure;

import dev.simplecore.simplix.springboot.converter.SimpliXLocalDateTimeConverter;
import dev.simplecore.simplix.springboot.converter.SimpliXOffsetDateTimeConverter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SimpliXJpaAutoConfiguration - JPA converter bean registration")
class SimpliXJpaAutoConfigurationTest {

    private SimpliXJpaAutoConfiguration config;

    @BeforeEach
    void setUp() {
        config = new SimpliXJpaAutoConfiguration();
    }

    @Test
    @DisplayName("Should create SimpliXOffsetDateTimeConverter bean")
    void createOffsetDateTimeConverter() {
        SimpliXOffsetDateTimeConverter converter = config.simplixOffsetDateTimeConverter();

        assertThat(converter).isNotNull();
    }

    @Test
    @DisplayName("Should create SimpliXLocalDateTimeConverter bean")
    void createLocalDateTimeConverter() {
        SimpliXLocalDateTimeConverter converter = config.simplixLocalDateTimeConverter();

        assertThat(converter).isNotNull();
    }
}
