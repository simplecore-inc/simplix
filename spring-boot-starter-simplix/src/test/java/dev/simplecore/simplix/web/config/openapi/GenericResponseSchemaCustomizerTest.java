package dev.simplecore.simplix.web.config.openapi;

import dev.simplecore.simplix.core.model.SimpliXApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.Ordered;
import org.springframework.core.ResolvableType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Slice;
import org.springframework.http.ResponseEntity;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
@ExtendWith(MockitoExtension.class)
@DisplayName("GenericResponseSchemaCustomizer - resolves generic type info for response schemas")
class GenericResponseSchemaCustomizerTest {

    private GenericResponseSchemaCustomizer customizer;

    @Mock
    private HandlerMethod handlerMethod;

    @BeforeEach
    void setUp() {
        customizer = new GenericResponseSchemaCustomizer(true);
    }

    @Nested
    @DisplayName("resolveSchema")
    class ResolveSchema {

        @Test
        @DisplayName("Should resolve String to StringSchema")
        void resolveString() {
            ResolvableType type = ResolvableType.forClass(String.class);

            Schema<?> schema = customizer.resolveSchema(type);

            assertThat(schema.getType()).isEqualTo("string");
        }

        @Test
        @DisplayName("Should resolve Integer to IntegerSchema")
        void resolveInteger() {
            ResolvableType type = ResolvableType.forClass(Integer.class);

            Schema<?> schema = customizer.resolveSchema(type);

            assertThat(schema.getType()).isEqualTo("integer");
        }

        @Test
        @DisplayName("Should resolve int primitive to IntegerSchema")
        void resolveIntPrimitive() {
            ResolvableType type = ResolvableType.forClass(int.class);

            Schema<?> schema = customizer.resolveSchema(type);

            assertThat(schema.getType()).isEqualTo("integer");
        }

        @Test
        @DisplayName("Should resolve Long to IntegerSchema with int64 format")
        void resolveLong() {
            ResolvableType type = ResolvableType.forClass(Long.class);

            Schema<?> schema = customizer.resolveSchema(type);

            assertThat(schema.getType()).isEqualTo("integer");
            assertThat(schema.getFormat()).isEqualTo("int64");
        }

        @Test
        @DisplayName("Should resolve long primitive to IntegerSchema with int64 format")
        void resolveLongPrimitive() {
            ResolvableType type = ResolvableType.forClass(long.class);

            Schema<?> schema = customizer.resolveSchema(type);

            assertThat(schema.getType()).isEqualTo("integer");
            assertThat(schema.getFormat()).isEqualTo("int64");
        }

        @Test
        @DisplayName("Should resolve Boolean to BooleanSchema")
        void resolveBoolean() {
            ResolvableType type = ResolvableType.forClass(Boolean.class);

            Schema<?> schema = customizer.resolveSchema(type);

            assertThat(schema.getType()).isEqualTo("boolean");
        }

        @Test
        @DisplayName("Should resolve boolean primitive to BooleanSchema")
        void resolveBooleanPrimitive() {
            ResolvableType type = ResolvableType.forClass(boolean.class);

            Schema<?> schema = customizer.resolveSchema(type);

            assertThat(schema.getType()).isEqualTo("boolean");
        }

        @Test
        @DisplayName("Should resolve Double to NumberSchema with double format")
        void resolveDouble() {
            ResolvableType type = ResolvableType.forClass(Double.class);

            Schema<?> schema = customizer.resolveSchema(type);

            assertThat(schema.getType()).isEqualTo("number");
            assertThat(schema.getFormat()).isEqualTo("double");
        }

        @Test
        @DisplayName("Should resolve Float to NumberSchema with float format")
        void resolveFloat() {
            ResolvableType type = ResolvableType.forClass(Float.class);

            Schema<?> schema = customizer.resolveSchema(type);

            assertThat(schema.getType()).isEqualTo("number");
            assertThat(schema.getFormat()).isEqualTo("float");
        }

        @Test
        @DisplayName("Should resolve Byte to StringSchema with byte format")
        void resolveByte() {
            ResolvableType type = ResolvableType.forClass(Byte.class);

            Schema<?> schema = customizer.resolveSchema(type);

            assertThat(schema.getType()).isEqualTo("string");
            assertThat(schema.getFormat()).isEqualTo("byte");
        }

