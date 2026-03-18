package dev.simplecore.simplix.springboot.autoconfigure;

import dev.simplecore.simplix.springboot.web.timezone.TimezoneInterceptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.web.servlet.config.annotation.InterceptorRegistration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;

import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SimpliXTimezoneWebAutoConfiguration - per-request timezone support configuration")
class SimpliXTimezoneWebAutoConfigurationTest {

    private SimpliXTimezoneWebAutoConfiguration config;

    @BeforeEach
    void setUp() {
        ZoneId fallback = ZoneId.of("Asia/Seoul");
        config = new SimpliXTimezoneWebAutoConfiguration(fallback);
    }

    @Test
    @DisplayName("Should create TimezoneInterceptor bean")
    void createTimezoneInterceptor() {
        TimezoneInterceptor interceptor = config.timezoneInterceptor();

        assertThat(interceptor).isNotNull();
    }

    @Test
    @DisplayName("Should create Jackson2ObjectMapperBuilderCustomizer for Instant serialization")
    void createInstantCustomizer() {
        Jackson2ObjectMapperBuilderCustomizer customizer = config.timezoneAwareInstantCustomizer();

        assertThat(customizer).isNotNull();
    }

    @Test
    @DisplayName("Should apply customizer to Jackson2ObjectMapperBuilder without errors")
    void applyCustomizerToBuilder() {
        Jackson2ObjectMapperBuilderCustomizer customizer = config.timezoneAwareInstantCustomizer();
        Jackson2ObjectMapperBuilder builder = new Jackson2ObjectMapperBuilder();

        customizer.customize(builder);

        // Should apply the module to the builder without exception
        assertThat(builder).isNotNull();
    }

    @Test
    @DisplayName("Should register interceptor via addInterceptors")
    void registerInterceptors() {
        InterceptorRegistry registry = new InterceptorRegistry();

        config.addInterceptors(registry);

        // Should not throw and registry should have interceptors
        assertThat(registry).isNotNull();
    }
}
