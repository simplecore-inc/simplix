package dev.simplecore.simplix.license.config;

/**
 * What tells this installation apart from another one on the same host.
 *
 * <p>Contributed by the application, which alone knows where its data lives and how to observe
 * it. The machine fingerprint names the host; two installations copied onto one host share it,
 * so the licence server needs a second value to count them apart. That value is read from the
 * running environment on every request rather than stored, because anything stored is copied
 * along with the directory it sits in.
 *
 * <p>This module carries it and does nothing else with it: no computation, no comparison. The
 * licence server judges it, and an application that contributes no bean reports nothing — a
 * licence server that receives nothing judges the claim by the fingerprint alone, exactly as it
 * did before.
 */
public interface InstallationDiscriminator {

    /**
     * @return the value as the licence server reads it, or null when this environment cannot
     *         observe one
     */
    String installationDiscriminator();
}