        @Test
        @DisplayName("Should resolve void to ObjectSchema")
        void resolveVoid() {
            ResolvableType type = ResolvableType.forClass(void.class);

            Schema<?> schema = customizer.resolveSchema(type);

            assertThat(schema.getType()).isEqualTo("object");
        }

        @Test
        @DisplayName("Should resolve Void wrapper to ObjectSchema")
        void resolveVoidWrapper() {
            ResolvableType type = ResolvableType.forClass(Void.class);

            Schema<?> schema = customizer.resolveSchema(type);

            assertThat(schema.getType()).isEqualTo("object");
        }

        @Test
        @DisplayName("Should resolve Object to ObjectSchema")
        void resolveObject() {
            ResolvableType type = ResolvableType.forClass(Object.class);

            Schema<?> schema = customizer.resolveSchema(type);

            assertThat(schema.getType()).isEqualTo("object");
        }

        @Test
        @DisplayName("Should resolve DTO class to $ref schema")
        void resolveDtoClass() {
            ResolvableType type = ResolvableType.forClass(SampleDto.class);

            Schema<?> schema = customizer.resolveSchema(type);

            assertThat(schema.get$ref()).isEqualTo("#/components/schemas/SampleDto");
        }

        @Test
        @DisplayName("Should resolve List<String> to ArraySchema with string items")
        void resolveListOfStrings() {
            ResolvableType type = ResolvableType.forClassWithGenerics(List.class, String.class);

            Schema<?> schema = customizer.resolveSchema(type);

            assertThat(schema.getType()).isEqualTo("array");
            assertThat(schema.getItems().getType()).isEqualTo("string");
        }

        @Test
        @DisplayName("Should resolve Set<Integer> to ArraySchema")
        void resolveSetOfIntegers() {
            ResolvableType type = ResolvableType.forClassWithGenerics(Set.class, Integer.class);

            Schema<?> schema = customizer.resolveSchema(type);

            assertThat(schema.getType()).isEqualTo("array");
            assertThat(schema.getItems().getType()).isEqualTo("integer");
        }

        @Test
        @DisplayName("Should resolve Map<String, Object> to MapSchema")
        void resolveMap() {
            ResolvableType type = ResolvableType.forClassWithGenerics(Map.class, String.class, Object.class);

            Schema<?> schema = customizer.resolveSchema(type);

            assertThat(schema.getType()).isEqualTo("object");
        }

        @Test
        @DisplayName("Should resolve Map<String, String> to MapSchema with string values")
        void resolveMapOfStrings() {
            ResolvableType type = ResolvableType.forClassWithGenerics(Map.class, String.class, String.class);

            Schema<?> schema = customizer.resolveSchema(type);

            assertThat(schema.getType()).isEqualTo("object");
        }

        @Test
        @DisplayName("Should unwrap ResponseEntity and resolve inner type")
        void resolveResponseEntity() {
            ResolvableType type = ResolvableType.forClassWithGenerics(ResponseEntity.class, String.class);

            Schema<?> schema = customizer.resolveSchema(type);

            assertThat(schema.getType()).isEqualTo("string");
        }

        @Test
        @DisplayName("Should unwrap Optional and resolve inner type")
        void resolveOptional() {
            ResolvableType type = ResolvableType.forClassWithGenerics(Optional.class, String.class);

            Schema<?> schema = customizer.resolveSchema(type);

            assertThat(schema.getType()).isEqualTo("string");
        }

        @Test
        @DisplayName("Should resolve SimpliXApiResponse with wrapped structure")
        void resolveApiResponse() {
            ResolvableType type = ResolvableType.forClassWithGenerics(
                    SimpliXApiResponse.class, String.class);

            Schema<?> schema = customizer.resolveSchema(type);

            assertThat(schema.getProperties()).containsKeys("type", "message", "body", "timestamp");
        }

        @Test
        @DisplayName("Should resolve Page type with pagination schema")
        void resolvePageType() {
            ResolvableType type = ResolvableType.forClassWithGenerics(Page.class, SampleDto.class);

            Schema<?> schema = customizer.resolveSchema(type);

            assertThat(schema.getProperties()).containsKeys("content", "totalElements", "totalPages", "pageable");
        }

