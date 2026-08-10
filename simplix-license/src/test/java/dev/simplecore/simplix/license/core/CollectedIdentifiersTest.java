package dev.simplecore.simplix.license.core;

import dev.accesscore.license.sdk.model.LicenseModel.CollectedIdentifiers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What a deployment can say about the machine it is running on.
 *
 * <p>A licence refused as belonging to another machine leaves an operator holding two opaque
 * digests. Two different faults produce that same sentence — a mount the container lost, and a
 * token cut for a different host — and they want opposite fixes. Which identifiers answered and
 * which were consulted and gave nothing is the only thing that separates them, and the SDK
 * collects it on every call already; the deployment merely has to pass it on.
 */
@DisplayName("Collected identifiers - what the machine reported and what it could not")
class CollectedIdentifiersTest {

    @Test
    @DisplayName("should report the sources beside the fingerprints, not only the digests")
    void reportsSourcesBesideFingerprints() {
        CollectedIdentifiers collected = new CollectedIdentifiers(
                java.util.List.of("sha256:aaa"),
                java.util.List.of("IO_PLATFORM_UUID"),
                java.util.List.of("DMI_PRODUCT_UUID"));

        assertThat(collected.fingerprintsOrEmpty())
                .as("what a licence binds to")
                .containsExactly("sha256:aaa");
        assertThat(collected.sources())
                .as("what an operator reads to know the digest came from something real")
                .containsExactly("IO_PLATFORM_UUID");
        assertThat(collected.unavailableSources())
                .as("what is still missing, which is the one thing an operator can act on")
                .containsExactly("DMI_PRODUCT_UUID");
    }

    @Test
    @DisplayName("should answer no fingerprints as unavailable rather than as an empty machine")
    void treatsNothingReadAsUnavailable() {
        CollectedIdentifiers nothing = new CollectedIdentifiers(
                java.util.List.of(), java.util.List.of(), java.util.List.of("IO_PLATFORM_UUID"));

        assertThat(nothing.isAvailable())
                .as("a server that cannot identify its machine refuses to register at all, and "
                        + "the sources are what say which mount would fix that")
                .isFalse();
        assertThat(nothing.fingerprintsOrEmpty()).isEmpty();
    }
}
