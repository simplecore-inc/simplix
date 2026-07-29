package dev.simplecore.simplix.license.activation;

import dev.simplecore.simplix.license.core.LicenseAuditTrail;
import dev.simplecore.simplix.license.core.LicenseManager;
import dev.accesscore.license.sdk.issuing.ProductKeys;
import dev.accesscore.license.sdk.model.LicenseModel.LicensePayload;
import dev.accesscore.license.sdk.model.LicenseModel.RegistrationRecord;
import dev.accesscore.license.sdk.protocol.ActivationModel.ActivationError;
import dev.accesscore.license.sdk.protocol.ActivationModel.ActivationOperation;
import dev.accesscore.license.sdk.protocol.ActivationModel.ActivationOutcome;
import dev.accesscore.license.sdk.protocol.ActivationModel.ActivationSuccess;
import dev.accesscore.license.sdk.protocol.ActivationModel.PreparedRequest;
import dev.accesscore.license.sdk.spi.LicenseSpi.LifecycleEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Drives license registration: claiming a seat, refreshing it, and releasing it.
 *
 * <p>Both transports go through the same steps. Online, the request is posted to the license
 * server and the answer comes back in the response. Offline, the same request is written to a
 * file an operator carries to the server, and the signed answer is uploaded back. In both cases
 * the returned token is checked locally — signature, nonce, then machine binding — before it is
 * persisted, so a hostile or mistaken answer cannot install a token this deployment would later
 * reject at startup.
 *
 * <p>Every one of those checks is the SDK's. What this class owns is the order of the steps and
 * what this product does between them: which key was registered, which seat is being replaced,
 * and what goes in the audit trail.
 *
 * <p>A revoked activation is never replaced by an uploaded token. Revocation is recorded
 * locally, and an offline response could otherwise be replayed to reinstate a token issued
 * before the revocation.
 */
public class LicenseActivationService {

    private static final Logger log = LoggerFactory.getLogger(LicenseActivationService.class);

    private final LicenseActivationClient client;
    private final LicenseManager licenseManager;
    private final ProductKeys productKeys;
    private final String productKeyPrefix;
    private final LicenseAuditTrail auditTrail;
    private final dev.accesscore.license.sdk.LicenseManager sdk;

    /**
     * @param client the activation transport
     * @param licenseManager the orchestrator holding the registration
     * @param productKeys the key format, for reading a key an operator typed
     * @param productKeyPrefix the product family this deployment's keys are issued under
     * @param auditTrail where registration lifecycle events are recorded
     * @param sdk the SDK manager that writes the request bodies
     */
    public LicenseActivationService(LicenseActivationClient client,
                                    LicenseManager licenseManager,
                                    ProductKeys productKeys,
                                    String productKeyPrefix,
                                    LicenseAuditTrail auditTrail,
                                    dev.accesscore.license.sdk.LicenseManager sdk) {
        this.client = client;
        this.licenseManager = licenseManager;
        this.productKeys = productKeys;
        this.productKeyPrefix = productKeyPrefix;
        this.auditTrail = auditTrail;
        this.sdk = sdk;
    }

    /**
     * @return whether an activation server is configured for this deployment
     */
    public boolean isOnlineActivationAvailable() {
        return client.isConfigured();
    }

    /**
     * Asks a candidate license server to identify itself.
     *
     * <p>Separate from activation because it runs before there is anything to activate with: an
     * operator supplying an address needs to know it answers and signs with the right key
     * before the address is worth keeping.
     *
     * @param serverUrl the address to check
     * @return what the server reported, or why it could not be confirmed
     */
    public ActivationServerProbe identifyServer(String serverUrl) {
        return client.identify(serverUrl);
    }

    /**
     * Claims a seat over the network and registers the returned license.
     *
     * @param productKey the customer-facing key
     * @return the outcome
     */
    public ActivationOutcome activateOnline(String productKey) {
        return activateOnline(productKey, false);
    }

    /**
     * Claims a seat over the network, optionally replacing a registration that still works.
     *
     * <p>The key is checked against its own checksum before anything leaves the host, so a
     * mistyped character comes back as "the key is not well formed" instead of a round trip
     * that ends in an unexplained refusal from the license server.
     *
     * @param productKey the customer-facing key
     * @param replaceExisting whether a usable registration under another key may be replaced
     * @return the outcome
     */
    public ActivationOutcome activateOnline(String productKey, boolean replaceExisting) {
        String canonical = productKeys.canonical(productKeyPrefix, productKey);
        if (canonical == null) {
            return ActivationOutcome.failure(ActivationError.UNKNOWN_PRODUCT_KEY,
                    "The product key is not well formed");
        }

        ActivationOutcome occupied = refuseUnlessReplacing(canonical, replaceExisting);
        if (occupied != null) {
            return occupied;
        }
        if (licenseManager.machineFingerprints().isEmpty()) {
            return ActivationOutcome.failure(ActivationError.FINGERPRINT_UNAVAILABLE,
                    "No stable machine identifier is available on this host");
        }

        PreparedRequest request = sdk.activationRequest(canonical, Instant.now());
        ActivationOutcome outcome = client.send(ActivationOperation.ACTIVATE, request);
        if (!outcome.isSuccess()) {
            log.warn("Online activation failed: {}", outcome.error());
            return outcome;
        }

        ActivationSuccess result = outcome.result();
        licenseManager.applyRecord(new RegistrationRecord(
                canonical,
                result.licenseToken(),
                result.activationId(),
                result.activationSecret(),
                instanceId(),
                null,
                Instant.now(),
                null));
        log.info("License activated for activationId={}", result.activationId());
        auditTrail.lifecycle(LifecycleEvent.ACTIVATED, currentLicenseId(),
                "Activated online against the license server");
        return outcome;
    }