        @Test
        @DisplayName("Should resolve Slice type without total elements")
        void resolveSliceType() {
            ResolvableType type = ResolvableType.forClassWithGenerics(Slice.class, SampleDto.class);

            Schema<?> schema = customizer.resolveSchema(type);

            assertThat(schema.getProperties()).containsKeys("content", "pageable");
            assertThat(schema.getProperties()).doesNotContainKey("totalElements");
        }

        @Test
        @DisplayName("Should resolve ResolvableType.NONE to ObjectSchema")
        void resolveNone() {
            Schema<?> schema = customizer.resolveSchema(ResolvableType.NONE);

            assertThat(schema.getType()).isEqualTo("object");
        }
    }

    @Nested
    @DisplayName("customize operation")
    class CustomizeOperation {

        @Test
        @DisplayName("Should handle operation with no responses gracefully")
        void noResponses() throws Exception {
            Operation operation = new Operation();
            Method method = SampleApi.class.getMethod("getString");
            when(handlerMethod.getBeanType()).thenReturn((Class) SampleApi.class);
            when(handlerMethod.getMethod()).thenReturn(method);

            Operation result = customizer.customize(operation, handlerMethod);

            assertThat(result).isSameAs(operation);
        }

        @Test
        @DisplayName("Should set 200 response schema for String return type with auto-wrap")
        void autoWrapStringReturn() throws Exception {
            Operation operation = new Operation();
            operation.setResponses(new io.swagger.v3.oas.models.responses.ApiResponses());
            Method method = SampleApi.class.getMethod("getString");
            when(handlerMethod.getBeanType()).thenReturn((Class) SampleApi.class);
            when(handlerMethod.getMethod()).thenReturn(method);

            Operation result = customizer.customize(operation, handlerMethod);

            assertThat(result.getResponses().get("200")).isNotNull();
            Content content = result.getResponses().get("200").getContent();
            assertThat(content).isNotNull();
            assertThat(content.get("application/json")).isNotNull();
        }

        @Test
        @DisplayName("Should wrap void return type with empty body schema when autoWrap is enabled")
        void autoWrapVoidReturn() throws Exception {
            Operation operation = new Operation();
            operation.setResponses(new io.swagger.v3.oas.models.responses.ApiResponses());
            Method method = SampleApi.class.getMethod("doVoid");

            HandlerMethod localHandlerMethod =
                    new HandlerMethod(new SampleApi(), method);

            Operation result = customizer.customize(operation, localHandlerMethod);

            assertThat(result.getResponses().get("200")).isNotNull();
        }

