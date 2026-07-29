package dev.simplecore.simplix.license.activation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.accesscore.license.sdk.protocol.ActivationModel.ActivationError;
import dev.accesscore.license.sdk.protocol.ActivationModel.ActivationOperation;
import dev.accesscore.license.sdk.protocol.ActivationModel.ActivationOutcome;
import dev.accesscore.license.sdk.protocol.ActivationModel.PreparedRequest;
import dev.accesscore.license.sdk.spi.LicenseSpi.ActivationServerDirectory;
import dev.simplecore.simplix.license.config.ContactIdentity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/**
 * Talks to the license server over HTTPS to claim, refresh, and release a seat.
 *
 * <p>Transport only. The request body and the reading of the answer are the SDK's, so this
 * deployment sends exactly what a deployment in any other language sends and judges the answer
 * by the same rules. What is left here is the part that genuinely belongs to a host: opening a
 * connection, choosing a timeout, and telling an unreachable server from a refusing one.
 *
 * <p>One thing rides alongside the SDK's body rather than inside it: the address this deployment
 * answers at, sent when a seat is claimed. It is a header because the body is the SDK's protocol,
 * and what it guards is a key reaching the wrong installation — a mistake rather than an attack,
 * since whoever holds the key almost certainly knows the address it was sold under. The licence
 * server refuses a claim that names an address its customer record does not carry.
 */
public class LicenseActivationClient {

    private static final Logger log = LoggerFactory.getLogger(LicenseActivationClient.class);

    /** Field the standard response envelope carries its payload under. */
    private static final String ENVELOPE_BODY_FIELD = "body";

    /** Header the address this deployment answers at is reported under. */
    private static final String CONTACT_HEADER = "X-Activation-Contact";

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(20);

    /** The status a host reports when it never reached the server at all. */
    private static final int NOT_REACHED = 0;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String configuredServerUrl;
    private final ObjectProvider<ActivationServerDirectory> directory;
    private final ObjectProvider<ContactIdentity> contactIdentity;
    private final dev.accesscore.license.sdk.LicenseManager sdk;

    /**
     * @param serverUrl the configured address, used when the application registers none
     * @param objectMapper the mapper the envelope is read with
     * @param directory where a running deployment's own address is kept, resolved lazily so
     *        licensing works in a context that contributes none
     * @param contactIdentity where the address this deployment answers at is read, absent in an
     *        application that reports none
     * @param sdk the SDK manager that writes the bodies and reads the answers
     */
    public LicenseActivationClient(String serverUrl, ObjectMapper objectMapper,
                                   ObjectProvider<ActivationServerDirectory> directory,
                                   ObjectProvider<ContactIdentity> contactIdentity,
                                   dev.accesscore.license.sdk.LicenseManager sdk) {
        this.configuredServerUrl = normalizeServerUrl(serverUrl);
        this.objectMapper = objectMapper;
        this.directory = directory;
        this.contactIdentity = contactIdentity;
        this.sdk = sdk;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    /**
     * The address to call, read per request rather than fixed at startup.
     *
     * <p>An operator who registers a server during installation expects the next activation to
     * go there, not after a restart they have no way to perform.
     *
     * @return the registered address, or the configured one when none is registered
     */
    private String serverUrl() {
        ActivationServerDirectory registry = directory == null ? null : directory.getIfAvailable();
        if (registry == null) {
            return configuredServerUrl;
        }
        return registry.serverUrl()
                .map(LicenseActivationClient::normalizeServerUrl)
                .filter(url -> !url.isEmpty())
                .orElse(configuredServerUrl);
    }

    /**
     * @return whether an activation server is configured
     */
    public boolean isConfigured() {
        return !serverUrl().isEmpty();
    }

    /**
     * Asks a candidate server to identify itself, so an address can be checked before it is
     * saved.
     *
     * <p>Reaching the address is only half the question. A server that answers but signs with
     * another key issues tokens this deployment will refuse, and that failure surfaces long
     * after the address was accepted — at the first activation, as an unverifiable license. The
     * fingerprint comes back here so the caller can compare it against the key this build
     * verifies with while the operator is still on the screen that asked for the address.
     *
     * @param candidateUrl the address to probe
     * @return the outcome, carrying the server's signing key fingerprint on success
     */
    public ActivationServerProbe identify(String candidateUrl) {
        String normalized = normalizeServerUrl(candidateUrl);
        if (normalized.isEmpty()) {
            return ActivationServerProbe.failure(ActivationError.NOT_CONFIGURED,
                    "No activation server address was given");
        }

        HttpRequest request = HttpRequest
                .newBuilder(URI.create(normalized + "/api/v1/activations/identity"))
                .timeout(REQUEST_TIMEOUT)
                .header("Accept", "application/json")
                .GET()
                .build();

        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            log.warn("Activation server unreachable at {}", normalized);
            return ActivationServerProbe.failure(ActivationError.SERVER_UNREACHABLE,
                    "The activation server could not be reached");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ActivationServerProbe.failure(ActivationError.SERVER_UNREACHABLE,
                    "The request was interrupted");
        }

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            // Anything answering here that is not a license server — a proxy, another product,
            // a login page — lands in this branch rather than being mistaken for one.
            return ActivationServerProbe.failure(ActivationError.SERVER_ERROR,
                    "The address answered with HTTP " + response.statusCode());
        }

