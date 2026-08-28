package dev.simplecore.simplix.web.meta;

import dev.simplecore.simplix.web.meta.model.ConstraintMeta;
import jakarta.validation.Constraint;
import jakarta.validation.constraints.AssertFalse;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Negative;
import jakarta.validation.constraints.NegativeOrZero;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.Length;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.util.ArrayList;
import java.util.List;

/**
 * Maps jakarta/hibernate validation annotations to IR constraints.
 *
 * <p>{@code @NotNull} is deliberately NOT emitted here — it feeds the {@code required} flag.
 * Unknown {@code @Constraint}-meta-annotated annotations become {@code custom} entries, which
 * the generator surfaces as server-only checks instead of silently dropping.
 */
public final class ConstraintExtractor {

    private ConstraintExtractor() {}

    public static List<ConstraintMeta> extract(AnnotatedElement element, Class<?> fieldType) {
        List<ConstraintMeta> result = new ArrayList<>();
        boolean stringLike = CharSequence.class.isAssignableFrom(fieldType);

        for (Annotation annotation : element.getAnnotations()) {
            if (annotation instanceof NotBlank) result.add(ConstraintMeta.of("notBlank"));
            else if (annotation instanceof NotEmpty) result.add(ConstraintMeta.of("notEmpty"));
            else if (annotation instanceof Size size) addBounds(result, stringLike, size.min(), size.max());
            else if (annotation instanceof Length length) addBounds(result, true, length.min(), length.max());
            else if (annotation instanceof Min min) result.add(ConstraintMeta.of("min", min.value()));
            else if (annotation instanceof Max max) result.add(ConstraintMeta.of("max", max.value()));
            else if (annotation instanceof DecimalMin min) result.add(ConstraintMeta.of("min", min.value()));
            else if (annotation instanceof DecimalMax max) result.add(ConstraintMeta.of("max", max.value()));
            else if (annotation instanceof Positive) result.add(ConstraintMeta.of("positive"));
            else if (annotation instanceof PositiveOrZero) result.add(ConstraintMeta.of("nonnegative"));
            else if (annotation instanceof Negative) result.add(ConstraintMeta.of("negative"));
            else if (annotation instanceof NegativeOrZero) result.add(ConstraintMeta.of("nonpositive"));
            else if (annotation instanceof Pattern pattern) result.add(ConstraintMeta.of("pattern", pattern.regexp()));
            else if (annotation instanceof Email) result.add(ConstraintMeta.of("email"));
            else if (annotation instanceof AssertTrue) result.add(ConstraintMeta.of("assertTrue"));
            else if (annotation instanceof AssertFalse) result.add(ConstraintMeta.of("assertFalse"));
            else if (annotation instanceof NotNull) { /* feeds required, not a constraint entry */ }
            else if (annotation.annotationType().isAnnotationPresent(Constraint.class)) {
                result.add(ConstraintMeta.custom(annotation.annotationType().getSimpleName()));
            }
        }
        return result;
    }

    private static void addBounds(List<ConstraintMeta> result, boolean stringLike, int min, int max) {
        String minKind = stringLike ? "minLength" : "minItems";
        String maxKind = stringLike ? "maxLength" : "maxItems";
        if (min > 0) result.add(ConstraintMeta.of(minKind, min));
        if (max != Integer.MAX_VALUE) result.add(ConstraintMeta.of(maxKind, max));
    }
}
