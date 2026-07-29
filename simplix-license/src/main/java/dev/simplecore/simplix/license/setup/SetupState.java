package dev.simplecore.simplix.license.setup;

import java.util.Optional;

/**
 * What the framework asks the application about first-run setup.
 *
 * <p>The installer paths and the gate in front of them need to know whether setup has finished
 * and where the license server lives. Both answers are kept in whatever settings row the
 * application already owns, so the framework asks rather than storing a second copy.
 */
public interface SetupState {

    /**
     * @return whether first-run setup has completed
     */
    boolean isInitialized();

    /**
     * @return the license server this deployment activates against, empty when none is recorded
     */
    Optional<String> licenseServerUrl();

    /**
     * Records the license server this deployment activates against.
     *
     * @param serverUrl the confirmed address
     */
    void saveLicenseServerUrl(String serverUrl);
}
