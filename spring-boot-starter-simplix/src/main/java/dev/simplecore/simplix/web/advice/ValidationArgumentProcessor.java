package dev.simplecore.simplix.web.advice;

import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.FieldError;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Processes validation arguments from FieldError to ensure proper parameter order
 * for message formatting. This class provides a safe and extensible way to handle
 * various validation annotations without hardcoding specific behavior.
 */
@Slf4j
public class ValidationArgumentProcessor {
    
    /**
     * Registry of argument processors for specific validation scenarios
     */
    private static final Map<String, Function<Object[], Object[]>> ARGUMENT_PROCESSORS = new HashMap<>();
    
    static {
        // Register default processors
        registerDefaultProcessors();
    }
    
    /**
     * Process validation arguments from FieldError
     *
     * @param error The field error containing validation information
     * @return Processed arguments in correct order for message formatting
     */
    public static Object[] processArguments(FieldError error) {
        if (error.getArguments() == null) {
            return new Object[0];
        }

        // Extract constraint type from error codes
        String constraintType = extractConstraintType(error.getCodes());

        // Extract non-resolvable arguments (skip field names, extract from maps)
        List<Object> arguments = extractValidationArguments(error.getArguments());

        log.trace("Processing validation arguments - constraint: {}, extracted args: {}",
                constraintType, arguments);

        // If no arguments extracted, return empty array
        if (arguments.isEmpty()) {
            return new Object[0];
        }

        // Try to process with registered processors
        String messageTemplate = error.getDefaultMessage();
        if (messageTemplate != null && messageTemplate.startsWith("{") && messageTemplate.endsWith("}")) {
            String messageKey = messageTemplate.substring(1, messageTemplate.length() - 1);

            // Check constraint-specific processors first
            if (constraintType != null) {
                String constraintLower = constraintType.toLowerCase();
                if (ARGUMENT_PROCESSORS.containsKey(constraintLower)) {
                    return ARGUMENT_PROCESSORS.get(constraintLower).apply(arguments.toArray());
                }
            }

            // Then check message key patterns
            for (Map.Entry<String, Function<Object[], Object[]>> entry : ARGUMENT_PROCESSORS.entrySet()) {
                if (messageKey.contains(entry.getKey())) {
                    return entry.getValue().apply(arguments.toArray());
                }
            }
        }

        // Fallback: return arguments as-is
        return arguments.toArray();
    }
    
    /**
     * Extract constraint attributes as a map for named placeholder substitution
     *
     * @param error The field error containing validation information
     * @return Map of constraint attributes (e.g., {min=2, max=100})
     */
    public static Map<String, Object> extractConstraintAttributes(FieldError error) {
        Map<String, Object> attributeMap = new HashMap<>();

        if (error.getArguments() == null) {
            return attributeMap;
        }

        for (int i = 0; i < error.getArguments().length; i++) {
            Object arg = error.getArguments()[i];

            // Skip field name resolvables
            if (arg instanceof org.springframework.context.support.DefaultMessageSourceResolvable) {
                continue;
            }

            // Extract from Map (constraint attributes)
            if (arg instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> argMap = (Map<String, Object>) arg;
                attributeMap.putAll(argMap);
            }
        }

        log.trace("Extracted constraint attributes: {}", attributeMap);
        return attributeMap;
    }

    /**
     * Extract validation arguments, filtering out DefaultMessageSourceResolvable objects
     * and extracting values from constraint attribute maps
     */
    private static List<Object> extractValidationArguments(Object[] rawArguments) {
        List<Object> arguments = new ArrayList<>();

        for (int i = 0; i < rawArguments.length; i++) {
            Object arg = rawArguments[i];

            // Skip field name resolvables
            if (arg instanceof org.springframework.context.support.DefaultMessageSourceResolvable) {
                continue;
            }

            // Try to extract values from Map (constraint attributes)
            if (arg instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> attributeMap = (Map<String, Object>) arg;

                // Track whether we extracted any values from this map
                boolean extractedFromThisMap = false;

                // Extract common constraint attributes
                // Note: Hibernate Validator typically provides attributes in alphabetical order
                // For @Length(min=2, max=100), the order is: max, min
                if (attributeMap.containsKey("max")) {
                    arguments.add(attributeMap.get("max"));
                    extractedFromThisMap = true;
                }
                if (attributeMap.containsKey("min")) {
                    arguments.add(attributeMap.get("min"));
                    extractedFromThisMap = true;
                }
                if (attributeMap.containsKey("value") && !attributeMap.containsKey("max") && !attributeMap.containsKey("min")) {
                    arguments.add(attributeMap.get("value"));
                    extractedFromThisMap = true;
                }

                // If we extracted values from this map, continue to next argument
                if (extractedFromThisMap) {
                    continue;
                }
            }

            // Add non-map arguments directly (primitive values, strings, etc.)
            arguments.add(arg);
        }

        return arguments;
    }
    
