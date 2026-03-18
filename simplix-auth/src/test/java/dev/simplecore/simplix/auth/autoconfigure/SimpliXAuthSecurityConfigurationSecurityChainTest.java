package dev.simplecore.simplix.auth.autoconfigure;

import dev.simplecore.simplix.auth.properties.SimpliXAuthProperties;
import dev.simplecore.simplix.auth.security.SimpliXAccessDeniedHandler;
import dev.simplecore.simplix.auth.security.SimpliXAuthenticationEntryPoint;
import dev.simplecore.simplix.auth.security.SimpliXTokenAuthenticationFilter;
import dev.simplecore.simplix.auth.security.SimpliXUserDetailsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@DisplayName("SimpliXAuthSecurityConfiguration - bean creation")
@ExtendWith(MockitoExtension.class)
class SimpliXAuthSecurityConfigurationSecurityChainTest {

    @Mock
    private AuthenticationConfiguration authConfig;

    @Mock
    private AuthenticationManager authManager;

    private SimpliXAuthProperties properties;
    private SimpliXAuthSecurityConfiguration configuration;

    @BeforeEach
    void setUp() {
        properties = new SimpliXAuthProperties();
        configuration = new SimpliXAuthSecurityConfiguration(properties);
    }

    @Test
    @DisplayName("should create authenticationManager from configuration")
    void shouldCreateAuthenticationManager() throws Exception {
        when(authConfig.getAuthenticationManager()).thenReturn(authManager);

        AuthenticationManager result = configuration.authenticationManager(authConfig);

        assertThat(result).isEqualTo(authManager);
        verify(authConfig).getAuthenticationManager();
    }
}
