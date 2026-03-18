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

/**
 * Additional tests for ValidationArgumentProcessor targeting uncovered branches:
 * - size processor
 * - max processor
 * - min processor (single arg and two args)
 * - range processor
 * - constraintType extraction with dot prefix
 * - message key pattern matching
 * - processNumericArguments with reversed order
 * - extractConstraintType with null/empty codes
 */
@DisplayName("ValidationArgumentProcessor - additional branch coverage tests")
class ValidationArgumentProcessorAdditionalTest {

    @Nested
    @DisplayName("Size processor")
    class SizeProcessor {

        @Test
        @DisplayName("Should apply size processor when constraint type is Size")
        void sizeProcessorApplied() {
            DefaultMessageSourceResolvable resolvable = new DefaultMessageSourceResolvable("field");
            Map<String, Object> constraintAttrs = new HashMap<>();
            constraintAttrs.put("max", 50);
            constraintAttrs.put("min", 5);

            FieldError error = new FieldError("obj", "name", null, false,
                    new String[]{"Size.user.name", "Size.name", "Size.java.lang.String", "Size"},
                    new Object[]{resolvable, constraintAttrs},
                    "{validation.size}");

            Object[] result = ValidationArgumentProcessor.processArguments(error);

            assertThat(result).hasSize(2);
            // processNumericArguments should ensure min before max
            assertThat(((Number) result[0]).intValue()).isLessThanOrEqualTo(((Number) result[1]).intValue());
        }
    }

    @Nested
    @DisplayName("Max processor")
    class MaxProcessor {

        @Test
        @DisplayName("Should return first argument for max processor")
        void maxProcessorReturnFirstArg() {
            Map<String, Function<Object[], Object[]>> processors =
                    ValidationArgumentProcessor.getRegisteredProcessors();
            Function<Object[], Object[]> maxProcessor = processors.get("max");

            Object[] result = maxProcessor.apply(new Object[]{100, 5});

            assertThat(result).containsExactly(100);
        }

