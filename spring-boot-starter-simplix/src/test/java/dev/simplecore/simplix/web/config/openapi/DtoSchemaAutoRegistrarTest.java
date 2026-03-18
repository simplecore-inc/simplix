package dev.simplecore.simplix.web.config.openapi;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Schema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.Ordered;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("DtoSchemaAutoRegistrar - registers DTO schemas from controller return types")
class DtoSchemaAutoRegistrarTest {

    @Mock
    private RequestMappingHandlerMapping handlerMapping;

    private DtoSchemaAutoRegistrar registrar;

    @BeforeEach
    void setUp() {
        registrar = new DtoSchemaAutoRegistrar(handlerMapping);
    }

    @Test
    @DisplayName("Should handle empty handler methods map")
    void emptyHandlerMethods() {
        when(handlerMapping.getHandlerMethods()).thenReturn(new HashMap<>());

        OpenAPI openApi = new OpenAPI();
        openApi.setComponents(new Components());

        registrar.customise(openApi);

        // Should not throw, schemas should be empty
        assertThat(openApi.getComponents().getSchemas()).isNull();
    }

    @Test
    @DisplayName("Should handle null components gracefully")
    @SuppressWarnings("unchecked")
    void nullComponents() throws Exception {
        Map<RequestMappingInfo, HandlerMethod> methods = new HashMap<>();
        Method method = SampleController.class.getMethod("getDto");
        HandlerMethod handlerMethod = new HandlerMethod(new SampleController(), method);
        methods.put(RequestMappingInfo.paths("/test").build(), handlerMethod);
        when(handlerMapping.getHandlerMethods()).thenReturn(methods);

        OpenAPI openApi = new OpenAPI();
        openApi.setComponents(null);

        // Should not throw
        registrar.customise(openApi);
    }

    @Test
    @DisplayName("Should register DTO schema from controller return type")
    @SuppressWarnings("unchecked")
    void registerDtoFromReturnType() throws Exception {
        Map<RequestMappingInfo, HandlerMethod> methods = new HashMap<>();
        Method method = SampleController.class.getMethod("getDto");
        HandlerMethod handlerMethod = new HandlerMethod(new SampleController(), method);
        methods.put(RequestMappingInfo.paths("/test").build(), handlerMethod);
        when(handlerMapping.getHandlerMethods()).thenReturn(methods);

        OpenAPI openApi = new OpenAPI();
        openApi.setComponents(new Components());

        registrar.customise(openApi);

        // TestDto should be registered
        Map<String, Schema> schemas = openApi.getComponents().getSchemas();
        assertThat(schemas).isNotNull();
        assertThat(schemas).containsKey("TestDto");
    }

    @Test
    @DisplayName("Should not re-register existing schemas")
    @SuppressWarnings({"unchecked", "rawtypes"})
    void noReRegistration() throws Exception {
        Map<RequestMappingInfo, HandlerMethod> methods = new HashMap<>();
        Method method = SampleController.class.getMethod("getDto");
        HandlerMethod handlerMethod = new HandlerMethod(new SampleController(), method);
        methods.put(RequestMappingInfo.paths("/test").build(), handlerMethod);
        when(handlerMapping.getHandlerMethods()).thenReturn(methods);

        OpenAPI openApi = new OpenAPI();
        Components components = new Components();
        Schema existingSchema = new Schema<>();
        existingSchema.setDescription("existing");
        Map<String, Schema> schemas = new HashMap<>();
        schemas.put("TestDto", existingSchema);
        components.setSchemas(schemas);
        openApi.setComponents(components);

        registrar.customise(openApi);

        // Should keep existing schema
        assertThat(openApi.getComponents().getSchemas().get("TestDto").getDescription())
                .isEqualTo("existing");
    }

    @Test
    @DisplayName("Should skip JDK and Spring framework types")
    @SuppressWarnings("unchecked")
    void skipJdkTypes() throws Exception {
        Map<RequestMappingInfo, HandlerMethod> methods = new HashMap<>();
        Method method = SampleController.class.getMethod("getString");
        HandlerMethod handlerMethod = new HandlerMethod(new SampleController(), method);
        methods.put(RequestMappingInfo.paths("/test").build(), handlerMethod);
        when(handlerMapping.getHandlerMethods()).thenReturn(methods);

        OpenAPI openApi = new OpenAPI();
        openApi.setComponents(new Components());

        registrar.customise(openApi);

        // String is a JDK type, should not be registered
        assertThat(openApi.getComponents().getSchemas()).isNull();
    }

    @Test
    @DisplayName("Should run at highest precedence to ensure schemas are available for other customizers")
    void highestPrecedence() {
        assertThat(registrar.getOrder()).isEqualTo(Ordered.HIGHEST_PRECEDENCE + 10);
    }

    // Test helper classes
    public static class TestDto {
        public String name;
        public int age;
    }

    public static class SampleController {
        public TestDto getDto() { return null; }
        public String getString() { return "hello"; }
    }
}
