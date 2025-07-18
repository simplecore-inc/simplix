package dev.simplecore.simplix.web.advice;

import org.springframework.validation.FieldError;

import java.util.*;
import java.util.function.Function;

/**
 * Processes validation arguments from FieldError to ensure proper parameter order
 * for message formatting. This class provides a safe and extensible way to handle
 * various validation annotations without hardcoding specific behavior.
 */
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
        
        // Extract non-resolvable arguments (skip field names)
        List<Object> arguments = extractValidationArguments(error.getArguments());
        
        // Try to process with registered processors first
        String messageTemplate = error.getDefaultMessage();
        if (messageTemplate != null && messageTemplate.startsWith("{") && messageTemplate.endsWith("}")) {
            String messageKey = messageTemplate.substring(1, messageTemplate.length() - 1);
            
            for (Map.Entry<String, Function<Object[], Object[]>> entry : ARGUMENT_PROCESSORS.entrySet()) {
                if (messageKey.contains(entry.getKey())) {
                    return entry.getValue().apply(arguments.toArray());
                }
            }
        }
        
        // Fallback: Generic numeric argument reordering
        return processNumericArguments(arguments.toArray());
    }
    
    /**
     * Extract validation arguments, filtering out DefaultMessageSourceResolvable objects
     */
    private static List<Object> extractValidationArguments(Object[] rawArguments) {
        List<Object> arguments = new ArrayList<>();
        for (Object arg : rawArguments) {
            if (!(arg instanceof org.springframework.context.support.DefaultMessageSourceResolvable)) {
                arguments.add(arg);
            }
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
     * Register default argument processors for common validation scenarios
     */
    private static void registerDefaultProcessors() {
        // Range validations (Size, Length, Range, etc.)
        ARGUMENT_PROCESSORS.put("range", ValidationArgumentProcessor::processNumericArguments);
        ARGUMENT_PROCESSORS.put("size", ValidationArgumentProcessor::processNumericArguments);
        ARGUMENT_PROCESSORS.put("length", ValidationArgumentProcessor::processNumericArguments);
        
        // Min/Max validations - use first argument only
        ARGUMENT_PROCESSORS.put("min", args -> args.length > 0 ? new Object[]{args[0]} : args);
        ARGUMENT_PROCESSORS.put("max", args -> args.length > 0 ? new Object[]{args[args.length - 1]} : args);
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