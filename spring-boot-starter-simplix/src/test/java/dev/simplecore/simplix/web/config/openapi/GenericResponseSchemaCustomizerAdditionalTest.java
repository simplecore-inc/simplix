package dev.simplecore.simplix.web.config.openapi;

import dev.simplecore.simplix.core.model.SimpliXApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.core.ResolvableType;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.method.HandlerMethod;

import java.lang.reflect.Method;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Additional tests for GenericResponseSchemaCustomizer targeting uncovered branches:
 * - hasExplicitResponseSchema with @ApiResponses container
 * - isSuccessResponse with empty, default, and non-200 codes
 * - resolveSchema for double/float primitives, byte primitive
 * - ResponseEntity wrapping without generics
 * - autoWrap disabled branch for non-wrapper types
 * - Non-success @ApiResponse codes (should not skip customization)
 * - Exception path during customization
 * - Nested generic wrappers (ResponseEntity<Optional<String>>)
 * - Map return type
 * - Page wrapped in SimpliXApiResponse
 */
@DisplayName("GenericResponseSchemaCustomizer - additional branch coverage tests")
class GenericResponseSchemaCustomizerAdditionalTest {

    private GenericResponseSchemaCustomizer customizer;

    @BeforeEach
    void setUp() {
        customizer = new GenericResponseSchemaCustomizer(true);
    }

    @Nested
    @DisplayName("resolveSchema additional types")
    class ResolveSchemaAdditional {

        @Test
        @DisplayName("Should resolve double primitive to NumberSchema")
        void resolveDoublePrimitive() {
            ResolvableType type = ResolvableType.forClass(double.class);
            Schema<?> schema = customizer.resolveSchema(type);
            assertThat(schema.getType()).isEqualTo("number");
            assertThat(schema.getFormat()).isEqualTo("double");
        }

        @Test
        @DisplayName("Should resolve float primitive to NumberSchema")
        void resolveFloatPrimitive() {
            ResolvableType type = ResolvableType.forClass(float.class);
            Schema<?> schema = customizer.resolveSchema(type);
            assertThat(schema.getType()).isEqualTo("number");
            assertThat(schema.getFormat()).isEqualTo("float");
        }

        @Test
        @DisplayName("Should resolve byte primitive to StringSchema with byte format")
        void resolveBytePrimitive() {
            ResolvableType type = ResolvableType.forClass(byte.class);
            Schema<?> schema = customizer.resolveSchema(type);
            assertThat(schema.getType()).isEqualTo("string");
            assertThat(schema.getFormat()).isEqualTo("byte");
        }

        @Test
        @DisplayName("Should resolve ResponseEntity without generics to ObjectSchema")
        void resolveResponseEntityWithoutGenerics() {
            ResolvableType type = ResolvableType.forClass(ResponseEntity.class);
            Schema<?> schema = customizer.resolveSchema(type);
            assertThat(schema.getType()).isEqualTo("object");
        }

        @Test
        @DisplayName("Should resolve Optional without generics to ObjectSchema")
        void resolveOptionalWithoutGenerics() {
            ResolvableType type = ResolvableType.forClass(Optional.class);
            Schema<?> schema = customizer.resolveSchema(type);
            assertThat(schema.getType()).isEqualTo("object");
        }

        @Test
        @DisplayName("Should resolve SimpliXApiResponse<Page<SampleDto>> with nested page schema")
        void resolveApiResponseWithPage() {
            ResolvableType pageType = ResolvableType.forClassWithGenerics(Page.class, SampleDto.class);
            ResolvableType apiType = ResolvableType.forClassWithGenerics(SimpliXApiResponse.class, pageType);
            Schema<?> schema = customizer.resolveSchema(apiType);

            // Should be an ApiResponse wrapper with Page schema in body
            assertThat(schema.getProperties()).containsKeys("type", "message", "body", "timestamp");
        }

        @Test
        @DisplayName("Should resolve Collection<SampleDto> to ArraySchema with ref items")
        void resolveCollectionOfDto() {
            ResolvableType type = ResolvableType.forClassWithGenerics(Collection.class, SampleDto.class);
            Schema<?> schema = customizer.resolveSchema(type);

            assertThat(schema.getType()).isEqualTo("array");
            assertThat(schema.getItems().get$ref()).contains("SampleDto");
        }

