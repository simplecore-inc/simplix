package dev.simplecore.simplix.license.setup;

import dev.simplecore.simplix.license.activation.ActivationFailures;
import dev.simplecore.simplix.license.config.LicenseProperties;
import dev.simplecore.simplix.license.activation.ActivationServerProbe;
import dev.accesscore.license.sdk.protocol.ActivationModel.PreparedRequest;
import dev.simplecore.simplix.license.activation.LicenseActivationService;
import dev.simplecore.simplix.license.controller.dto.LicenseDTOs.ActivateRequestDTO;
import dev.simplecore.simplix.license.controller.dto.LicenseDTOs.ActivationRequestFileDTO;
import dev.simplecore.simplix.license.controller.dto.LicenseDTOs.LicenseServerRequestDTO;
import dev.simplecore.simplix.license.controller.dto.LicenseDTOs.OfflineResponseDTO;
import dev.simplecore.simplix.license.core.LicenseManager;
import dev.accesscore.license.sdk.model.LicenseModel.LicensePayload;
import dev.simplecore.simplix.license.model.LicenseState;
import dev.accesscore.license.sdk.issuing.ProductKeys;
import dev.simplecore.simplix.license.setup.dto.SetupLicenseDTO;
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

/**
 * Installer license step: registers a license before anyone can sign in. Entity-less
 * aggregation surface, hand-authored per the generator-first exception.
 *
 * <p>The application's own license surface requires an authenticated administrator, and no
 * account exists yet while the wizard runs — so the installer carries its own registration
 * path. Like the env-profile editor beside it, every operation is restricted to the
 * pre-initialization state and to a trusted installer caller (loopback, or the configured
 * bootstrap token), which the initialization gate enforces for this whole path.
 *
 * <p>Registering here rather than after sign-in is what lets the wizard's feature step offer
 * exactly the modules the license grants: activation refuses anything it does not.
 */
@RestController
@RequestMapping("/system/setup/license")
@Tag(name = "system.Setup", description = "First-run setup wizard")
@SimpliXStandardApi
public class LicenseSetupController {

    private final LicenseManager licenseManager;
    private final LicenseActivationService activationService;
    private final SetupState setupState;
    private final ProductKeys productKeys;
    private final String productKeyPrefix;

    public LicenseSetupController(LicenseManager licenseManager,
                                  LicenseActivationService activationService,
                                  SetupState setupState,
                                  ProductKeys productKeys,
                                  LicenseProperties properties) {
        this.licenseManager = licenseManager;
        this.activationService = activationService;
        this.setupState = setupState;
        this.productKeys = productKeys;
        this.productKeyPrefix = properties.getProductKeyPrefix();
    }

    /**
     * Reads what is registered so far.
     *
     * @return the installer's view of the license
     */
    @GetMapping
    @Operation(summary = "Read Installer License",
            description = "Whether a license is registered during first-run setup and which "
                    + "modules it grants, so the wizard can offer them")
    @PreAuthorize("permitAll()")
    public SimpliXApiResponse<SetupLicenseDTO> status() {
        requireUninitialized();
        return SimpliXApiResponse.success(buildStatus());
    }

    /**
     * Registers the license server this deployment activates against, after confirming it.
     *
     * <p>A deployment shipped without an address cannot activate online at all, and the person
     * installing it usually cannot edit a file on the host to supply one. Taking it here closes
     * that gap — but only after asking the address to identify itself, because the failure this
     * prevents is silent otherwise: a server that answers while signing with another key hands
     * back a token this build refuses, long after the address was accepted.
     *
     * <p>The key it signs with is checked against EVERY key this deployment verifies with, not
     * against the first one. An issuing server that signs with a delegated key is exactly what an
     * issuer certificate makes acceptable, and comparing against the built-in key alone refuses
     * the arrangement the certificate exists to permit — while the activation path a step later
     * accepts it, so the wizard is the only place that says no.
     *
     * @param dto the candidate address
     * @return the installer's view, now reporting online activation as available
     */
    @PostMapping("/server")
    @Operation(summary = "Register License Server During Setup",
            description = "Confirms the given license server answers and signs with the key this "
                    + "build verifies against, then records it for activation")
    @PreAuthorize("permitAll()")
    public SimpliXApiResponse<SetupLicenseDTO> registerServer(
            @Validated @RequestBody LicenseServerRequestDTO dto) {
        requireUninitialized();

        ActivationServerProbe probe = activationService.identifyServer(dto.getServerUrl());
        if (!probe.isConfirmed()) {
            throw new SimpliXGeneralException(ErrorCode.GEN_CONFLICT,
                    "{error.license.activationServerUnconfirmed}", null);
        }
        if (!licenseManager.verificationKeyFingerprints().contains(probe.signingKeyFingerprint())) {
            throw new SimpliXGeneralException(ErrorCode.GEN_CONFLICT,
                    "{error.license.activationServerKeyMismatch}", null);
        }

        setupState.saveLicenseServerUrl(dto.getServerUrl());
        return SimpliXApiResponse.success(buildStatus(), "License server registered");
    }

