package dev.simplecore.simplix.core.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The field holds an amount of money as a whole number of the currency's smallest stored unit, so
 * the amount is the value divided by ten to the power of {@link #scale()}. A screen draws it as
 * currency and edits it in the currency's own units. SimpliX Meta publishes it as
 * {@code format: "money"} with {@code money: {currency, scale}}. Placed on the entity field, it
 * reaches every DTO field mapped to that entity field; placed on a DTO field, it covers a derived
 * value with no column of its own.
 */
@Documented
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface MoneyValue {

    /** @return the ISO 4217 currency code of the amount */
    String currency();

    /** @return how many decimal places the stored whole number carries: 2 for cents, 6 for millionths */
    int scale() default 0;
}