    /**
     * Builds the request a closed-network installation carries to the license server.
     *
     * <p>A malformed key produces no request at all. The operator carries the file to another
     * network, so a typo that only surfaced there would cost a second trip.
     *
     * @param productKey the customer-facing key
     * @return the request body, or empty when the key is malformed or this machine reports no
     *         identifier
     */
    public Optional<PreparedRequest> buildOfflineRequest(String productKey) {
        String canonical = productKeys.canonical(productKeyPrefix, productKey);
        if (canonical == null || licenseManager.machineFingerprints().isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(sdk.activationRequest(canonical, Instant.now()));
    }

    /**
     * Registers a license token issued from an offline activation request.
     *
     * <p>The nonce echo is not enforced here — the operator's browser does not hold the request
     * that produced this file. Replay is instead closed off by the machine binding and by
     * refusing to overwrite a revoked activation; duplicate request files are rejected by the
     * license server, which is the side that keeps the ledger.
     *
     * @param productKey the customer-facing key
     * @param licenseToken the signed token from the response file
     * @return the outcome
     */
    public ActivationOutcome applyOfflineResponse(String productKey, String licenseToken) {
        return applyOfflineResponse(productKey, licenseToken, false);
    }

    /**
     * Registers a license from an offline activation response, optionally replacing a
     * registration that still works.
     *
     * @param productKey the customer-facing key
     * @param licenseToken the signed token from the response file
     * @param replaceExisting whether a usable registration under another key may be replaced
     * @return the outcome
     */
    public ActivationOutcome applyOfflineResponse(String productKey, String licenseToken,
                                                  boolean replaceExisting) {
        String canonical = productKeys.canonical(productKeyPrefix, productKey);
        if (canonical == null) {
            return ActivationOutcome.failure(ActivationError.UNKNOWN_PRODUCT_KEY,
                    "The product key is not well formed");
        }

        ActivationOutcome occupied = refuseUnlessReplacing(canonical, replaceExisting);
        if (occupied != null) {
            return occupied;
        }
        if (licenseManager.machineFingerprints().isEmpty()) {
            return ActivationOutcome.failure(ActivationError.FINGERPRINT_UNAVAILABLE,
                    "No stable machine identifier is available on this host");
        }

        Optional<RegistrationRecord> existing = licenseManager.currentRecord();
        if (existing.isPresent() && existing.get().revokedAt() != null) {
            return ActivationOutcome.failure(ActivationError.REVOKED,
                    "This activation was revoked and cannot be reinstated from a file");
        }

        // Judged before it is stored, against this build's key and this machine. A token this
        // deployment would refuse at its next start is refused here instead, while an operator
        // is still looking at the screen that uploaded it.
        ActivationOutcome accepted = sdk.interpret(ActivationOperation.ACTIVATE, 200,
                envelope(licenseToken), null, true);
        if (!accepted.isSuccess()) {
            return ActivationOutcome.failure(accepted.error(),
                    "The uploaded license file was not accepted");
        }

        licenseManager.applyRecord(new RegistrationRecord(
                canonical,
                licenseToken,
                existing.map(RegistrationRecord::activationId).orElse(null),
                existing.map(RegistrationRecord::activationSecret).orElse(null),
                existing.map(RegistrationRecord::instanceId).orElseGet(LicenseActivationService::newInstanceId),
                existing.map(RegistrationRecord::verificationWatermark).orElse(null),
                existing.map(RegistrationRecord::lastHeartbeatAt).orElse(null),
                null));
        log.info("License registered from an offline activation response");
        auditTrail.lifecycle(LifecycleEvent.ACTIVATED, currentLicenseId(),
                "Registered from an offline activation response");
        return ActivationOutcome.success(new ActivationSuccess(licenseToken, null, null, null));
    }

    /**
     * Refreshes the token so revocation, expiry, and contract changes reach this deployment.
     *
     * @return the outcome
     */
    public ActivationOutcome heartbeat() {
        Optional<RegistrationRecord> loaded = licenseManager.currentRecord();
        if (loaded.isEmpty() || !loaded.get().hasActivationCredentials()) {
            return ActivationOutcome.failure(ActivationError.NOT_ACTIVATED,
                    "This deployment has no online activation to refresh");
        }
        if (licenseManager.machineFingerprints().isEmpty()) {
            return ActivationOutcome.failure(ActivationError.FINGERPRINT_UNAVAILABLE,
                    "No stable machine identifier is available on this host");
        }

        RegistrationRecord record = loaded.get();
        PreparedRequest request = sdk.heartbeatRequest(Instant.now());
        ActivationOutcome outcome = client.send(ActivationOperation.HEARTBEAT, request);

        if (outcome.error() == ActivationError.REVOKED) {
            String licenseId = currentLicenseId();
            licenseManager.markRevoked();
            auditTrail.lifecycle(LifecycleEvent.REVOKED, licenseId,
                    "The license server reported this activation revoked");
            return outcome;
        }
        if (!outcome.isSuccess()) {
            log.warn("License heartbeat failed: {}", outcome.error());
            return outcome;
        }

        licenseManager.applyRecord(
                record.withHeartbeat(outcome.result().licenseToken(), Instant.now()));
        auditTrail.lifecycle(LifecycleEvent.HEARTBEAT, currentLicenseId(),
                "Token refreshed from the license server");
        return outcome;
    }

    /**
     * Releases the seat and discards the local registration, so the license can be moved to
     * another machine.
     *
     * @return the outcome
     */
    public ActivationOutcome deactivate() {
        Optional<RegistrationRecord> loaded = licenseManager.currentRecord();
        if (loaded.isEmpty()) {
            return ActivationOutcome.failure(ActivationError.NOT_ACTIVATED,
                    "This deployment has no license to release");
        }

        String licenseId = currentLicenseId();
        RegistrationRecord record = loaded.get();
        if (record.hasActivationCredentials() && client.isConfigured()) {
            ActivationOutcome outcome = client.send(ActivationOperation.DEACTIVATE,
                    sdk.deactivationRequest(Instant.now()));
            if (!outcome.isSuccess() && outcome.error() != ActivationError.REVOKED) {
                log.warn("Seat release was refused: {}", outcome.error());
                return outcome;
            }
        }

        licenseManager.clearRegistration();
        log.info("License registration cleared");
        auditTrail.lifecycle(LifecycleEvent.DEACTIVATED, licenseId,
                "Seat released and the local registration cleared");
        return ActivationOutcome.released();
    }

    /**
     * Refuses to register a different key over a registration that still works, unless the
     * caller says that is what they mean.
     *
     * <p>Overwriting silently is how one machine ends up counted twice. The seat this
     * deployment already holds is not released by registering elsewhere — the issuing ledger
     * counts seats per key and has no way to learn the machine moved — so the old seat stays
     * occupied while a new one is spent. Naming the registered key in the refusal is also the
     * answer to the question that led here: which key is this server on.
     *
     * <p>A registration that no longer verifies is not protected. Replacing an expired or
     * unreadable license is the ordinary way out of it, and there is no seat worth keeping
     * behind a license the deployment cannot use.
     *
     * @param canonicalKey the key being registered
     * @param replaceExisting whether the caller has confirmed the replacement
     * @return the refusal, or null when registration may proceed
     */
    private ActivationOutcome refuseUnlessReplacing(String canonicalKey, boolean replaceExisting) {
        if (replaceExisting || !licenseManager.getState().isUsable()) {
            return null;
        }

        Optional<RegistrationRecord> existing = licenseManager.currentRecord();
        String registered = existing.map(RegistrationRecord::productKey).orElse(null);
        if (registered == null || registered.equals(canonicalKey)) {
            return null;
        }

        return ActivationOutcome.failure(ActivationError.ALREADY_REGISTERED,
                productKeys.masked(productKeyPrefix, registered));
    }

    /**
     * @return the license currently published to the runtime state, null when none was read
     */
    private String currentLicenseId() {
        LicensePayload payload = licenseManager.getState().payload();
        return payload != null ? payload.licenseId() : null;
    }

    /**
     * @return this deployment's stable identifier, kept across registrations
     */
    private String instanceId() {
        return licenseManager.currentRecord()
                .map(RegistrationRecord::instanceId)
                .filter(id -> id != null && !id.isBlank())
                .orElseGet(LicenseActivationService::newInstanceId);
    }

    /**
     * @return a fresh deployment identifier
     */
    private static String newInstanceId() {
        return UUID.randomUUID().toString();
    }

    /**
     * Wraps a token in the envelope the SDK reads an activation answer from, so an uploaded
     * file goes through exactly the same checks a networked answer does.
     *
     * @param licenseToken the signed token
     * @return the envelope
     */
    private static String envelope(String licenseToken) {
        return "{\"body\":{\"licenseToken\":\"" + licenseToken + "\"}}";
    }
}
