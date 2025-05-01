package dev.simplecore.simplix.core.convert.enumeration;

import java.util.Map;

/**
 * Interface for converting between Enum objects and strings or Maps
 */
public interface EnumConverter {
    /**
     * Converts string value to enum constant.
     *
     * @param value String value to convert (enum constant name)
     * @param enumType Enum class
     * @param <T> Enum type
     * @return Converted enum constant
     */
    <T extends Enum<?>> T fromString(String value, Class<T> enumType);

    /**
     * Converts enum constant to string.
     *
     * @param value Enum constant to convert
     * @return Converted string (enum constant name)
     */
    String toString(Enum<?> value);

    /**
     * Converts enum constant to Map.
     * The Map includes all fields of the enum.
     *
     * @param value Enum constant to convert
     * @return Map containing the enum constant's fields
     */
    Map<String, Object> toMap(Enum<?> value);

    /**
     * Returns the default EnumConverter instance.
     *
     * @return Default EnumConverter implementation
     */
    static EnumConverter getDefault() {
        return new StandardEnumConverter();
    }
} 