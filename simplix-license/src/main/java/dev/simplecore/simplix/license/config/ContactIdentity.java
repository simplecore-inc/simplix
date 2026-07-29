package dev.simplecore.simplix.license.config;

/**
 * Who to reach about this deployment's licence.
 *
 * <p>Contributed by the application from whatever it settled at install time — the address the
 * licence was sold to. The licence server compares it against the address on the customer
 * record when a seat is claimed, and a mismatch is what stops a key from being pasted into the
 * wrong company's installation.
 *
 * <p>What this guards is a mistake, not an attacker: whoever holds the product key almost
 * certainly knows the address it was sold under. Binding a key to a machine is the fingerprint's
 * job, and holding a suspect claim is the approval queue's.
 *
 * <p>Optional. A deployment that contributes no bean reports nothing, and a licence server that
 * receives nothing has nothing to compare — activation proceeds exactly as it did before.
 */
public interface ContactIdentity {

    /**
     * @return the address this deployment answers at, or null when the application has none yet
     */
    String contactEmail();
}