    /**
     * Registers a license by claiming a seat over the network.
     *
     * @param dto the product key to activate
     * @return the installer's view of the registered license
     */
    @PostMapping("/activate")
    @Operation(summary = "Activate License During Setup",
            description = "Claims a seat on the license server with the given product key and "
                    + "registers the machine-bound license it returns")
    @PreAuthorize("permitAll()")
    public SimpliXApiResponse<SetupLicenseDTO> activate(@Validated @RequestBody ActivateRequestDTO dto) {
        requireUninitialized();
        ActivationFailures.require(activationService.activateOnline(
                dto.getProductKey(), Boolean.TRUE.equals(dto.getReplaceExisting())));
        return SimpliXApiResponse.success(buildStatus(), "License activated");
    }

    /**
     * The activation request a closed-network deployment carries to the license server.
     *
     * @param productKey the product key the request is built for
     * @return the request an operator submits to the license server
     */
    @GetMapping("/activation-request")
    @Operation(summary = "Build Offline Activation Request During Setup",
            description = "Returns the activation request an operator submits to the license "
                    + "server when this deployment cannot reach it over the network")
    @PreAuthorize("permitAll()")
    public SimpliXApiResponse<ActivationRequestFileDTO> activationRequest(
            @RequestParam String productKey) {
        requireUninitialized();
        // Checked here so a mistyped key is named as such: past this point the only reason a
        // request cannot be built is a missing machine fingerprint.
        if (productKeys.canonical(productKeyPrefix, productKey) == null) {
            throw new SimpliXGeneralException(ErrorCode.GEN_BAD_REQUEST,
                    "{error.license.malformedProductKey}", null);
        }

        PreparedRequest request = activationService.buildOfflineRequest(productKey)
                .orElseThrow(() -> new SimpliXGeneralException(ErrorCode.GEN_CONFLICT,
                        "{error.license.fingerprintUnavailable}", null));

        // The body travels whole: the issuing side reads the same bytes whether they arrived
        // over a socket or on a memory stick, so an offline activation goes through the path an
        // online one does rather than a reassembled imitation of it.
        return SimpliXApiResponse.success(ActivationRequestFileDTO.builder()
                .body(request.body())
                .nonce(request.nonce())
                .requestedAt(Instant.now())
                .build());
    }

    /**
     * Registers a license from the signed answer to an offline activation request.
     *
     * @param dto the product key and the signed token issued for it
     * @return the installer's view of the registered license
     */
    @PostMapping("/activation-response")
    @Operation(summary = "Apply Offline Activation Response During Setup",
            description = "Registers the signed license issued for an offline activation "
                    + "request, after verifying its signature and machine binding")
    @PreAuthorize("permitAll()")
    public SimpliXApiResponse<SetupLicenseDTO> activationResponse(
            @Validated @RequestBody OfflineResponseDTO dto) {
        requireUninitialized();
        ActivationFailures.require(
                activationService.applyOfflineResponse(dto.getProductKey(), dto.getLicenseToken()));
        return SimpliXApiResponse.success(buildStatus(), "License registered");
    }

    /**
     * @return the installer's view of the current license
     */
    private SetupLicenseDTO buildStatus() {
        LicenseState.Snapshot snapshot = licenseManager.getState().snapshot();
        LicensePayload payload = snapshot.payload();

        return SetupLicenseDTO.builder()
                .registered(payload != null)
                .status(snapshot.status().name())
                .customer(payload == null ? null : payload.customer())
                .features(payload == null ? List.of() : licenseManager.availableFeatures())
                .onlineActivationAvailable(activationService.isOnlineActivationAvailable())
                .machineFingerprints(licenseManager.machineFingerprints())
                .build();
    }

    /**
     * Asserts the app has not completed first-run setup, so the installer surface stays closed
     * once an administrator exists to use the application's own license screen.
     *
     * @throws SimpliXGeneralException GEN_CONFLICT when the app is already initialized
     */
    private void requireUninitialized() {
        if (setupState.isInitialized()) {
            throw new SimpliXGeneralException(ErrorCode.GEN_CONFLICT, "{error.system.alreadyInitialized}", null);
        }
    }
}
