package dev.simplecore.simplix.springboot.autoconfigure;

import dev.simplecore.simplix.web.config.openapi.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springdoc.core.properties.SpringDocConfigProperties;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
@DisplayName("SimpliXSwaggerAutoConfiguration - Swagger/OpenAPI auto-configuration")
class SimpliXSwaggerAutoConfigurationTest {

    private SimpliXSwaggerAutoConfiguration config;

    @Mock
    private RequestMappingHandlerMapping handlerMapping;

    @BeforeEach
    void setUp() {
        config = new SimpliXSwaggerAutoConfiguration();
    }

    @Nested
    @DisplayName("springDocConfigProperties")
    class SpringDocConfig {

        @Test
        @DisplayName("Should configure API docs path to /v3/api-docs")
        void apiDocsPath() {
            SpringDocConfigProperties props = config.springDocConfigProperties();

            assertThat(props.getApiDocs().getPath()).isEqualTo("/v3/api-docs");
            assertThat(props.getApiDocs().isEnabled()).isTrue();
        }

        @Test
        @DisplayName("Should disable actuator endpoints in docs")
        void actuatorDisabled() {
            SpringDocConfigProperties props = config.springDocConfigProperties();

            assertThat(props.isShowActuator()).isFalse();
        }
    }

    @Nested
    @DisplayName("Bean creation")
    class BeanCreation {

        @Test
        @DisplayName("Should create EnumSchemaExtractor bean")
        void createEnumSchemaExtractor() {
            EnumSchemaExtractor extractor = config.enumSchemaExtractor();

            assertThat(extractor).isNotNull();
        }

        @Test
        @DisplayName("Should create NestedObjectSchemaExtractor bean")
        void createNestedObjectSchemaExtractor() {
            NestedObjectSchemaExtractor extractor = config.nestedObjectSchemaExtractor();

            assertThat(extractor).isNotNull();
        }

        @Test
        @DisplayName("Should create GenericResponseSchemaCustomizer with autoWrap enabled")
        void createGenericResponseSchemaCustomizer() {
            GenericResponseSchemaCustomizer customizer =
                    config.genericResponseSchemaCustomizer(true);

            assertThat(customizer).isNotNull();
        }

        @Test
        @DisplayName("Should create GenericResponseSchemaCustomizer with autoWrap disabled")
        void createGenericResponseSchemaCustomizerNoAutoWrap() {
            GenericResponseSchemaCustomizer customizer =
                    config.genericResponseSchemaCustomizer(false);

            assertThat(customizer).isNotNull();
        }

        @Test
        @DisplayName("Should create OperationIdCustomizer bean")
        void createOperationIdCustomizer() {
            OperationIdCustomizer customizer = config.operationIdCustomizer();

            assertThat(customizer).isNotNull();
        }

        @Test
        @DisplayName("Should create SchemaOrganizer bean")
        void createSchemaOrganizer() {
            SchemaOrganizer organizer = config.schemaOrganizer();

            assertThat(organizer).isNotNull();
        }

        @Test
        @DisplayName("Should create DtoSchemaAutoRegistrar bean")
        void createDtoSchemaAutoRegistrar() {
            DtoSchemaAutoRegistrar registrar = config.dtoSchemaAutoRegistrar(handlerMapping);

            assertThat(registrar).isNotNull();
        }
    }
}