        @Test
        @DisplayName("Should return empty array for max processor with no arguments")
        void maxProcessorEmptyArgs() {
            Map<String, Function<Object[], Object[]>> processors =
                    ValidationArgumentProcessor.getRegisteredProcessors();
            Function<Object[], Object[]> maxProcessor = processors.get("max");

            Object[] result = maxProcessor.apply(new Object[0]);

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("Min processor")
    class MinProcessor {

        @Test
        @DisplayName("Should return second argument when two args available")
        void minProcessorTwoArgs() {
            Map<String, Function<Object[], Object[]>> processors =
                    ValidationArgumentProcessor.getRegisteredProcessors();
            Function<Object[], Object[]> minProcessor = processors.get("min");

            Object[] result = minProcessor.apply(new Object[]{100, 5});

            assertThat(result).containsExactly(5);
        }

        @Test
        @DisplayName("Should return first argument when only one arg available")
        void minProcessorOneArg() {
            Map<String, Function<Object[], Object[]>> processors =
                    ValidationArgumentProcessor.getRegisteredProcessors();
            Function<Object[], Object[]> minProcessor = processors.get("min");

            Object[] result = minProcessor.apply(new Object[]{5});

            assertThat(result).containsExactly(5);
        }

        @Test
        @DisplayName("Should return empty array when no args available")
        void minProcessorNoArgs() {
            Map<String, Function<Object[], Object[]>> processors =
                    ValidationArgumentProcessor.getRegisteredProcessors();
            Function<Object[], Object[]> minProcessor = processors.get("min");

            Object[] result = minProcessor.apply(new Object[0]);

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("Range processor")
    class RangeProcessor {

        @Test
        @DisplayName("Should swap arguments when max comes before min")
        void rangeProcessorSwap() {
            Map<String, Function<Object[], Object[]>> processors =
                    ValidationArgumentProcessor.getRegisteredProcessors();
            Function<Object[], Object[]> rangeProcessor = processors.get("range");

            // max=100, min=5 -> should swap to 5, 100
            Object[] result = rangeProcessor.apply(new Object[]{100, 5});

            assertThat(result).containsExactly(5, 100);
        }

        @Test
        @DisplayName("Should not swap when already in correct order")
        void rangeProcessorNoSwap() {
            Map<String, Function<Object[], Object[]>> processors =
                    ValidationArgumentProcessor.getRegisteredProcessors();
            Function<Object[], Object[]> rangeProcessor = processors.get("range");

            Object[] result = rangeProcessor.apply(new Object[]{5, 100});

            assertThat(result).containsExactly(5, 100);
        }

        @Test
        @DisplayName("Should return as-is when not exactly two arguments")
        void rangeProcessorSingleArg() {
            Map<String, Function<Object[], Object[]>> processors =
                    ValidationArgumentProcessor.getRegisteredProcessors();
            Function<Object[], Object[]> rangeProcessor = processors.get("range");

            Object[] result = rangeProcessor.apply(new Object[]{42});

            assertThat(result).containsExactly(42);
        }

        @Test
        @DisplayName("Should return as-is when arguments are not numbers")
        void rangeProcessorNonNumericArgs() {
            Map<String, Function<Object[], Object[]>> processors =
                    ValidationArgumentProcessor.getRegisteredProcessors();
            Function<Object[], Object[]> rangeProcessor = processors.get("range");

            Object[] result = rangeProcessor.apply(new Object[]{"hello", "world"});

            assertThat(result).containsExactly("hello", "world");
        }
    }

    @Nested
    @DisplayName("extractConstraintType")
    class ExtractConstraintType {

        @Test
        @DisplayName("Should extract constraint type from codes with dot prefix")
        void extractWithDotPrefix() {
            DefaultMessageSourceResolvable resolvable = new DefaultMessageSourceResolvable("field");
            Map<String, Object> constraintAttrs = new HashMap<>();
            constraintAttrs.put("value", 10);

            FieldError error = new FieldError("obj", "field", null, false,
                    new String[]{"Min.obj.field", "Min.field", "Min.int", "Min"},
                    new Object[]{resolvable, constraintAttrs},
                    "{jakarta.validation.constraints.Min.message}");

            // The constraint type "Min" should be extracted
            Object[] result = ValidationArgumentProcessor.processArguments(error);
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("Should handle null error codes")
        void nullCodes() {
            DefaultMessageSourceResolvable resolvable = new DefaultMessageSourceResolvable("field");

            FieldError error = new FieldError("obj", "field", null, false,
                    null, new Object[]{resolvable, 42}, "{some.key}");

            Object[] result = ValidationArgumentProcessor.processArguments(error);
            assertThat(result).containsExactly(42);
        }

        @Test
        @DisplayName("Should handle empty error codes")
        void emptyCodes() {
            DefaultMessageSourceResolvable resolvable = new DefaultMessageSourceResolvable("field");

            FieldError error = new FieldError("obj", "field", null, false,
                    new String[]{}, new Object[]{resolvable, 42}, "{some.key}");

            Object[] result = ValidationArgumentProcessor.processArguments(error);
            assertThat(result).containsExactly(42);
        }
    }

    @Nested
    @DisplayName("Message key pattern matching")
    class MessageKeyPatternMatching {

        @Test
        @DisplayName("Should match message key pattern to registered processor")
        void matchMessageKeyPattern() {
            DefaultMessageSourceResolvable resolvable = new DefaultMessageSourceResolvable("field");
            Map<String, Object> constraintAttrs = new HashMap<>();
            constraintAttrs.put("max", 200);
            constraintAttrs.put("min", 10);

            // Message key containing "size" should match the size processor
            FieldError error = new FieldError("obj", "field", null, false,
                    new String[]{"CustomAnnotation"},
                    new Object[]{resolvable, constraintAttrs},
                    "{custom.validation.size.message}");

            Object[] result = ValidationArgumentProcessor.processArguments(error);

            assertThat(result).hasSize(2);
        }

        @Test
        @DisplayName("Should fall through to default when no processor matches")
        void noProcessorMatch() {
            DefaultMessageSourceResolvable resolvable = new DefaultMessageSourceResolvable("field");

            FieldError error = new FieldError("obj", "field", null, false,
                    new String[]{"UnknownAnnotation"},
                    new Object[]{resolvable, "someValue"},
                    "{custom.unknown.key}");

            Object[] result = ValidationArgumentProcessor.processArguments(error);

            assertThat(result).containsExactly("someValue");
        }

        @Test
        @DisplayName("Should handle non-template message (no braces)")
        void nonTemplateMessage() {
            DefaultMessageSourceResolvable resolvable = new DefaultMessageSourceResolvable("field");
            Map<String, Object> constraintAttrs = new HashMap<>();
            constraintAttrs.put("max", 50);
            constraintAttrs.put("min", 1);

            FieldError error = new FieldError("obj", "field", null, false,
                    new String[]{"Size"},
                    new Object[]{resolvable, constraintAttrs},
                    "plain message without braces");

            Object[] result = ValidationArgumentProcessor.processArguments(error);

            // Should return extracted args without going through processor lookup
            assertThat(result).hasSize(2);
        }
    }

    @Nested
    @DisplayName("Map with value key and min/max present")
    class MapValueExtraction {

        @Test
        @DisplayName("Should not extract value when min and max are both present")
        void valueNotExtractedWithMinMax() {
            DefaultMessageSourceResolvable resolvable = new DefaultMessageSourceResolvable("field");
            Map<String, Object> constraintAttrs = new HashMap<>();
            constraintAttrs.put("min", 1);
            constraintAttrs.put("max", 100);
            constraintAttrs.put("value", 50);

            FieldError error = new FieldError("obj", "field", null, false,
                    null, new Object[]{resolvable, constraintAttrs}, "message");

            Object[] result = ValidationArgumentProcessor.processArguments(error);

            // Should extract max and min, but not value (because min/max are present)
            assertThat(result).containsExactly(100, 1);
        }
    }
}
