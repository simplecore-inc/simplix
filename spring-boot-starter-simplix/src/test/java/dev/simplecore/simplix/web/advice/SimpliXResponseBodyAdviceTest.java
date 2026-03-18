package dev.simplecore.simplix.web.advice;

import dev.simplecore.simplix.core.model.SimpliXApiResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SimpliXResponseBodyAdvice - wraps responses with SimpliXApiResponse")
class SimpliXResponseBodyAdviceTest {

    private SimpliXResponseBodyAdvice advice;

    @Mock
    private MethodParameter returnType;

    @Mock
    private ServerHttpResponse serverResponse;

    @Mock
    private ServerHttpRequest serverRequest;

    @BeforeEach
    void setUp() {
        advice = new SimpliXResponseBodyAdvice();
    }

    @Nested
    @DisplayName("supports")
    class Supports {

        @Test
        @DisplayName("Should return false for springdoc classes")
        @SuppressWarnings("unchecked")
        void excludeSpringdocClasses() {
            when(returnType.getContainingClass())
                    .thenReturn((Class) org.springdoc.core.properties.SpringDocConfigProperties.class);

            boolean result = advice.supports(returnType, MappingJackson2HttpMessageConverter.class);

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("Should return false for swagger classes")
        @SuppressWarnings("unchecked")
        void excludeSwaggerClasses() {
            when(returnType.getContainingClass())
                    .thenReturn((Class) io.swagger.v3.oas.models.OpenAPI.class);

            boolean result = advice.supports(returnType, MappingJackson2HttpMessageConverter.class);

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("Should return false when return type is already SimpliXApiResponse")
        @SuppressWarnings("unchecked")
        void excludeAlreadyWrapped() {
            when(returnType.getContainingClass()).thenReturn((Class) String.class);
            when(returnType.getParameterType()).thenReturn((Class) SimpliXApiResponse.class);

            boolean result = advice.supports(returnType, MappingJackson2HttpMessageConverter.class);

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("Should return true for regular controller classes")
        @SuppressWarnings("unchecked")
        void supportRegularControllers() {
            when(returnType.getContainingClass()).thenReturn((Class) String.class);
            when(returnType.getParameterType()).thenReturn((Class) String.class);

            boolean result = advice.supports(returnType, MappingJackson2HttpMessageConverter.class);

            assertThat(result).isTrue();
        }
    }

    @Nested
    @DisplayName("beforeBodyWrite")
    class BeforeBodyWrite {

        @Test
        @DisplayName("Should wrap null body with success response")
        void wrapNullBody() {
            Object result = advice.beforeBodyWrite(null, null, null, null, null, null);

            assertThat(result).isInstanceOf(SimpliXApiResponse.class);
            SimpliXApiResponse<?> response = (SimpliXApiResponse<?>) result;
            assertThat(response.getType()).isEqualTo("SUCCESS");
            assertThat(response.getBody()).isNull();
        }

        @Test
        @DisplayName("Should return existing SimpliXApiResponse as-is")
        void returnExistingApiResponse() {
            SimpliXApiResponse<String> existing = SimpliXApiResponse.success("data");

            Object result = advice.beforeBodyWrite(existing, null, null, null, null, null);

            assertThat(result).isSameAs(existing);
        }

        @Test
        @DisplayName("Should wrap regular object with success response")
        void wrapRegularObject() {
            String body = "test data";

            Object result = advice.beforeBodyWrite(body, null, null, null, null, null);

            assertThat(result).isInstanceOf(SimpliXApiResponse.class);
            SimpliXApiResponse<?> response = (SimpliXApiResponse<?>) result;
            assertThat(response.getType()).isEqualTo("SUCCESS");
            assertThat(response.getBody()).isEqualTo("test data");
        }

        @Test
        @DisplayName("Should unwrap ResponseEntity and wrap its body with success response")
        void unwrapResponseEntity() {
            ResponseEntity<String> responseEntity = ResponseEntity.ok("entity body");
            HttpHeaders headers = new HttpHeaders();
            when(serverResponse.getHeaders()).thenReturn(headers);

            Object result = advice.beforeBodyWrite(responseEntity, null, null, null, null, serverResponse);

            assertThat(result).isInstanceOf(SimpliXApiResponse.class);
            SimpliXApiResponse<?> response = (SimpliXApiResponse<?>) result;
            assertThat(response.getType()).isEqualTo("SUCCESS");
            assertThat(response.getBody()).isEqualTo("entity body");
        }

        @Test
        @DisplayName("Should wrap integer body with success response")
        void wrapIntegerBody() {
            Object result = advice.beforeBodyWrite(42, null, null, null, null, null);

            assertThat(result).isInstanceOf(SimpliXApiResponse.class);
            SimpliXApiResponse<?> response = (SimpliXApiResponse<?>) result;
            assertThat(response.getType()).isEqualTo("SUCCESS");
            assertThat(response.getBody()).isEqualTo(42);
        }

        @Test
        @DisplayName("Should wrap list body with success response")
        void wrapListBody() {
            var body = java.util.List.of("a", "b", "c");

            Object result = advice.beforeBodyWrite(body, null, null, null, null, null);

            assertThat(result).isInstanceOf(SimpliXApiResponse.class);
            SimpliXApiResponse<?> response = (SimpliXApiResponse<?>) result;
            assertThat(response.getType()).isEqualTo("SUCCESS");
            assertThat(response.getBody()).isEqualTo(body);
        }

        @Test
        @DisplayName("Should handle ResponseEntity with null serverResponse")
        void handleResponseEntityWithNullServerResponse() {
            ResponseEntity<String> responseEntity = ResponseEntity.ok("body");

            Object result = advice.beforeBodyWrite(responseEntity, null, null, null, null, null);

            assertThat(result).isInstanceOf(SimpliXApiResponse.class);
            SimpliXApiResponse<?> response = (SimpliXApiResponse<?>) result;
            assertThat(response.getType()).isEqualTo("SUCCESS");
            assertThat(response.getBody()).isEqualTo("body");
        }

        @Test
        @DisplayName("Should set status code and headers from ResponseEntity")
        void setsStatusCodeAndHeaders() {
            HttpHeaders entityHeaders = new HttpHeaders();
            entityHeaders.add("X-Custom", "value");
            ResponseEntity<String> responseEntity = ResponseEntity
                    .status(201)
                    .headers(entityHeaders)
                    .body("created");

            HttpHeaders responseHeaders = new HttpHeaders();
            when(serverResponse.getHeaders()).thenReturn(responseHeaders);

            Object result = advice.beforeBodyWrite(responseEntity, null, null, null, null, serverResponse);

            assertThat(result).isInstanceOf(SimpliXApiResponse.class);
            verify(serverResponse).setStatusCode(HttpStatusCode.valueOf(201));
        }
    }
}
