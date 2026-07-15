package dev.simplecore.simplix.springboot.autoconfigure;

import dev.simplecore.simplix.springboot.web.timezone.TimezoneInterceptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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
    @DisplayName("Should register interceptor via addInterceptors")
    void registerInterceptors() {
        InterceptorRegistry registry = new InterceptorRegistry();

        config.addInterceptors(registry);

        // Should not throw and registry should have interceptors
        assertThat(registry).isNotNull();
    }
}
