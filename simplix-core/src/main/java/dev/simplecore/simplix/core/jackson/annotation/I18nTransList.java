package dev.simplecore.simplix.core.jackson.annotation;

import com.fasterxml.jackson.annotation.JacksonAnnotationsInside;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import dev.simplecore.simplix.core.jackson.SimpliXI18nTransSerializer;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Container annotation for repeatable {@link I18nTrans} annotations.
 * <p>
 * This annotation is used internally by Java's {@code @Repeatable} mechanism.
 * When multiple {@code @I18nTrans} annotations are applied to the same field,
 * the compiler automatically wraps them in this container annotation.
 * <p>
 * Example usage:
 * <pre>{@code
 * public class ProductDto {
 *     @I18nTrans(source = "nameI18n", target = "name")
 *     @I18nTrans(source = "descriptionI18n", target = "description")
 *     private ProductTranslations translations;
 * }
 * }</pre>
 *
 * @see I18nTrans
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@JacksonAnnotationsInside
@JsonSerialize(using = SimpliXI18nTransSerializer.class)
public @interface I18nTransList {

    /**
     * Array of {@link I18nTrans} annotations.
     *
     * @return array of I18nTrans annotations
     */
    I18nTrans[] value();
}
