package dev.simplecore.simplix.web.meta.model;

/**
 * How a field marked {@code @MoneyValue} stores its amount.
 *
 * @param currency the ISO 4217 currency code
 * @param scale the decimal places the stored whole number carries; the amount is the value
 *        divided by ten to this power
 */
public record MoneyMeta(String currency, int scale) {
}
