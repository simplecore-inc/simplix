package dev.simplecore.simplix.auth.security;

import dev.simplecore.simplix.auth.properties.SimpliXAuthProperties;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;

/**
 * Builds the CORS configuration every SimpliX security chain shares from {@code simplix.auth.cors}.
 */
public final class SimpliXCorsConfigurations {

    private SimpliXCorsConfigurations() {
    }

    /**
     * Creates a source that applies the configured CORS rules to every path.
     *
     * @param cors the configured CORS properties
     * @return a source registered for {@code /**}
     */
    public static CorsConfigurationSource source(SimpliXAuthProperties.CorsProperties cors) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.applyPermitDefaultValues();

        // Origin patterns take precedence over exact origins; they support wildcards and work
        // with allowCredentials=true, unlike allowedOrigins=["*"].
        String[] allowedOriginPatterns = cors.getAllowedOriginPatterns();
        if (allowedOriginPatterns != null && allowedOriginPatterns.length > 0) {
            configuration.setAllowedOriginPatterns(Arrays.asList(allowedOriginPatterns));
        } else if (cors.getAllowedOrigins() != null && cors.getAllowedOrigins().length > 0) {
            configuration.setAllowedOrigins(Arrays.asList(cors.getAllowedOrigins()));
        }
        if (cors.getAllowedMethods() != null && cors.getAllowedMethods().length > 0) {
            configuration.setAllowedMethods(Arrays.asList(cors.getAllowedMethods()));
        }
        if (cors.getAllowedHeaders() != null && cors.getAllowedHeaders().length > 0) {
            configuration.setAllowedHeaders(Arrays.asList(cors.getAllowedHeaders()));
        }
        if (cors.getExposedHeaders() != null && cors.getExposedHeaders().length > 0) {
            configuration.setExposedHeaders(Arrays.asList(cors.getExposedHeaders()));
        }
        if (cors.getAllowCredentials() != null) {
            configuration.setAllowCredentials(cors.getAllowCredentials());
        }
        if (cors.getMaxAge() != null) {
            configuration.setMaxAge(cors.getMaxAge());
        }

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
