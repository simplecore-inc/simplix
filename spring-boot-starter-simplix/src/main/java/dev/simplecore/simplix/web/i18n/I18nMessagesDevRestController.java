package dev.simplecore.simplix.web.i18n;

import dev.simplecore.simplix.core.model.SimpliXApiResponse;
import dev.simplecore.simplix.web.controller.SimpliXStandardApi;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Development API for i18n messages export
 *
 * This controller provides endpoints to export all entity and enum messages
 * for frontend TypeScript generation. These endpoints are intended for
 * development use only and should be disabled in production.
 *
 * Usage:
 * 1. Frontend calls GET /dev/i18n/messages
 * 2. Receives JSON with all entity/enum labels in all supported locales
 * 3. Converts JSON to TypeScript type definitions
 * 4. Uses type-safe i18n labels in frontend code
 */
@RestController
@RequestMapping("/dev/i18n")
@Tag(name = "dev.i18n", description = "Development API for exporting i18n messages (entities and enums)")
@SimpliXStandardApi
public class I18nMessagesDevRestController {

    private final I18nMessagesService i18nMessagesService;

    public I18nMessagesDevRestController(I18nMessagesService i18nMessagesService) {
        this.i18nMessagesService = i18nMessagesService;
    }

    /**
     * Get complete i18n messages (entities + enums + locales)
     *
     * Returns all entity field labels and enum value labels in all supported locales.
     * This is the primary endpoint for frontend i18n message generation.
     *
     * @return Complete I18nMessagesResponse with version, locales, entities, and enums
     */
    @GetMapping("/messages")
    @Operation(
        summary = "Get all i18n messages",
        description = "Returns complete i18n messages including entities, enums, and supported locales. " +
                      "Use this for frontend TypeScript generation."
    )
    @PreAuthorize("permitAll()")
    public SimpliXApiResponse<I18nMessagesResponse> getAllMessages() {
        I18nMessagesResponse response = i18nMessagesService.getAllMessages();
        return SimpliXApiResponse.success(response);
    }

    /**
     * Get entity messages only
     *
     * Returns entity field labels in all supported locales.
     *
     * @return Map of entity name to EntityMessages
     */
    @GetMapping("/entities")
    @Operation(
        summary = "Get entity messages only",
        description = "Returns only entity field labels in all supported locales"
    )
    @PreAuthorize("permitAll()")
    public SimpliXApiResponse<Map<String, EntityMessages>> getEntityMessages() {
        Map<String, EntityMessages> entityMessages = i18nMessagesService.getEntityMessages();
        return SimpliXApiResponse.success(entityMessages);
    }

    /**
     * Get enum messages only
     *
     * Returns enum value labels in all supported locales.
     *
     * @return Map of enum name to EnumMessages
     */
    @GetMapping("/enums")
    @Operation(
        summary = "Get enum messages only",
        description = "Returns only enum value labels in all supported locales"
    )
    @PreAuthorize("permitAll()")
    public SimpliXApiResponse<Map<String, EnumMessages>> getEnumMessages() {
        Map<String, EnumMessages> enumMessages = i18nMessagesService.getEnumMessages();
        return SimpliXApiResponse.success(enumMessages);
    }

    /**
     * Get supported locales only
     *
     * Returns list of locale codes that are auto-detected from properties files.
     *
     * @return List of locale codes (e.g., ["en", "ko", "ja"])
     */
    @GetMapping("/locales")
    @Operation(
        summary = "Get supported locales",
        description = "Returns list of supported locale codes (auto-detected from properties files)"
    )
    @PreAuthorize("permitAll()")
    public SimpliXApiResponse<List<String>> getSupportedLocales() {
        List<String> locales = i18nMessagesService.getSupportedLocales();
        return SimpliXApiResponse.success(locales);
    }
}
