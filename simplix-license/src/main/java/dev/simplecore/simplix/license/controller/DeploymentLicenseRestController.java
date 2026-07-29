package dev.simplecore.simplix.license.controller;

import dev.simplecore.simplix.license.activation.ActivationFailures;
import dev.simplecore.simplix.license.activation.LicenseActivationService;
import dev.simplecore.simplix.license.controller.dto.LicenseDTOs.ActivateRequestDTO;
import dev.simplecore.simplix.license.controller.dto.LicenseDTOs.ActivationRequestFileDTO;
import dev.simplecore.simplix.license.controller.dto.LicenseDTOs.LicenseStatusDTO;
import dev.simplecore.simplix.license.controller.dto.LicenseDTOs.OfflineResponseDTO;
import dev.simplecore.simplix.license.config.LicenseProperties;
import dev.simplecore.simplix.license.core.LicenseManager;
import dev.simplecore.simplix.license.enforcement.FeatureAccess;
import dev.simplecore.simplix.license.enforcement.LicenseQuotaGuard;
import dev.simplecore.simplix.license.model.LicenseState;
import dev.accesscore.license.sdk.issuing.ProductKeys;
import dev.accesscore.license.sdk.model.LicenseModel.LicensePayload;
import dev.accesscore.license.sdk.model.LicenseModel.RegistrationRecord;
import dev.accesscore.license.sdk.protocol.ActivationModel.PreparedRequest;
import dev.simplecore.simplix.core.exception.ErrorCode;
import dev.simplecore.simplix.core.exception.SimpliXGeneralException;
import dev.simplecore.simplix.core.model.SimpliXApiResponse;
import dev.simplecore.simplix.web.controller.SimpliXStandardApi;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * License registration and reporting surface. Entity-less aggregation surface, hand-authored
 * per the generator-first exception: the license lives in a signed token rather than a table.
 *
 * <p>This surface stays reachable while the license is missing or rejected — the enforcement
 * filter exempts it — so an operator can register a license through the product instead of
 * needing shell access to the server.
 *
 * <p>Both registration paths are here. Online activation posts the product key to the license
 * server. Offline activation hands out a request file the operator carries to the server and
 * accepts the signed answer back, which is how a closed-network site is licensed.
 */
@RestController
// The path says which licence this is, the same way the class and the tag do. A product
// that also sells licences has its own surface over those, and one word doing both jobs
// is what makes a generated client merge two unrelated things into one.
@RequestMapping("/deployment-license")
// Named for what it is — the licence this deployment runs under — rather than for the
// package it lives in. A product that also sells licences has its own surface over those,
// and two tags ending in the same word are read as one entity by the frontend generator.
@Tag(name = "system.DeploymentLicense", description = "This deployment's own licence: registration and status")
@SimpliXStandardApi
public class DeploymentLicenseRestController {

    private final LicenseManager licenseManager;
    private final LicenseActivationService activationService;
    private final FeatureAccess featureAccess;
    private final LicenseQuotaGuard quotaGuard;
    private final ProductKeys productKeys;
    private final String productKeyPrefix;

    /**
     * @param licenseManager the source of the current judgement
     * @param activationService what drives registration
     * @param featureAccess where a feature's availability is judged
     * @param quotaGuard the source of the current counts
     * @param productKeys the key format
     * @param properties where the product family is configured
     */
    public DeploymentLicenseRestController(LicenseManager licenseManager,
                                 LicenseActivationService activationService,
                                 FeatureAccess featureAccess,
                                 LicenseQuotaGuard quotaGuard,
                                 ProductKeys productKeys,
                                 LicenseProperties properties) {
        this.licenseManager = licenseManager;
        this.activationService = activationService;
        this.featureAccess = featureAccess;
        this.quotaGuard = quotaGuard;
        this.productKeys = productKeys;
        this.productKeyPrefix = properties.getProductKeyPrefix();
    }

    /**
     * Current license status and the contract behind it.
     */
    @GetMapping("/status")
    @Operation(summary = "Get License Status",
            description = "Current license verdict with the contract it was judged from, "
                    + "this machine's fingerprint, the fingerprint of the license key this "
                    + "deployment verifies against, and whether online activation is available")
    @PreAuthorize("hasPermission('LICENSE', 'view')")
    public SimpliXApiResponse<LicenseStatusDTO> status() {
        return SimpliXApiResponse.success(buildStatus());
    }

    /**
     * The features this deployment may act on: granted by the license and activated by an
     * administrator. The frontend gates navigation on this list.
     */
    @GetMapping("/features")
    @Operation(summary = "Get Available Features",
            description = "Feature keys the license grants and an administrator has activated")
    @PreAuthorize("isAuthenticated()")
    public SimpliXApiResponse<List<String>> features() {
        return SimpliXApiResponse.success(featureAccess.effectiveFeatures());
    }

    /**
     * Registers a license by claiming a seat over the network.
     */
    @PostMapping("/activate")
    @Operation(summary = "Activate License",
            description = "Claims a seat on the license server with the given product key and "
                    + "registers the machine-bound license it returns")
    @PreAuthorize("hasPermission('LICENSE', 'manage')")
    public SimpliXApiResponse<LicenseStatusDTO> activate(@Validated @RequestBody ActivateRequestDTO dto) {
        ActivationFailures.require(activationService.activateOnline(
                dto.getProductKey(), Boolean.TRUE.equals(dto.getReplaceExisting())));
        return SimpliXApiResponse.success(buildStatus(), "License activated");
    }