        try {
            JsonNode payload = objectMapper.readTree(response.body()).path(ENVELOPE_BODY_FIELD);
            String fingerprint = payload.path("signingKeyFingerprint").asText("");
            if (fingerprint.isBlank()) {
                return ActivationServerProbe.failure(ActivationError.INVALID_RESPONSE,
                        "The address answered, but not as a license server");
            }
            return ActivationServerProbe.confirmed(fingerprint);
        } catch (IOException e) {
            return ActivationServerProbe.failure(ActivationError.INVALID_RESPONSE,
                    "The answer could not be read as a license server identity");
        }
    }

    /**
     * Sends a request the SDK prepared, and reads the answer through the SDK.
     *
     * <p>An unreachable server is reported to the SDK as exactly that rather than as a status
     * code, because the two mean different things to a caller and only the host can tell them
     * apart.
     *
     * @param operation which request this is
     * @param prepared the body, the headers, and the nonce the answer has to echo
     * @return the outcome
     */
    public ActivationOutcome send(ActivationOperation operation, PreparedRequest prepared) {
        if (!isConfigured()) {
            return ActivationOutcome.failure(ActivationError.NOT_CONFIGURED,
                    "No activation server is configured");
        }

        HttpRequest.Builder builder = HttpRequest
                .newBuilder(URI.create(serverUrl() + prepared.path()))
                .timeout(REQUEST_TIMEOUT)
                .POST(HttpRequest.BodyPublishers.ofString(prepared.body()));
        for (Map.Entry<String, String> header : prepared.headers().entrySet()) {
            builder.header(header.getKey(), header.getValue());
        }
        // Only on the claim. A refresh happens on a seat the server already admitted, and an
        // address the deployment could restate there would be an address it could move — which
        // is the licence server operator's decision, not this deployment's.
        if (operation == ActivationOperation.ACTIVATE) {
            String contact = reportedContact();
            if (contact != null) {
                builder.header(CONTACT_HEADER, contact);
            }
        }

        HttpResponse<String> response;
        try {
            response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            log.warn("Activation server unreachable at {}", serverUrl());
            return sdk.interpret(operation, NOT_REACHED, "", prepared.nonce(), false);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return sdk.interpret(operation, NOT_REACHED, "", prepared.nonce(), false);
        }

        return sdk.interpret(operation, response.statusCode(), response.body(),
                prepared.nonce(), true);
    }

    /**
     * @return the address this deployment answers at, or null when it reports none
     */
    private String reportedContact() {
        ContactIdentity identity = contactIdentity.getIfAvailable();
        if (identity == null) {
            return null;
        }
        String contact = identity.contactEmail();
        return contact == null || contact.isBlank() ? null : contact.trim();
    }

    /**
     * @param serverUrl an address as configured or registered
     * @return the address without trailing slashes, so two forms of one address compare equal
     */
    private static String normalizeServerUrl(String serverUrl) {
        if (serverUrl == null || serverUrl.isBlank()) {
            return "";
        }
        String url = serverUrl.trim();
        while (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        return url;
    }
}
