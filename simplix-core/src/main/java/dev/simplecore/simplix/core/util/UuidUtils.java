package dev.simplecore.simplix.core.util;

import com.github.f4b6a3.uuid.UuidCreator;

import java.util.UUID;

/**
 * Utility class for generating UUID Version 7 using UUID Creator library
 * 
 * UUID Version 7 is a time-ordered UUID that combines:
 * - 48-bit timestamp (milliseconds since Unix epoch)
 * - 16-bit sequence number
 * - 48-bit random data
 * 
 * This provides better performance and sortability compared to UUID v4
 * 
 * Uses UUID Creator library (com.github.f4b6a3:uuid-creator) for optimal performance
 */
public class UuidUtils {
    
    /**
     * Generate a UUID Version 7
     * 
     * @return UUID Version 7 string
     */
    public static String generateUuidV7() {
        return UuidCreator.getTimeOrderedEpoch().toString();
    }
    
    /**
     * Generate a UUID Version 7 object
     * 
     * @return UUID Version 7 object
     */
    public static UUID generateUuidV7Object() {
        return UuidCreator.getTimeOrderedEpoch();
    }
    
    /**
     * Generate a short UUID (first 8 characters)
     * 
     * @return Short UUID string (8 characters)
     */
    public static String generateShortUuid() {
        return generateUuidV7().substring(0, 8);
    }
    
    /**
     * Extract timestamp from UUID Version 7
     * 
     * @param uuid UUID Version 7
     * @return Timestamp in milliseconds since Unix epoch
     */
    public static long extractTimestamp(UUID uuid) {
        // UUID Creator doesn't have a direct extractTimestamp method
        // We'll implement our own extraction logic for UUID Version 7
        if (!isUuidV7(uuid)) {
            throw new IllegalArgumentException("UUID is not Version 7");
        }
        return (uuid.getMostSignificantBits() >> 16) & 0xFFFFFFFFFFFFL;
    }
    
    /**
     * Extract timestamp from UUID Version 7 string
     * 
     * @param uuidString UUID Version 7 string
     * @return Timestamp in milliseconds since Unix epoch
     */
    public static long extractTimestamp(String uuidString) {
        return extractTimestamp(UUID.fromString(uuidString));
    }
    
    /**
     * Check if UUID is Version 7
     * 
     * @param uuid UUID to check
     * @return true if UUID is Version 7
     */
    public static boolean isUuidV7(UUID uuid) {
        // Check if it's a time-ordered UUID (Version 7)
        return (uuid.getLeastSignificantBits() & 0xF000L) == 0x7000L;
    }
    
    /**
     * Check if UUID string is Version 7
     * 
     * @param uuidString UUID string to check
     * @return true if UUID is Version 7
     */
    public static boolean isUuidV7(String uuidString) {
        try {
            return isUuidV7(UUID.fromString(uuidString));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
    
    /**
     * Generate UUID Version 4 (random) for backward compatibility
     * 
     * @return UUID Version 4 string
     */
    public static String generateUuidV4() {
        return UuidCreator.getRandomBased().toString();
    }
    
    /**
     * Generate UUID Version 4 object for backward compatibility
     * 
     * @return UUID Version 4 object
     */
    public static UUID generateUuidV4Object() {
        return UuidCreator.getRandomBased();
    }
}
