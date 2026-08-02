package dev.simplecore.simplix.license.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * One verification key a build carries, as the embedded resource holds it.
 *
 * <p>The name and the key material live in the same entry. Held apart — a name in compiled code
 * and a key in a file beside it — the two can drift, and a name that resolves no key makes every
 * license look unverifiable with nothing to point at.
 *
 * @param keyId the name a payload's {@code kid} matches
 * @param publicKeyPem the key, as an X.509 PEM document
 * @param compromised whether this key's signing half is known to be in someone else's hands, in
 *        which case a token it signed may keep what this deployment already holds but may not
 *        grant more
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record VerificationKey(String keyId, String publicKeyPem, boolean compromised) {
}
