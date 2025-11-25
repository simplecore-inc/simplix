package dev.simplecore.simplix.core.convert.bool;

/**
 * Standard implementation of BooleanConverter
 */
public class StandardBooleanConverter implements BooleanConverter {
    
    @Override
    public Boolean fromString(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        
        String normalizedValue = value.toLowerCase().trim();
		return switch (normalizedValue) {
			case "true", "1", "yes", "y", "on" -> true;
			case "false", "0", "no", "n", "off" -> false;
			default -> throw new IllegalArgumentException(
				"Invalid boolean value: '" + value + "'. " +
					"Allowed values are: true/false, 1/0, yes/no, y/n, on/off");
		};
    }

    @Override
    public String toString(Boolean value) {
        if (value == null) {
            return null;
        }
        return value ? "true" : "false";
    }
} 