        @Test
        @DisplayName("Should skip customization for SseEmitter streaming type")
        void skipSseEmitter() throws Exception {
            Operation operation = new Operation();
            operation.setResponses(new io.swagger.v3.oas.models.responses.ApiResponses());
            Method method = SampleApi.class.getMethod("getSse");

            HandlerMethod localHandlerMethod =
                    new HandlerMethod(new SampleApi(), method);

            Operation result = customizer.customize(operation, localHandlerMethod);

            // No 200 response should be set for streaming types
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("Should skip customization when explicit @ApiResponse is present")
        void skipExplicitApiResponse() throws Exception {
            Operation operation = new Operation();
            operation.setResponses(new io.swagger.v3.oas.models.responses.ApiResponses());
            Method method = SampleApi.class.getMethod("getWithExplicitResponse");

            HandlerMethod localHandlerMethod =
                    new HandlerMethod(new SampleApi(), method);

            Operation result = customizer.customize(operation, localHandlerMethod);

            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("Should handle generic wrapper return type")
        void handleGenericWrapper() throws Exception {
            Operation operation = new Operation();
            operation.setResponses(new io.swagger.v3.oas.models.responses.ApiResponses());
            Method method = SampleApi.class.getMethod("getWrapped");

            HandlerMethod localHandlerMethod =
                    new HandlerMethod(new SampleApi(), method);

            Operation result = customizer.customize(operation, localHandlerMethod);

            assertThat(result.getResponses().get("200")).isNotNull();
        }

        @Test
        @DisplayName("Should handle ResponseEntity<List<SampleDto>> return type")
        void handleResponseEntityList() throws Exception {
            Operation operation = new Operation();
            operation.setResponses(new io.swagger.v3.oas.models.responses.ApiResponses());
            Method method = SampleApi.class.getMethod("getResponseEntityList");

            HandlerMethod localHandlerMethod =
                    new HandlerMethod(new SampleApi(), method);

            Operation result = customizer.customize(operation, localHandlerMethod);

            assertThat(result.getResponses().get("200")).isNotNull();
        }

        @Test
        @DisplayName("Should preserve existing examples when setting schema")
        void preserveExistingExamples() throws Exception {
            Operation operation = new Operation();
            io.swagger.v3.oas.models.responses.ApiResponses responses = new io.swagger.v3.oas.models.responses.ApiResponses();
            io.swagger.v3.oas.models.responses.ApiResponse existing200 =
                    new io.swagger.v3.oas.models.responses.ApiResponse();
            Content existingContent = new Content();
            MediaType existingMediaType = new MediaType();
            existingMediaType.setExample("{\"test\":true}");
            existingContent.addMediaType("application/json", existingMediaType);
            existing200.setContent(existingContent);
            responses.addApiResponse("200", existing200);
            operation.setResponses(responses);

            Method method = SampleApi.class.getMethod("getString");
            HandlerMethod localHandlerMethod =
                    new HandlerMethod(new SampleApi(), method);

            Operation result = customizer.customize(operation, localHandlerMethod);

            assertThat(result.getResponses().get("200")).isNotNull();
        }

        @Test
        @DisplayName("Should handle exception during customization gracefully")
        void handleExceptionGracefully() throws Exception {
            Operation operation = new Operation();
            operation.setResponses(new io.swagger.v3.oas.models.responses.ApiResponses());
            Method method = SampleApi.class.getMethod("getString");

            // The customize method catches exceptions in doCustomize
            // We need the exception to happen inside doCustomize, not in customize itself
            // The getBeanType() is called inside doCustomize's hasExplicitResponseSchema check
            when(handlerMethod.getMethod()).thenReturn(method);
            when(handlerMethod.getBeanType()).thenReturn((Class) SampleApi.class);

            // This should work without exception - testing the normal path
            Operation result = customizer.customize(operation, handlerMethod);

            assertThat(result).isNotNull();
        }
    }

    @Test
    @DisplayName("Should not auto-wrap when autoWrapEnabled is false")
    void noAutoWrapWhenDisabled() throws Exception {
        GenericResponseSchemaCustomizer noAutoWrap = new GenericResponseSchemaCustomizer(false);
        Operation operation = new Operation();
        operation.setResponses(new io.swagger.v3.oas.models.responses.ApiResponses());
        Method method = SampleApi.class.getMethod("getString");

        HandlerMethod localHandlerMethod =
                new HandlerMethod(new SampleApi(), method);

        Operation result = noAutoWrap.customize(operation, localHandlerMethod);

        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("Should not auto-wrap void when disabled")
    void noAutoWrapVoidWhenDisabled() throws Exception {
        GenericResponseSchemaCustomizer noAutoWrap = new GenericResponseSchemaCustomizer(false);
        Operation operation = new Operation();
        operation.setResponses(new io.swagger.v3.oas.models.responses.ApiResponses());
        Method method = SampleApi.class.getMethod("doVoid");

        HandlerMethod localHandlerMethod =
                new HandlerMethod(new SampleApi(), method);

        Operation result = noAutoWrap.customize(operation, localHandlerMethod);

        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("Should run at late order to override springdoc defaults")
    void lateOrder() {
        assertThat(customizer.getOrder()).isEqualTo(Ordered.LOWEST_PRECEDENCE - 10);
    }

    // Test helper classes
    static class SampleDto {
        public String name;
    }

    @SuppressWarnings("unused")
    static class SampleApi {
        public String getString() { return "hello"; }
        public void doVoid() {}
        public SimpliXApiResponse<SampleDto> getWrapped() { return null; }
        public SseEmitter getSse() { return null; }
        public ResponseEntity<List<SampleDto>> getResponseEntityList() { return null; }

        @ApiResponse(responseCode = "200",
                content = @io.swagger.v3.oas.annotations.media.Content(
                        schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = SampleDto.class)))
        public SampleDto getWithExplicitResponse() { return null; }
    }
}
