package dev.simplecore.simplix.web.meta;

import dev.simplecore.simplix.web.meta.model.ConstraintMeta;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.constraints.*;
import org.hibernate.validator.constraints.Length;
import org.junit.jupiter.api.Test;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Field;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ConstraintExtractorTest {

    @Target(ElementType.FIELD)
    @Retention(RetentionPolicy.RUNTIME)
    @Constraint(validatedBy = {})
    @interface Unique {
        String message() default "";
        Class<?>[] groups() default {};
        Class<? extends Payload>[] payload() default {};
    }

    static class Sample {
        @NotBlank @Length(max = 100) String name;
        @Size(min = 1, max = 5) List<String> tags;
        @Min(-1) @Max(9999) Integer occupancy;
        @Pattern(regexp = "^[A-Z]+$") String code;
        @Email String mail;
        @Positive Integer count;
        @Unique String serial;
        String plain;
        @Pattern.List({@Pattern(regexp = "^[A-Z]+$"), @Pattern(regexp = ".{3,}")}) String multiPattern;
    }

    private List<ConstraintMeta> extract(String field) throws Exception {
        Field f = Sample.class.getDeclaredField(field);
        return ConstraintExtractor.extract(f, f.getType());
    }

    @Test
    void stringLengthBecomesMinMaxLength() throws Exception {
        assertThat(extract("name")).containsExactlyInAnyOrder(
            ConstraintMeta.of("notBlank"), ConstraintMeta.of("maxLength", 100));
    }

    @Test
    void collectionSizeBecomesItemBounds() throws Exception {
        assertThat(extract("tags")).containsExactlyInAnyOrder(
            ConstraintMeta.of("minItems", 1), ConstraintMeta.of("maxItems", 5));
    }

    @Test
    void numericBoundsAndSigns() throws Exception {
        assertThat(extract("occupancy")).containsExactlyInAnyOrder(
            ConstraintMeta.of("min", -1L), ConstraintMeta.of("max", 9999L));
        assertThat(extract("count")).containsExactly(ConstraintMeta.of("positive"));
    }

    @Test
    void patternAndEmail() throws Exception {
        assertThat(extract("code")).containsExactly(ConstraintMeta.of("pattern", "^[A-Z]+$"));
        assertThat(extract("mail")).containsExactly(ConstraintMeta.of("email"));
    }

    @Test
    void unknownConstraintAnnotationBecomesCustom() throws Exception {
        assertThat(extract("serial")).containsExactly(ConstraintMeta.custom("Unique"));
    }

    @Test
    void unconstrainedFieldYieldsEmptyList() throws Exception {
        assertThat(extract("plain")).isEmpty();
    }

    @Test
    void repeatedConstraintContainerBecomesCustom() throws Exception {
        assertThat(extract("multiPattern")).containsExactly(ConstraintMeta.custom("Pattern.List"));
    }
}