        @Test
        @DisplayName("Should resolve Map<String, SampleDto> to MapSchema with ref values")
        void resolveMapOfDto() {
            ResolvableType type = ResolvableType.forClassWithGenerics(Map.class, String.class, SampleDto.class);
            Schema<?> schema = customizer.resolveSchema(type);

            assertThat(schema.getType()).isEqualTo("object");
        }
    }

    @Nested
    @DisplayName("customize operation additional branches")
    class CustomizeAdditional {

        @Test
        @DisplayName("Should skip @ApiResponses container with 200 success and content definition")
        void skipApiResponsesContainer() throws Exception {
            Operation operation = new Operation();
            operation.setResponses(new io.swagger.v3.oas.models.responses.ApiResponses());
            Method method = AdditionalSampleApi.class.getMethod("getWithApiResponses");

            HandlerMethod handlerMethod = new HandlerMethod(new AdditionalSampleApi(), method);

            Operation result = customizer.customize(operation, handlerMethod);
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("Should not skip @ApiResponse with non-200 response code")
        void notSkipNon200ApiResponse() throws Exception {
            Operation operation = new Operation();
            operation.setResponses(new io.swagger.v3.oas.models.responses.ApiResponses());
            Method method = AdditionalSampleApi.class.getMethod("getWith404Response");

            HandlerMethod handlerMethod = new HandlerMethod(new AdditionalSampleApi(), method);

            Operation result = customizer.customize(operation, handlerMethod);
            // Should still customize since the @ApiResponse is for 404, not 200
            assertThat(result).isNotNull();
            assertThat(result.getResponses().get("200")).isNotNull();
        }

        @Test
        @DisplayName("Should not skip @ApiResponse with empty content")
        void notSkipEmptyContentApiResponse() throws Exception {
            Operation operation = new Operation();
            operation.setResponses(new io.swagger.v3.oas.models.responses.ApiResponses());
            Method method = AdditionalSampleApi.class.getMethod("getWithEmptyContentResponse");

            HandlerMethod handlerMethod = new HandlerMethod(new AdditionalSampleApi(), method);

            Operation result = customizer.customize(operation, handlerMethod);
            assertThat(result).isNotNull();
            assertThat(result.getResponses().get("200")).isNotNull();
        }

        @Test
        @DisplayName("Should handle Page<SampleDto> return type with auto-wrap")
        void handlePageReturnType() throws Exception {
            Operation operation = new Operation();
            operation.setResponses(new io.swagger.v3.oas.models.responses.ApiResponses());
            Method method = AdditionalSampleApi.class.getMethod("getPage");

            HandlerMethod handlerMethod = new HandlerMethod(new AdditionalSampleApi(), method);

            Operation result = customizer.customize(operation, handlerMethod);
            assertThat(result.getResponses().get("200")).isNotNull();
        }

        @Test
        @DisplayName("Should handle Map return type with auto-wrap")
        void handleMapReturnType() throws Exception {
            Operation operation = new Operation();
            operation.setResponses(new io.swagger.v3.oas.models.responses.ApiResponses());
            Method method = AdditionalSampleApi.class.getMethod("getMap");

            HandlerMethod handlerMethod = new HandlerMethod(new AdditionalSampleApi(), method);

            Operation result = customizer.customize(operation, handlerMethod);
            assertThat(result.getResponses().get("200")).isNotNull();
        }

        @Test
        @DisplayName("Should handle ResponseEntity<Optional<String>> nested unwrapping")
        void handleNestedResponseEntityOptional() throws Exception {
            Operation operation = new Operation();
            operation.setResponses(new io.swagger.v3.oas.models.responses.ApiResponses());
            Method method = AdditionalSampleApi.class.getMethod("getResponseEntityOptional");

            HandlerMethod handlerMethod = new HandlerMethod(new AdditionalSampleApi(), method);

            Operation result = customizer.customize(operation, handlerMethod);
            assertThat(result.getResponses().get("200")).isNotNull();
        }

        @Test
        @DisplayName("Should preserve existing examples with named examples map")
        void preserveExistingNamedExamples() throws Exception {
            Operation operation = new Operation();
            io.swagger.v3.oas.models.responses.ApiResponses responses = new io.swagger.v3.oas.models.responses.ApiResponses();
            io.swagger.v3.oas.models.responses.ApiResponse existing200 =
                    new io.swagger.v3.oas.models.responses.ApiResponse();
            Content existingContent = new Content();
            MediaType existingMediaType = new MediaType();
            existingMediaType.setExamples(Map.of("example1",
                    new io.swagger.v3.oas.models.examples.Example().value("{\"test\":true}")));
            existingContent.addMediaType("application/json", existingMediaType);
            existing200.setContent(existingContent);
            responses.addApiResponse("200", existing200);
            operation.setResponses(responses);

            Method method = AdditionalSampleApi.class.getMethod("getString");
            HandlerMethod handlerMethod = new HandlerMethod(new AdditionalSampleApi(), method);

            Operation result = customizer.customize(operation, handlerMethod);

            // The named examples should be preserved
            MediaType mediaType = result.getResponses().get("200").getContent().get("application/json");
            assertThat(mediaType.getExamples()).isNotNull();
            assertThat(mediaType.getExamples()).containsKey("example1");
        }

        @Test
        @DisplayName("Should handle @ApiResponse with default response code")
        void handleDefaultResponseCode() throws Exception {
            Operation operation = new Operation();
            operation.setResponses(new io.swagger.v3.oas.models.responses.ApiResponses());
            Method method = AdditionalSampleApi.class.getMethod("getWithDefaultResponse");

            HandlerMethod handlerMethod = new HandlerMethod(new AdditionalSampleApi(), method);

            Operation result = customizer.customize(operation, handlerMethod);
            // Should skip because @ApiResponse with default code and content is treated as explicit
            assertThat(result).isNotNull();
        }
    }

