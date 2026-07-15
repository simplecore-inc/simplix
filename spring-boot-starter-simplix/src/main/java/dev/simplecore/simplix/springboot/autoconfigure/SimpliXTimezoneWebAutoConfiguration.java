package dev.simplecore.simplix.springboot.autoconfigure;

import dev.simplecore.simplix.springboot.web.timezone.TimezoneInterceptor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.time.ZoneId;

/**
 * Auto-configuration for per-request timezone support via {@code X-Timezone} header.
 *
 * <p>Registers {@link TimezoneInterceptor}, which extracts the timezone from the HTTP header
 * into a ThreadLocal ({@code TimezoneContext}). The request-aware {@code Instant} serializer
 * that reads that ThreadLocal is registered on the primary {@code ObjectMapper} by
 * {@link SimpliXJacksonAutoConfiguration}, so it reaches the MVC message converter.
 *
 * <p>Uses the {@code applicationZoneId} bean (from {@link SimpliXDateTimeAutoConfiguration})
 * as the fallback timezone when the header is absent.
 *
 * <p>Enable/disable via {@code simplix.date-time.request-timezone-enabled} (default: true).
 */
@Slf4j
@AutoConfiguration(after = SimpliXDateTimeAutoConfiguration.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(prefix = "simplix.date-time", name = "request-timezone-enabled",
        havingValue = "true", matchIfMissing = true)
public class SimpliXTimezoneWebAutoConfiguration implements WebMvcConfigurer {

    private final ZoneId fallbackZoneId;

    public SimpliXTimezoneWebAutoConfiguration(ZoneId applicationZoneId) {
        this.fallbackZoneId = applicationZoneId;
    }

    @Bean
    public TimezoneInterceptor timezoneInterceptor() {
        return new TimezoneInterceptor(fallbackZoneId);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(timezoneInterceptor());
        log.info("TimezoneInterceptor registered [fallback={}]", fallbackZoneId);
    }
}
