package dev.simplecore.simplix.core.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The tone of one enum constant, placed on the constant itself:
 * {@code @Tone(ToneKind.DANGER) SUSPENDED}. SimpliX Meta publishes it beside the constant's
 * label key, so a status badge reads its colour from the contract. A constant without it has no
 * tone and the client falls back to neutral.
 */
@Documented
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Tone {

    ToneKind value();
}
