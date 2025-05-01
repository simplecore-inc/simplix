package dev.simplecore.simplix.core.convert.bool;

/**
 * Interface for converting between boolean values and strings
 */
public interface BooleanConverter {
    /**
     * Converts string to boolean value.
     *
     * @param value String value to convert
     * @return Converted boolean value
     * @throws IllegalArgumentException if the string is not a valid boolean representation
     */
    Boolean fromString(String value);

    /**
     * Converts boolean value to string.
     *
     * @param value Boolean value to convert
     * @return Converted string representation
     */
    String toString(Boolean value);

    /**
     * Returns the default BooleanConverter instance.
     *
     * @return Default BooleanConverter implementation
     */
    static BooleanConverter getDefault() {
        return new StandardBooleanConverter();
    }
} 