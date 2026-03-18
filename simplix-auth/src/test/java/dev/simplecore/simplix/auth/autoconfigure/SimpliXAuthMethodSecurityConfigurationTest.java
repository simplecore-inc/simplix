package dev.simplecore.simplix.auth.autoconfigure;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationContext;
import org.springframework.security.access.PermissionEvaluator;
import org.springframework.security.access.expression.method.MethodSecurityExpressionHandler;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SimpliXAuthMethodSecurityConfiguration")
@ExtendWith(MockitoExtension.class)
class SimpliXAuthMethodSecurityConfigurationTest {

    @Mock
    private ApplicationContext applicationContext;

    @Mock
    private PermissionEvaluator permissionEvaluator;

    @Test
    @DisplayName("should create expression handler with permission evaluator")
    void shouldCreateExpressionHandlerWithPermissionEvaluator() throws Exception {
        SimpliXAuthMethodSecurityConfiguration config = new SimpliXAuthMethodSecurityConfiguration();

        // Set fields via reflection since @Autowired fields are normally injected
        Field appCtxField = SimpliXAuthMethodSecurityConfiguration.class.getDeclaredField("applicationContext");
        appCtxField.setAccessible(true);
        appCtxField.set(config, applicationContext);

        Field permField = SimpliXAuthMethodSecurityConfiguration.class.getDeclaredField("permissionEvaluator");
        permField.setAccessible(true);
        permField.set(config, permissionEvaluator);

        MethodSecurityExpressionHandler handler = config.methodSecurityExpressionHandler();
        assertThat(handler).isNotNull();
    }

    @Test
    @DisplayName("should create expression handler without permission evaluator")
    void shouldCreateExpressionHandlerWithoutPermissionEvaluator() throws Exception {
        SimpliXAuthMethodSecurityConfiguration config = new SimpliXAuthMethodSecurityConfiguration();

        Field appCtxField = SimpliXAuthMethodSecurityConfiguration.class.getDeclaredField("applicationContext");
        appCtxField.setAccessible(true);
        appCtxField.set(config, applicationContext);

        // permissionEvaluator is null by default
        MethodSecurityExpressionHandler handler = config.methodSecurityExpressionHandler();
        assertThat(handler).isNotNull();
    }
}