    /**
     * Generic processor for numeric arguments - ensures min comes before max
     */
    private static Object[] processNumericArguments(Object[] arguments) {
        if (arguments.length == 2) {
            Object arg1 = arguments[0];
            Object arg2 = arguments[1];

            if (arg1 instanceof Number && arg2 instanceof Number) {
                Number num1 = (Number) arg1;
                Number num2 = (Number) arg2;

                // Ensure min comes before max
                if (num1.longValue() > num2.longValue()) {
                    return new Object[]{arg2, arg1}; // swap to min, max order
                }
            }
        }

        return arguments;
    }

    /**
     * Extract constraint type from error codes
     * For example: ["Length.user.name", "Length.name", "Length.java.lang.String", "Length"]
     * Returns: "Length"
     */
    private static String extractConstraintType(String[] codes) {
        if (codes == null || codes.length == 0) {
            return null;
        }

        // The last code is typically the constraint name
        String lastCode = codes[codes.length - 1];

        // Remove any package prefix
        int lastDot = lastCode.lastIndexOf('.');
        if (lastDot > 0) {
            return lastCode.substring(lastDot + 1);
        }

        return lastCode;
    }
    
    /**
     * Register default argument processors for common validation scenarios
     */
    private static void registerDefaultProcessors() {
        // Length constraint: extracted as [max, min] from constraint attributes
        // For "length" key in messages (e.g., "validation.length"), return both in min-max order
        ARGUMENT_PROCESSORS.put("length", args -> {
            if (args.length == 2) {
                return processNumericArguments(args); // Reorder to min, max if needed
            }
            return args;
        });

        // Size constraint (similar to Length)
        ARGUMENT_PROCESSORS.put("size", args -> {
            if (args.length == 2) {
                return processNumericArguments(args);
            }
            return args;
        });

        // Max-only validations - use first argument (max value)
        // For @Length(min=2, max=100), args = [100, 2], so args[0] = 100
        ARGUMENT_PROCESSORS.put("max", args -> args.length > 0 ? new Object[]{args[0]} : new Object[0]);

        // Min-only validations - use second argument if available, otherwise first
        // For @Length(min=2, max=100), args = [100, 2], so args[1] = 2
        ARGUMENT_PROCESSORS.put("min", args -> {
            if (args.length > 1) {
                return new Object[]{args[1]};
            } else if (args.length > 0) {
                return new Object[]{args[0]};
            }
            return new Object[0];
        });

        // Range validations
        ARGUMENT_PROCESSORS.put("range", ValidationArgumentProcessor::processNumericArguments);
    }
    
    /**
     * Register a custom argument processor for specific message keys
     * 
     * @param keyPattern The pattern to match in message keys
     * @param processor The function to process arguments
     */
    public static void registerProcessor(String keyPattern, Function<Object[], Object[]> processor) {
        ARGUMENT_PROCESSORS.put(keyPattern, processor);
    }
    
    /**
     * Remove a registered processor
     * 
     * @param keyPattern The pattern to remove
     */
    public static void removeProcessor(String keyPattern) {
        ARGUMENT_PROCESSORS.remove(keyPattern);
    }
    
    /**
     * Get all registered processors (for debugging/testing)
     */
    public static Map<String, Function<Object[], Object[]>> getRegisteredProcessors() {
        return new HashMap<>(ARGUMENT_PROCESSORS);
    }
} 