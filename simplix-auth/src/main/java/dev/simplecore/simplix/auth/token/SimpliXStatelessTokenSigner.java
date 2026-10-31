package dev.simplecore.simplix.auth.token;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;

/**
 * HMAC-SHA256 signer for short-lived stateless tokens.
 *
 * <p>Registered by {@code SimpliXStatelessTokenAutoConfiguration}
 * unless the application defines its own bean.
 *
 * <p>Signs an opaque payload into a URL-safe {@code payload.signature} token and
 * verifies it back without any persistence. Consumers (signed download links,
 * one-time QR codes) encode their own expiry inside the payload and re-check it
 * after signature verification - the signer only guarantees integrity and origin.
 *
 * <p>When {@code simplix.auth.stateless-token.secret} is not configured, a random
 * per-boot key is generated, which invalidates outstanding tokens on restart.
 * Production deployments set the property so tokens survive rolling restarts.
 */
public class SimpliXStatelessTokenSigner {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final int GENERATED_SECRET_BYTES = 32;

    private final byte[] secret;

    /**
     * @param configuredSecret the HMAC key; blank generates a random per-boot key
     */
    public SimpliXStatelessTokenSigner(String configuredSecret) {
        if (configuredSecret == null || configuredSecret.isBlank()) {
            byte[] generated = new byte[GENERATED_SECRET_BYTES];
            new SecureRandom().nextBytes(generated);
            this.secret = generated;
        } else {
            this.secret = configuredSecret.getBytes(StandardCharsets.UTF_8);
        }
    }

    /**
     * Signs a payload into a URL-safe token.
     *
     * @param payload the payload to protect; must be non-blank
     * @return {@code base64url(payload) + "." + base64url(hmac)}
     */
    public String sign(String payload) {
        if (payload == null || payload.isBlank()) {
            throw new IllegalArgumentException("Token payload must not be blank");
        }
        byte[] payloadBytes = payload.getBytes(StandardCharsets.UTF_8);
        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        return encoder.encodeToString(payloadBytes) + "." + encoder.encodeToString(hmac(payloadBytes));
    }

    /**
     * Computes the keyed digest of a value as a URL-safe string.
     *
     * <p>For deterministic, non-reversible identifiers derived from sensitive input
     * (e.g. decoy credential ids, verified-contact bindings) - NOT a token; there is
     * nothing to verify back.
     *
     * @param value the value to digest; must be non-blank
     * @return base64url-encoded HMAC of the value
     */
    public String mac(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Digest value must not be blank");
        }
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(hmac(value.getBytes(StandardCharsets.UTF_8)));
    }

    /**
     * Verifies a token and returns its payload.
     *
     * @param token the token produced by {@link #sign(String)}
     * @return the payload when the signature matches; empty for malformed or tampered tokens
     */
    public Optional<String> verify(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        int separator = token.lastIndexOf('.');
        if (separator <= 0 || separator == token.length() - 1) {
            return Optional.empty();
        }
        byte[] payloadBytes;
        byte[] providedSignature;
        try {
            Base64.Decoder decoder = Base64.getUrlDecoder();
            payloadBytes = decoder.decode(token.substring(0, separator));
            providedSignature = decoder.decode(token.substring(separator + 1));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
        if (!MessageDigest.isEqual(hmac(payloadBytes), providedSignature)) {
            return Optional.empty();
        }
        return Optional.of(new String(payloadBytes, StandardCharsets.UTF_8));
    }

    private byte[] hmac(byte[] payloadBytes) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret, HMAC_ALGORITHM));
            return mac.doFinal(payloadBytes);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            // HmacSHA256 is mandatory on every JVM and the key is never empty here.
            throw new IllegalStateException("HMAC signing unavailable", e);
        }
    }
}
