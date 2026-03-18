package dev.simplecore.simplix.mybatis.properties;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SimpliXMyBatisProperties")
class SimpliXMyBatisPropertiesTest {

    @Nested
    @DisplayName("Default values")
    class DefaultValues {

        @Test
        @DisplayName("should have enabled set to true by default")
        void shouldHaveEnabledTrueByDefault() {
            SimpliXMyBatisProperties properties = new SimpliXMyBatisProperties();
            assertThat(properties.isEnabled()).isTrue();
        }

        @Test
        @DisplayName("should have default mapper locations")
        void shouldHaveDefaultMapperLocations() {
            SimpliXMyBatisProperties properties = new SimpliXMyBatisProperties();
            assertThat(properties.getMapperLocations()).isEqualTo("classpath*:mapper/**/*.xml");
        }

        @Test
        @DisplayName("should have null typeAliasesPackage by default")
        void shouldHaveNullTypeAliasesPackageByDefault() {
            SimpliXMyBatisProperties properties = new SimpliXMyBatisProperties();
            assertThat(properties.getTypeAliasesPackage()).isNull();
        }

        @Test
        @DisplayName("should have null configLocation by default")
        void shouldHaveNullConfigLocationByDefault() {
            SimpliXMyBatisProperties properties = new SimpliXMyBatisProperties();
            assertThat(properties.getConfigLocation()).isNull();
        }
    }

    @Nested
    @DisplayName("Setter methods")
    class SetterMethods {

        @Test
        @DisplayName("should set enabled property")
        void shouldSetEnabled() {
            SimpliXMyBatisProperties properties = new SimpliXMyBatisProperties();
            properties.setEnabled(false);
            assertThat(properties.isEnabled()).isFalse();
        }

        @Test
        @DisplayName("should set mapper locations")
        void shouldSetMapperLocations() {
            SimpliXMyBatisProperties properties = new SimpliXMyBatisProperties();
            properties.setMapperLocations("classpath*:custom/**/*.xml");
            assertThat(properties.getMapperLocations()).isEqualTo("classpath*:custom/**/*.xml");
        }

        @Test
        @DisplayName("should set type aliases package")
        void shouldSetTypeAliasesPackage() {
            SimpliXMyBatisProperties properties = new SimpliXMyBatisProperties();
            properties.setTypeAliasesPackage("com.example.model");
            assertThat(properties.getTypeAliasesPackage()).isEqualTo("com.example.model");
        }

        @Test
        @DisplayName("should set config location")
        void shouldSetConfigLocation() {
            SimpliXMyBatisProperties properties = new SimpliXMyBatisProperties();
            properties.setConfigLocation("classpath:mybatis-config.xml");
            assertThat(properties.getConfigLocation()).isEqualTo("classpath:mybatis-config.xml");
        }
    }
}
