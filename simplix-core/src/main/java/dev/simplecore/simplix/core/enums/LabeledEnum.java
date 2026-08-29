package dev.simplecore.simplix.core.enums;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import dev.simplecore.simplix.core.i18n.EnumMessageResolver;
import org.springframework.context.i18n.LocaleContextHolder;

import java.util.Locale;

/**
 * Labeled enum with the {@code {"value":"...","label":"..."}} wire contract.
 *
 * <p>Serializes as {@code {"value": name(), "label": localized}} and deserializes from either
 * the bare value string or the full object. The label resolves from
 * {@code classpath*:messages/enums/**} bundles via {@link EnumMessageResolver} using the key
 * {@code enums.<SimpleClassName>.<NAME>}.
 *
 * <p><b>Caveat:</b> the key uses {@code getClass().getSimpleName()}, so an enum constant
 * declared with a class body ({@code ACTIVE { ... }}) resolves against its anonymous subclass
 * name rather than the enum's own, and its label will not be found. Declare labeled enums
 * without constant bodies.
 */
@JsonFormat(shape = JsonFormat.Shape.OBJECT)
@JsonDeserialize(using = LabeledEnumDeserializer.class)
public interface LabeledEnum extends SimpliXLabeledEnum {

    @JsonProperty("value")
    String name();

    /**
     * Get localized label using current locale from Spring context
     * @return Localized label
     */
    default String getLabel() {
        Locale locale = LocaleContextHolder.getLocale();
        return getLabel(locale);
    }

    /**
     * Get localized label for specific locale
     * @param locale Target locale
     * @return Localized label
     */
    default String getLabel(Locale locale) {
        // Generate key from the runtime class's simple name and the enum constant's name
        // (see class-level Caveat: this is the constant's anonymous subclass name for
        // a constant declared with a class body, not the enum's own simple name)
        String className = this.getClass().getSimpleName();

        // Build key with enums prefix, original class name and enum value name
        String key = String.format("enums.%s.%s",
            className,
            this.name()
        );
        return EnumMessageResolver.resolveMessage(key, locale);
    }
}
