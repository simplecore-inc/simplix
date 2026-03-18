package dev.simplecore.simplix.web.advice;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.validation.FieldError;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ValidationArgumentProcessor - processes validation arguments from FieldError")
class ValidationArgumentProcessorTest {

    @Nested
    @DisplayName("processArguments")
    class ProcessArguments {

        @Test
        @DisplayName("Should return empty array when error arguments are null")
        void nullArguments() {
            FieldError error = new FieldError("obj", "field", null, false,
                    null, null, "message");

            Object[] result = ValidationArgumentProcessor.processArguments(error);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Should skip DefaultMessageSourceResolvable arguments and extract map values")
        void skipResolvableAndExtractMapValues() {
            DefaultMessageSourceResolvable resolvable = new DefaultMessageSourceResolvable("field");
            Map<String, Object> constraintAttrs = new HashMap<>();
            constraintAttrs.put("max", 100);
            constraintAttrs.put("min", 2);

            FieldError error = new FieldError("obj", "name", null, false,
                    new String[]{"Length.name", "Length"}, new Object[]{resolvable, constraintAttrs},
                    "must be between 2 and 100");

            Object[] result = ValidationArgumentProcessor.processArguments(error);

            assertThat(result).contains(100, 2);
        }

        @Test
        @DisplayName("Should reorder numeric arguments for length processor when message key contains 'length'")
        void lengthProcessorReordering() {
            DefaultMessageSourceResolvable resolvable = new DefaultMessageSourceResolvable("field");
            Map<String, Object> constraintAttrs = new HashMap<>();
            constraintAttrs.put("max", 100);
            constraintAttrs.put("min", 2);

            // Use a {key} format message to trigger processor lookup
            FieldError error = new FieldError("obj", "name", null, false,
                    new String[]{"Length.name", "Length"}, new Object[]{resolvable, constraintAttrs},
                    "{validation.length}");

            Object[] result = ValidationArgumentProcessor.processArguments(error);

            // length processor reorders to min, max: [2, 100]
            assertThat(result).hasSize(2);
            assertThat(((Number) result[0]).intValue()).isLessThanOrEqualTo(((Number) result[1]).intValue());
        }

        @Test
        @DisplayName("Should return empty array when all arguments are resolvable objects")
        void allResolvableArguments() {
            DefaultMessageSourceResolvable resolvable1 = new DefaultMessageSourceResolvable("field1");
            DefaultMessageSourceResolvable resolvable2 = new DefaultMessageSourceResolvable("field2");

            FieldError error = new FieldError("obj", "field", null, false,
                    null, new Object[]{resolvable1, resolvable2}, "message");

            Object[] result = ValidationArgumentProcessor.processArguments(error);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Should pass through non-map, non-resolvable arguments directly")
        void directArguments() {
            DefaultMessageSourceResolvable resolvable = new DefaultMessageSourceResolvable("field");

            FieldError error = new FieldError("obj", "field", null, false,
                    null, new Object[]{resolvable, 42, "text"}, "message");

            Object[] result = ValidationArgumentProcessor.processArguments(error);

            assertThat(result).containsExactly(42, "text");
        }

        @Test
        @DisplayName("Should extract value from map when only value key is present (no min/max)")
        void extractValueFromMap() {
            DefaultMessageSourceResolvable resolvable = new DefaultMessageSourceResolvable("field");
            Map<String, Object> constraintAttrs = new HashMap<>();
            constraintAttrs.put("value", 5);

            FieldError error = new FieldError("obj", "field", null, false,
                    null, new Object[]{resolvable, constraintAttrs}, "message");

            Object[] result = ValidationArgumentProcessor.processArguments(error);

            assertThat(result).containsExactly(5);
        }
    }

    @Nested
    @DisplayName("extractConstraintAttributes")
    class ExtractConstraintAttributes {

        @Test
        @DisplayName("Should return empty map when error arguments are null")
        void nullArguments() {
            FieldError error = new FieldError("obj", "field", null, false,
                    null, null, "message");

            Map<String, Object> result = ValidationArgumentProcessor.extractConstraintAttributes(error);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Should extract attributes from constraint map arguments")
        void extractFromMap() {
            DefaultMessageSourceResolvable resolvable = new DefaultMessageSourceResolvable("field");
            Map<String, Object> constraintAttrs = new HashMap<>();
            constraintAttrs.put("min", 2);
            constraintAttrs.put("max", 100);

            FieldError error = new FieldError("obj", "name", null, false,
                    null, new Object[]{resolvable, constraintAttrs}, "message");

            Map<String, Object> result = ValidationArgumentProcessor.extractConstraintAttributes(error);

            assertThat(result).containsEntry("min", 2).containsEntry("max", 100);
        }

        @Test
        @DisplayName("Should skip non-map arguments")
        void skipNonMapArguments() {
            DefaultMessageSourceResolvable resolvable = new DefaultMessageSourceResolvable("field");

            FieldError error = new FieldError("obj", "field", null, false,
                    null, new Object[]{resolvable, 42}, "message");

            Map<String, Object> result = ValidationArgumentProcessor.extractConstraintAttributes(error);

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("Processor registration")
    class ProcessorRegistration {

        @Test
        @DisplayName("Should register and retrieve custom processor")
        void registerCustomProcessor() {
            Function<Object[], Object[]> customProcessor = args -> new Object[]{"custom"};

            ValidationArgumentProcessor.registerProcessor("custom-key", customProcessor);

            Map<String, Function<Object[], Object[]>> processors =
                    ValidationArgumentProcessor.getRegisteredProcessors();
            assertThat(processors).containsKey("custom-key");

            // Cleanup
            ValidationArgumentProcessor.removeProcessor("custom-key");
        }

        @Test
        @DisplayName("Should remove previously registered processor")
        void removeProcessor() {
            Function<Object[], Object[]> processor = args -> args;
            ValidationArgumentProcessor.registerProcessor("temp-key", processor);

            ValidationArgumentProcessor.removeProcessor("temp-key");

            Map<String, Function<Object[], Object[]>> processors =
                    ValidationArgumentProcessor.getRegisteredProcessors();
            assertThat(processors).doesNotContainKey("temp-key");
        }

        @Test
        @DisplayName("Should include default processors for length, size, max, min, range")
        void defaultProcessorsRegistered() {
            Map<String, Function<Object[], Object[]>> processors =
                    ValidationArgumentProcessor.getRegisteredProcessors();

            assertThat(processors).containsKeys("length", "size", "max", "min", "range");
        }
    }
}
