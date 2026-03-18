package dev.simplecore.simplix.core.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SimpliXCompositeKey")
class SimpliXCompositeKeyTest {

    // Concrete implementation for testing
    static class TestCompositeKey implements SimpliXCompositeKey {
        private String tenantId;
        private Long userId;

        public TestCompositeKey() {}

        public TestCompositeKey(String tenantId, Long userId) {
            this.tenantId = tenantId;
            this.userId = userId;
        }

        @Override
        public SimpliXCompositeKey fromPathVariables(String... pathVariables) {
            if (pathVariables.length != 2) {
                throw new IllegalArgumentException("Expected 2 path variables");
            }
            this.tenantId = pathVariables[0];
            this.userId = Long.parseLong(pathVariables[1]);
            return this;
        }

        @Override
        public SimpliXCompositeKey fromCompositeId(String compositeId) {
            String[] parts = compositeId.split("__");
            if (parts.length != 2) {
                throw new IllegalArgumentException("Invalid format");
            }
            this.tenantId = parts[0];
            this.userId = Long.parseLong(parts[1]);
            return this;
        }

        @Override
        public void validate() {
            if (tenantId == null || userId == null) {
                throw new RuntimeException("Both tenantId and userId are required");
            }
        }

        @Override
        public String toString() {
            return toCompositeKeyString();
        }
    }

    // Test class with a static field
    static class KeyWithStatic implements SimpliXCompositeKey {
        private static final long serialVersionUID = 1L;
        private String code;

        public KeyWithStatic() {}
        public KeyWithStatic(String code) { this.code = code; }

        @Override
        public SimpliXCompositeKey fromPathVariables(String... pathVariables) { return this; }
        @Override
        public SimpliXCompositeKey fromCompositeId(String compositeId) { return this; }
        @Override
        public void validate() {}
    }

    @Nested
    @DisplayName("toCompositeKeyString")
    class ToCompositeKeyString {

        @Test
        @DisplayName("should join field values with __ delimiter")
        void shouldJoinFieldValues() {
            TestCompositeKey key = new TestCompositeKey("tenant1", 42L);
            String result = key.toCompositeKeyString();

            assertThat(result).isEqualTo("tenant1__42");
        }

        @Test
        @DisplayName("should handle null field values")
        void shouldHandleNullValues() {
            TestCompositeKey key = new TestCompositeKey("tenant1", null);
            String result = key.toCompositeKeyString();

            assertThat(result).isEqualTo("tenant1__null");
        }

        @Test
        @DisplayName("should skip static fields")
        void shouldSkipStaticFields() {
            KeyWithStatic key = new KeyWithStatic("ABC");
            String result = key.toCompositeKeyString();

            assertThat(result).isEqualTo("ABC");
            assertThat(result).doesNotContain("serialVersionUID");
        }
    }

    @Nested
    @DisplayName("fromPathVariables")
    class FromPathVariables {

        @Test
        @DisplayName("should populate key from path variables")
        void shouldPopulateFromPathVariables() {
            TestCompositeKey key = new TestCompositeKey();
            key.fromPathVariables("myTenant", "100");

            assertThat(key.toCompositeKeyString()).isEqualTo("myTenant__100");
        }
    }

    @Nested
    @DisplayName("fromCompositeId")
    class FromCompositeId {

        @Test
        @DisplayName("should populate key from composite string")
        void shouldPopulateFromCompositeId() {
            TestCompositeKey key = new TestCompositeKey();
            key.fromCompositeId("tenantX__200");

            assertThat(key.toCompositeKeyString()).isEqualTo("tenantX__200");
        }
    }

    @Nested
    @DisplayName("validate")
    class Validate {

        @Test
        @DisplayName("should pass validation for valid key")
        void shouldPassForValidKey() {
            TestCompositeKey key = new TestCompositeKey("t1", 1L);
            key.validate(); // should not throw
        }
    }
}