    /**
     * The activation request a closed-network deployment carries to the license server.
     */
    @GetMapping("/activation-request")
    @Operation(summary = "Build Offline Activation Request",
            description = "Returns the activation request an operator submits to the license "
                    + "server when this deployment cannot reach it over the network")
    @PreAuthorize("hasPermission('LICENSE', 'manage')")
    public SimpliXApiResponse<ActivationRequestFileDTO> activationRequest(
            @RequestParam String productKey) {
        // Checked here so a mistyped key is named as such: past this point the only reason a
        // request cannot be built is a missing machine fingerprint.
        if (productKeys.canonical(productKeyPrefix, productKey) == null) {
            throw new SimpliXGeneralException(ErrorCode.GEN_BAD_REQUEST,
                    "{error.license.malformedProductKey}", null);
        }

        PreparedRequest request = activationService.buildOfflineRequest(productKey)
                .orElseThrow(() -> new SimpliXGeneralException(ErrorCode.GEN_CONFLICT,
                        "{error.license.fingerprintUnavailable}", null));

        return SimpliXApiResponse.success(ActivationRequestFileDTO.builder()
                .body(request.body())
                .nonce(request.nonce())
                .requestedAt(Instant.now())
                .build());
    }

    /**
     * Registers a license from the signed answer to an offline activation request.
     */
    @PostMapping("/activation-response")
    @Operation(summary = "Apply Offline Activation Response",
            description = "Registers the signed license issued for an offline activation "
                    + "request, after verifying its signature and machine binding")
    @PreAuthorize("hasPermission('LICENSE', 'manage')")
    public SimpliXApiResponse<LicenseStatusDTO> activationResponse(
            @Validated @RequestBody OfflineResponseDTO dto) {
        ActivationFailures.require(activationService.applyOfflineResponse(
                dto.getProductKey(), dto.getLicenseToken(),
                Boolean.TRUE.equals(dto.getReplaceExisting())));
        return SimpliXApiResponse.success(buildStatus(), "License registered");
    }

    /**
     * Refreshes the license token immediately instead of waiting for the schedule.
     */
    @PostMapping("/heartbeat")
    @Operation(summary = "Refresh License",
            description = "Contacts the license server for a freshly signed token, which is how "
                    + "revocation, expiry, and contract changes reach this deployment")
    @PreAuthorize("hasPermission('LICENSE', 'manage')")
    public SimpliXApiResponse<LicenseStatusDTO> heartbeat() {
        ActivationFailures.require(activationService.heartbeat());
        return SimpliXApiResponse.success(buildStatus(), "License refreshed");
    }

    /**
     * Releases the seat and discards the local registration.
     */
    @PostMapping("/deactivate")
    @Operation(summary = "Deactivate License",
            description = "Releases the seat on the license server and discards the local "
                    + "registration so the license can be moved to another machine")
    @PreAuthorize("hasPermission('LICENSE', 'manage')")
    public SimpliXApiResponse<LicenseStatusDTO> deactivate() {
        ActivationFailures.require(activationService.deactivate());
        return SimpliXApiResponse.success(buildStatus(), "License released");
    }

    /**
     * @return the current status, with contract details when a license is registered
     */
    private LicenseStatusDTO buildStatus() {
        LicenseState.Snapshot snapshot = licenseManager.getState().snapshot();
        List<String> fingerprints = licenseManager.machineFingerprints();
        Optional<RegistrationRecord> record = licenseManager.currentRecord();

        LicenseStatusDTO.LicenseStatusDTOBuilder builder = LicenseStatusDTO.builder()
                .status(snapshot.status().name())
                .lastChecked(snapshot.lastChecked())
                .usable(licenseManager.getState().isUsable())
                .onlineActivationAvailable(activationService.isOnlineActivationAvailable())
                .machineFingerprintAvailable(!fingerprints.isEmpty())
                .machineFingerprints(fingerprints)
                .publicKeyFingerprint(licenseManager.getPublicKeyFingerprint())
                .maskedProductKey(record.map(RegistrationRecord::productKey)
                        .map(key -> productKeys.masked(productKeyPrefix, key))
                        .orElse(null))
                .lastHeartbeatAt(record.map(RegistrationRecord::lastHeartbeatAt).orElse(null));

        LicensePayload payload = snapshot.payload();
        if (payload == null) {
            return builder.build();
        }

        return builder
                .licenseId(payload.licenseId())
                .activationId(payload.activationId())
                .customer(payload.customer())
                .channel(payload.effectiveChannel())
                .productCode(payload.productCode())
                .maxRelease(payload.maxRelease())
                .tierLabel(payload.tierLabel())
                .issuedAt(payload.issuedAt())
                .activatedAt(payload.activatedAt())
                .expiresAt(payload.expiresAt())
                .graceExpiresAt(payload.graceExpiresAt())
                .gracePeriodDays(payload.gracePeriodDays())
                .limits(payload.limits())
                .usage(quotaGuard.currentUsage())
                .features(payload.features())
                .heartbeatRequired(payload.heartbeatRequired())
                .build();
    }
}
