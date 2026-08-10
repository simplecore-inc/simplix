package dev.simplecore.simplix.license.setup.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * What the installer's license step shows: whether a license is registered and what it grants,
 * so the wizard can offer exactly the modules the deployment may switch on.
 * <p>
 * Narrower than the application's license status on purpose — the installer runs before anyone
 * has authenticated, so it reports only what an operator needs to decide the next step, not the
 * whole contract.
 */
@Getter
@Builder
public class SetupLicenseDTO {

    /** Whether a license is registered on this deployment. */
    private Boolean registered;

    /** The judgement's current status. */
    private String status;

    /** Who the license was issued to. */
    private String customer;

    /** The features the license grants. */
    private List<String> features;

    /** Whether this deployment can reach a license server to activate over the network. */
    private Boolean onlineActivationAvailable;

    /**
     * Every fingerprint this machine reports about itself. An operator carrying an offline
     * request reads them here, and the license binds to all of them so a host that later reads
     * one fewer keeps working.
     */
    private List<String> machineFingerprints;

    /**
     * Which kinds of identifier answered, and which were consulted and gave nothing.
     *
     * <p>Reported here for the same reason as on the licence screen, and more urgently: the
     * installer is where a token cut for another machine is first refused, and the operator
     * refused there has no console to go and look in. The fingerprints alone cannot separate a
     * mount the container never received from a token minted against a different host, and the
     * two want opposite fixes.
     */
    private List<String> machineIdentifierSources;

    /** The kinds consulted that gave nothing, which is what an operator can still mount. */
    private List<String> machineIdentifierSourcesUnavailable;
}
