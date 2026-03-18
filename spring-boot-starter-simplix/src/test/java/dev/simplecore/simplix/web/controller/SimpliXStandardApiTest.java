package dev.simplecore.simplix.web.controller;

import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.annotation.*;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SimpliXStandardApi - annotation meta-test to verify annotation structure")
class SimpliXStandardApiTest {

    @Test
    @DisplayName("Should be annotated with @ApiResponses containing 200, 400, and 500 responses")
    void hasApiResponses() {
        ApiResponses apiResponses = SimpliXStandardApi.class.getAnnotation(ApiResponses.class);

        assertThat(apiResponses).isNotNull();
        assertThat(apiResponses.value()).hasSize(3);

        ApiResponse[] responses = apiResponses.value();

        // Check response codes
        assertThat(responses[0].responseCode()).isEqualTo("200");
        assertThat(responses[1].responseCode()).isEqualTo("400");
        assertThat(responses[2].responseCode()).isEqualTo("500");
    }

    @Test
    @DisplayName("Should be a RUNTIME retention annotation")
    void runtimeRetention() {
        Retention retention = SimpliXStandardApi.class.getAnnotation(Retention.class);

        assertThat(retention).isNotNull();
        assertThat(retention.value()).isEqualTo(RetentionPolicy.RUNTIME);
    }

    @Test
    @DisplayName("Should target METHOD and TYPE")
    void targetMethodAndType() {
        Target target = SimpliXStandardApi.class.getAnnotation(Target.class);

        assertThat(target).isNotNull();
        assertThat(target.value()).contains(ElementType.METHOD, ElementType.TYPE);
    }

    @Test
    @DisplayName("Should be inheritable")
    void isInheritable() {
        Inherited inherited = SimpliXStandardApi.class.getAnnotation(Inherited.class);

        assertThat(inherited).isNotNull();
    }

    @Test
    @DisplayName("Should be applicable to a class")
    void applicableToClass() {
        SimpliXStandardApi annotation = AnnotatedClass.class.getAnnotation(SimpliXStandardApi.class);

        assertThat(annotation).isNotNull();
    }

    @Test
    @DisplayName("Should be inherited by subclass")
    void inheritedBySubclass() {
        SimpliXStandardApi annotation = SubAnnotatedClass.class.getAnnotation(SimpliXStandardApi.class);

        assertThat(annotation).isNotNull();
    }

    @SimpliXStandardApi
    static class AnnotatedClass {}

    static class SubAnnotatedClass extends AnnotatedClass {}
}
