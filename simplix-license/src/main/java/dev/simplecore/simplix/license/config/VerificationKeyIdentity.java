package dev.simplecore.simplix.license.config;

/**
 * The name this build files its verification key under.
 *
 * <p>Every product supplies its own. There is no default, for the same reason there is no
 * default {@code ProductIdentity}: a name that arrived by falling through would be some other
 * product's, and a deployment cannot tell that it is verifying against the wrong one — the
 * token simply fails to resolve a key, and every license looks unverifiable with nothing to
 * point at.
 *
 * <p>Contributed as a bean rather than read from configuration, and for the same reason the key
 * location is fixed: a deployment that could rename the key it trusts could accept a token
 * signed by a key of its own making. This lives in compiled code, where an operator editing a
 * settings file cannot reach it.
 */
public interface VerificationKeyIdentity {

    /**
     * @return the name a token signed for this product carries, and the name the embedded
     *         public key is registered under
     */
    String verificationKeyId();
}
