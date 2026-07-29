package dev.simplecore.simplix.license.activation;

import dev.accesscore.license.sdk.protocol.ActivationModel.ActivationError;
import org.jspecify.annotations.Nullable;

/**
 * What asking a candidate license server to identify itself produced.
 *
 * <p>Kept apart from an activation outcome because the question is different: an activation
 * asks for a token, while this asks whether an address is worth saving. The answer that matters
 * is the signing key fingerprint — an address that answers but signs with another key would
 * issue tokens this deployment refuses, and finding that out here costs one request instead of
 * a support call.
 *
 * @param error why the server could not be confirmed, null when it was
 * @param signingKeyFingerprint the key the server signs licenses with, null on failure
 * @param detail a message for logs and operator-facing errors
 */
public record ActivationServerProbe(
        @Nullable ActivationError error,
        @Nullable String signingKeyFingerprint,
        String detail
) {

    /**
     * @param signingKeyFingerprint the fingerprint the server reported
     * @return a confirmed server
     */
    public static ActivationServerProbe confirmed(String signingKeyFingerprint) {
        return new ActivationServerProbe(null, signingKeyFingerprint, "");
    }

    /**
     * @param error the reason
     * @param detail a message for logs and operator-facing errors
     * @return a server that could not be confirmed
     */
    public static ActivationServerProbe failure(ActivationError error, String detail) {
        return new ActivationServerProbe(error, null, detail);
    }

    /**
     * @return whether the server answered and named its signing key
     */
    public boolean isConfirmed() {
        return error == null;
    }
}
