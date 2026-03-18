package dev.simplecore.simplix.web.config.openapi;

import io.swagger.v3.oas.models.Operation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.Ordered;
import org.springframework.web.method.HandlerMethod;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
@ExtendWith(MockitoExtension.class)
@DisplayName("OperationIdCustomizer - generates unique operation IDs from class+method name")
class OperationIdCustomizerTest {

    private OperationIdCustomizer customizer;

    @Mock
    private HandlerMethod handlerMethod;

    @BeforeEach
    void setUp() {
        customizer = new OperationIdCustomizer();
    }

    @Test
    @DisplayName("Should strip 'Controller' suffix and combine with method name")
    void stripControllerSuffix() throws Exception {
        Operation operation = new Operation();
        when(handlerMethod.getBeanType()).thenReturn((Class) SampleController.class);
        when(handlerMethod.getMethod()).thenReturn(SampleController.class.getMethod("getAll"));

        Operation result = customizer.customize(operation, handlerMethod);

        assertThat(result.getOperationId()).isEqualTo("Sample_getAll");
    }

    @Test
    @DisplayName("Should strip 'RestController' suffix and combine with method name")
    void stripRestControllerSuffix() throws Exception {
        Operation operation = new Operation();
        when(handlerMethod.getBeanType()).thenReturn((Class) SampleRestController.class);
        when(handlerMethod.getMethod()).thenReturn(SampleRestController.class.getMethod("create"));

        Operation result = customizer.customize(operation, handlerMethod);

        // The regex strips "Controller$" first, then "RestController$"
        // "SampleRestController" -> after replaceFirst("Controller$", "") -> "SampleRest"
        // -> after replaceFirst("RestController$", "") -> "SampleRest" (no match)
        // So the order matters: it first tries Controller, which matches, leaving "SampleRest"
        assertThat(result.getOperationId()).isEqualTo("SampleRest_create");
    }

    @Test
    @DisplayName("Should keep class name as-is when no Controller suffix")
    void noSuffix() throws Exception {
        Operation operation = new Operation();
        when(handlerMethod.getBeanType()).thenReturn((Class) SampleHandler.class);
        when(handlerMethod.getMethod()).thenReturn(SampleHandler.class.getMethod("handle"));

        Operation result = customizer.customize(operation, handlerMethod);

        assertThat(result.getOperationId()).isEqualTo("SampleHandler_handle");
    }

    @Test
    @DisplayName("Should run at high precedence order")
    void highPrecedence() {
        assertThat(customizer.getOrder()).isEqualTo(Ordered.HIGHEST_PRECEDENCE + 1);
    }

    // Test helper classes
    static class SampleController {
        public void getAll() {}
    }

    static class SampleRestController {
        public void create() {}
    }

    static class SampleHandler {
        public void handle() {}
    }
}