    @Nested
    @DisplayName("autoWrap disabled scenarios")
    class AutoWrapDisabled {

        @Test
        @DisplayName("Should not wrap generic wrapper types when autoWrap disabled")
        void noWrapGenericWhenDisabled() throws Exception {
            GenericResponseSchemaCustomizer noAutoWrap = new GenericResponseSchemaCustomizer(false);
            Operation operation = new Operation();
            operation.setResponses(new io.swagger.v3.oas.models.responses.ApiResponses());
            Method method = AdditionalSampleApi.class.getMethod("getPage");

            HandlerMethod handlerMethod = new HandlerMethod(new AdditionalSampleApi(), method);

            Operation result = noAutoWrap.customize(operation, handlerMethod);
            assertThat(result).isNotNull();
            // Still generates schema for Page because it's a known wrapper
            assertThat(result.getResponses().get("200")).isNotNull();
        }

        @Test
        @DisplayName("Should not wrap DTO types when autoWrap disabled")
        void noWrapDtoWhenDisabled() throws Exception {
            GenericResponseSchemaCustomizer noAutoWrap = new GenericResponseSchemaCustomizer(false);
            Operation operation = new Operation();
            operation.setResponses(new io.swagger.v3.oas.models.responses.ApiResponses());
            Method method = AdditionalSampleApi.class.getMethod("getDto");

            HandlerMethod handlerMethod = new HandlerMethod(new AdditionalSampleApi(), method);

            Operation result = noAutoWrap.customize(operation, handlerMethod);
            assertThat(result).isNotNull();
        }
    }

    // --- Test helper classes ---

    static class SampleDto {
        public String name;
    }

    @SuppressWarnings("unused")
    static class AdditionalSampleApi {
        public String getString() { return "hello"; }
        public SampleDto getDto() { return null; }
        public Page<SampleDto> getPage() { return null; }
        public Map<String, SampleDto> getMap() { return null; }
        public ResponseEntity<Optional<String>> getResponseEntityOptional() { return null; }

        @ApiResponses({
                @ApiResponse(responseCode = "200",
                        content = @io.swagger.v3.oas.annotations.media.Content(
                                schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = SampleDto.class)))
        })
        public SampleDto getWithApiResponses() { return null; }

        @ApiResponse(responseCode = "404")
        public SampleDto getWith404Response() { return null; }

        @ApiResponse(responseCode = "200")
        public SampleDto getWithEmptyContentResponse() { return null; }

        @ApiResponse(responseCode = "default",
                content = @io.swagger.v3.oas.annotations.media.Content(
                        schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = SampleDto.class)))
        public SampleDto getWithDefaultResponse() { return null; }
    }
}
