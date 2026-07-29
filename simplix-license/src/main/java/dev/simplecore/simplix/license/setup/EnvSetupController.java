package dev.simplecore.simplix.license.setup;

import dev.simplecore.simplix.license.setup.dto.EnvProfileDTOs.EncryptionKeyResult;
import dev.simplecore.simplix.license.setup.dto.EnvProfileDTOs.ProfileDetail;
import dev.simplecore.simplix.license.setup.dto.EnvProfileDTOs.ProfileSummary;
import dev.simplecore.simplix.license.setup.dto.EnvProfileDTOs.ProfileUpdate;
import dev.simplecore.simplix.license.setup.EnvProfileService;
import dev.simplecore.simplix.core.exception.ErrorCode;
import dev.simplecore.simplix.core.exception.SimpliXGeneralException;
import dev.simplecore.simplix.core.model.SimpliXApiResponse;
import dev.simplecore.simplix.web.controller.SimpliXStandardApi;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Installer server-configuration step: lists and edits the deployment env
 * profiles and mints an encryption key. Entity-less aggregation surface,
 * hand-authored per the generator-first exception.
 *
 * <p>Every operation is restricted to the pre-initialization state — once the app
 * is initialized these endpoints return conflict, so secrets are never exposed or
 * rewritten through the API after setup. Saved edits take effect on the next
 * restart, not the running JVM.
 */
@RestController
@RequestMapping("/system/setup/env")
@Tag(name = "system.Setup", description = "First-run setup wizard")
@SimpliXStandardApi
public class EnvSetupController {

    private final EnvProfileService envProfileService;
    private final SetupState setupState;

    public EnvSetupController(EnvProfileService envProfileService,
                              SetupState setupState) {
        this.envProfileService = envProfileService;
        this.setupState = setupState;
    }

    /**
     * Lists the selectable env profiles, marking the active one.
     *
     * @return the profile summaries
     */
    @GetMapping("/profiles")
    @Operation(summary = "List env profiles",
            description = "Lists the deployment env profiles available to edit before setup, marking the active one")
    @PreAuthorize("permitAll()")
    public SimpliXApiResponse<List<ProfileSummary>> profiles() {
        requireUninitialized();
        return SimpliXApiResponse.success(envProfileService.listProfiles());
    }

    /**
     * Reads one profile's grouped settings with secrets masked.
     *
     * @param name the profile name
     * @return the grouped, masked settings
     */
    @GetMapping("/profiles/{name}")
    @Operation(summary = "Read env profile",
            description = "Reads a profile's settings grouped into sections; secret values are masked")
    @PreAuthorize("permitAll()")
    public SimpliXApiResponse<ProfileDetail> profile(@PathVariable String name) {
        requireUninitialized();
        return SimpliXApiResponse.success(envProfileService.getProfile(name));
    }

    /**
     * Saves edits to a profile, preserving comments; secrets apply only when a new
     * value is supplied.
     *
     * @param name the profile name
     * @param update the key to new-value edits
     * @return an empty success response
     */
    @PutMapping("/profiles/{name}")
    @Operation(summary = "Save env profile",
            description = "Writes edits back to the profile; secret values are applied only when a new value is supplied")
    @PreAuthorize("permitAll()")
    public SimpliXApiResponse<Void> save(@PathVariable String name, @RequestBody ProfileUpdate update) {
        requireUninitialized();
        envProfileService.saveProfile(name, update.getSettings());
        return SimpliXApiResponse.success(null, "Env profile saved");
    }

    /**
     * Generates a fresh AES-256 encryption key for the installer to place in a
     * profile. Allowed only before initialization so an existing dataset's key is
     * never rotated out from under its ciphertext.
     *
     * @return the generated key
     */
    @PostMapping("/encryption-key")
    @Operation(summary = "Generate encryption key",
            description = "Generates a fresh AES-256 base64 encryption key; allowed only before initialization")
    @PreAuthorize("permitAll()")
    public SimpliXApiResponse<EncryptionKeyResult> generateEncryptionKey() {
        requireUninitialized();
        return SimpliXApiResponse.success(envProfileService.generateEncryptionKey());
    }

    /**
     * Asserts the app has not completed first-run setup, so the installer surface
     * stays closed once real data (and its encryption key) exist.
     *
     * @throws SimpliXGeneralException GEN_CONFLICT when the app is already initialized
     */
    private void requireUninitialized() {
        if (setupState.isInitialized()) {
            throw new SimpliXGeneralException(ErrorCode.GEN_CONFLICT, "{error.system.alreadyInitialized}", null);
        }
    }
}
