package dev.simplecore.simplix.springboot.properties;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SimpliXProperties - configuration properties")
class SimpliXPropertiesTest {

    @Test
    @DisplayName("Should have default core properties with enabled=true")
    void defaultCoreEnabled() {
        SimpliXProperties props = new SimpliXProperties();

        assertThat(props.getCore()).isNotNull();
        assertThat(props.getCore().isEnabled()).isTrue();
    }

    @Test
    @DisplayName("Should have default exceptionHandler properties with enabled=true")
    void defaultExceptionHandlerEnabled() {
        SimpliXProperties props = new SimpliXProperties();

        assertThat(props.getExceptionHandler()).isNotNull();
        assertThat(props.getExceptionHandler().isEnabled()).isTrue();
    }

    @Nested
    @DisplayName("DateTimeProperties")
    class DateTimePropertiesTest {

        @Test
        @DisplayName("Should have null defaultTimezone by default")
        void defaultTimezoneNull() {
            SimpliXProperties props = new SimpliXProperties();

            assertThat(props.getDateTime().getDefaultTimezone()).isNull();
        }

        @Test
        @DisplayName("Should have useUtcForDatabase=true by default")
        void useUtcForDatabaseDefault() {
            SimpliXProperties props = new SimpliXProperties();

            assertThat(props.getDateTime().isUseUtcForDatabase()).isTrue();
        }

        @Test
        @DisplayName("Should have normalizeTimezone=true by default")
        void normalizeTimezoneDefault() {
            SimpliXProperties props = new SimpliXProperties();

            assertThat(props.getDateTime().isNormalizeTimezone()).isTrue();
        }

        @Test
        @DisplayName("Should allow setting defaultTimezone")
        void setDefaultTimezone() {
            SimpliXProperties props = new SimpliXProperties();
            props.getDateTime().setDefaultTimezone("Asia/Seoul");

            assertThat(props.getDateTime().getDefaultTimezone()).isEqualTo("Asia/Seoul");
        }

        @Test
        @DisplayName("Should allow setting useUtcForDatabase")
        void setUseUtcForDatabase() {
            SimpliXProperties props = new SimpliXProperties();
            props.getDateTime().setUseUtcForDatabase(false);

            assertThat(props.getDateTime().isUseUtcForDatabase()).isFalse();
        }

        @Test
        @DisplayName("Should allow setting normalizeTimezone")
        void setNormalizeTimezone() {
            SimpliXProperties props = new SimpliXProperties();
            props.getDateTime().setNormalizeTimezone(false);

            assertThat(props.getDateTime().isNormalizeTimezone()).isFalse();
        }
    }

    @Nested
    @DisplayName("CoreProperties")
    class CorePropertiesTest {

        @Test
        @DisplayName("Should allow toggling enabled flag")
        void toggleEnabled() {
            SimpliXProperties.CoreProperties core = new SimpliXProperties.CoreProperties();
            core.setEnabled(false);

            assertThat(core.isEnabled()).isFalse();
        }
    }

    @Nested
    @DisplayName("ExceptionHandlerProperties")
    class ExceptionHandlerPropertiesTest {

        @Test
        @DisplayName("Should allow toggling enabled flag")
        void toggleEnabled() {
            SimpliXProperties.ExceptionHandlerProperties exHandler =
                    new SimpliXProperties.ExceptionHandlerProperties();
            exHandler.setEnabled(false);

            assertThat(exHandler.isEnabled()).isFalse();
        }
    }
}
