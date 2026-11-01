package dev.simplecore.simplix.web.meta.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Map;

/**
 * One serialized property of a DTO. {@code name} is the wire name after Jackson.
 * {@code reference} is present when the field holds the id (or ids) of another entity;
 * {@code tones} maps {@code "true"} and {@code "false"} to a tone for a flag with {@code @BooleanTone}.
 * {@code format} names how a screen draws the value beyond its type: {@code "code"} for a field
 * with {@code @CodeValue}, {@code "money"} for one with {@code @MoneyValue}, whose currency and
 * scale {@code money} carries. {@code labels} is the text {@code labelKey} resolves to per language
 * code, so a client that has no access to the server's bundles labels the field in every language.
 * {@code defaultValue} is the value a new record starts the field with: a boolean, a number, a
 * string, or an enum constant by name.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record FieldMeta(
        String name,
        TypeRef type,
        boolean required,
        boolean nullable,
        String description,
        String labelKey,
        String label,
        List<ConstraintMeta> constraints,
        SearchableMeta searchable,
        ReferenceMeta reference,
        Map<String, String> tones,
        String format,
        Map<String, String> labels,
        Object defaultValue,
        MoneyMeta money) {

    /** The value of {@link #format()} for a field with {@code @CodeValue}. */
    public static final String FORMAT_CODE = "code";

    /** The value of {@link #format()} for a field with {@code @MoneyValue}. */
    public static final String FORMAT_MONEY = "money";

    public FieldMeta(String name, TypeRef type, boolean required, boolean nullable,
                     String description, String labelKey, String label,
                     List<ConstraintMeta> constraints, SearchableMeta searchable) {
        this(name, type, required, nullable, description, labelKey, label, constraints, searchable, null, null, null, null, null, null);
    }

    public FieldMeta withReference(ReferenceMeta value) {
        return new FieldMeta(name, type, required, nullable, description, labelKey, label,
                constraints, searchable, value, tones, format, labels, defaultValue, money);
    }

    public FieldMeta withTones(Map<String, String> value) {
        return new FieldMeta(name, type, required, nullable, description, labelKey, label,
                constraints, searchable, reference, value, format, labels, defaultValue, money);
    }

    public FieldMeta withSearchable(SearchableMeta value) {
        return new FieldMeta(name, type, required, nullable, description, labelKey, label,
                constraints, value, reference, tones, format, labels, defaultValue, money);
    }

    public FieldMeta withFormat(String value) {
        return new FieldMeta(name, type, required, nullable, description, labelKey, label,
                constraints, searchable, reference, tones, value, labels, defaultValue, money);
    }

    public FieldMeta withLabels(Map<String, String> value) {
        return new FieldMeta(name, type, required, nullable, description, labelKey, label,
                constraints, searchable, reference, tones, format, value, defaultValue, money);
    }

    public FieldMeta withDefaultValue(Object value) {
        return new FieldMeta(name, type, required, nullable, description, labelKey, label,
                constraints, searchable, reference, tones, format, labels, value, money);
    }

    public FieldMeta withMoney(MoneyMeta value) {
        return new FieldMeta(name, type, required, nullable, description, labelKey, label,
                constraints, searchable, reference, tones, FORMAT_MONEY, labels, defaultValue, value);
    }
}
