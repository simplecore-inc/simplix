package dev.simplecore.simplix.license.config;

import dev.accesscore.license.sdk.model.LicenseModel.KeyMaterial;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Every verification key this build carries.
 *
 * <p>A deployment carries more than one so that replacing the issuer's signing key does not cut
 * off the licenses the previous one signed. Which key checks a given token is the payload's
 * {@code kid}, and that choice is the SDK's.
 *
 * <p>Read from a fixed classpath location. A deployment that could point at keys of its own
 * could verify a license it minted itself, and at that moment the signature check defends
 * nothing. What the resource changes is how many keys are carried, not who decides which.
 */
public final class VerificationKeys {

    private final List<VerificationKey> keys;
    private final Set<String> compromisedKeyIds;

    /**
     * @param keys the keys the embedded resource declares
     * @throws IllegalStateException when the resource declares no key, an unusable one, or the
     *         same name twice — each of which makes some license unverifiable for a reason no
     *         log would name
     */
    public VerificationKeys(List<VerificationKey> keys) {
        if (keys == null || keys.isEmpty()) {
            throw new IllegalStateException(
                    "This build carries no verification key, so no license can be judged here");
        }
        Set<String> seen = new HashSet<>();
        Set<String> compromised = new HashSet<>();
        for (VerificationKey key : keys) {
            if (key.keyId() == null || key.keyId().isBlank()) {
                throw new IllegalStateException("A verification key carries no name");
            }
            if (key.publicKeyPem() == null || key.publicKeyPem().isBlank()) {
                throw new IllegalStateException(
                        "Verification key " + key.keyId() + " carries no key material");
            }
            if (!seen.add(key.keyId())) {
                throw new IllegalStateException(
                        "Verification key " + key.keyId() + " is declared twice");
            }
            if (key.compromised()) {
                compromised.add(key.keyId());
            }
        }
        this.keys = List.copyOf(keys);
        this.compromisedKeyIds = Set.copyOf(compromised);
    }

    /**
     * @return the keys as the SDK takes them
     */
    public List<KeyMaterial> materials() {
        return keys.stream().map(key -> KeyMaterial.of(key.keyId(), key.publicKeyPem())).toList();
    }

    /**
     * @param keyId the name a token was signed under
     * @return whether that key's signing half is known to be in someone else's hands
     */
    public boolean isCompromised(String keyId) {
        return keyId != null && compromisedKeyIds.contains(keyId);
    }

    /**
     * @return whether any key here is marked compromised, which is what decides whether this
     *         deployment holds a ceiling at all
     */
    public boolean carriesCompromisedKey() {
        return !compromisedKeyIds.isEmpty();
    }
}
