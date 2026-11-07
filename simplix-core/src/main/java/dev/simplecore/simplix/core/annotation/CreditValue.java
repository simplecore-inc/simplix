package dev.simplecore.simplix.core.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The field holds an amount of credits, the whole units a product sells and spends, which a screen
 * draws grouped and followed by its unit. SimpliX Meta publishes it as {@code format: "credits"},
 * or as {@code format: "signedCredits"} when {@link #signed()} is set: a ledger amount whose sign
 * says which way it moved, drawn as a credit when positive and a debit when negative. Placed on the
 * entity field, it reaches every DTO field mapped to that entity field; placed on a DTO field, it
 * covers a derived value with no column of its own.
 */
@Documented
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface CreditValue {

    /** @return whether the sign carries meaning: positive is a credit, negative a debit */
    boolean signed() default false;
}
