package dev.simplecore.simplix.core.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Specifies a human-readable label for a DTO field.
 *
 * <p>Two modes:
 * <ol>
 *   <li><b>Message key mode</b>: {@code {message.key}} syntax, resolved from message bundles</li>
 *   <li><b>Direct label mode</b>: plain text used as-is</li>
 * </ol>
 *
 * <p>Consumed by validation error enhancement and by the dev meta endpoint
 * (message-key mode becomes {@code labelKey}, direct mode becomes {@code label} in the IR).
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface FieldLabel {

    /** Label text, either a {@code {message.key}} or a direct label. */
    String value();
}
