package dev.simplecore.simplix.license.config;

import dev.simplecore.simplix.license.core.FileLicenseStore;
import dev.simplecore.simplix.license.model.LicenseState;
import dev.accesscore.license.sdk.model.LicenseChannel;
import dev.accesscore.license.sdk.model.LicenseModel.EvaluationRequest;
import dev.accesscore.license.sdk.model.LicenseModel.EvaluationResponse;
import dev.accesscore.license.sdk.model.LicenseModel.LicensePayload;
import dev.accesscore.license.sdk.model.LicenseModel.RegistrationRecord;
import dev.accesscore.license.sdk.model.LicenseStatus;
import dev.accesscore.license.sdk.protocol.ActivationModel.ActivationOutcome;
import dev.accesscore.license.sdk.protocol.ActivationModel.PreparedRequest;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;

/**
 * GraalVM native image hints for licensing.
 *
 * <p>Everything crossing the SDK boundary is JSON, so the shapes on both sides of it need
 * constructor and accessor reflection to survive ahead-of-time compilation. The native library
 * itself is loaded from the jar at run time and needs a resource hint of its own.
 */
public class LicenseRuntimeHints implements RuntimeHintsRegistrar {

    @Override
    public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
        registerReflectionHints(hints);
        registerResourceHints(hints);
    }

    /**
     * @param hints where the reflection hints are registered
     */
    private void registerReflectionHints(RuntimeHints hints) {
        // What crosses the boundary in either direction.
        for (Class<?> type : new Class<?>[]{
                LicensePayload.class,
                EvaluationRequest.class,
                EvaluationResponse.class,
                RegistrationRecord.class,
                PreparedRequest.class,
                ActivationOutcome.class,
                FileLicenseStore.State.class,
                LicenseState.Snapshot.class,
                LicenseProperties.class,
                LicenseProperties.Activation.class
        }) {
            hints.reflection().registerType(type,
                    MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                    MemberCategory.INVOKE_PUBLIC_METHODS,
                    MemberCategory.DECLARED_FIELDS);
        }

        // Enums read out of JSON.
        for (Class<?> type : new Class<?>[]{
                LicenseStatus.class,
                LicenseChannel.class,
                LicenseProperties.GracePeriodMode.class
        }) {
            hints.reflection().registerType(type, MemberCategory.INVOKE_PUBLIC_METHODS);
        }
    }

    /**
     * @param hints where the resource hints are registered
     */
    private void registerResourceHints(RuntimeHints hints) {
        // The verification key this build carries.
        hints.resources().registerPattern("license-public-key.pem");

        // The license core, extracted from the jar on first use. Without this the native image
        // carries no library to extract and licensing cannot start.
        hints.resources().registerPattern("dev/accesscore/license/sdk/native/*/*");
    }
}
