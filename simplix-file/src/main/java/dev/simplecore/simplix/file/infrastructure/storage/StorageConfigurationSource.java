package dev.simplecore.simplix.file.infrastructure.storage;

import dev.simplecore.simplix.file.config.StorageProperties;

/**
 * Where the S3 storage settings come from.
 * <p>
 * The default source reads {@code simplix.file.storage.s3.*} once and never changes. An application
 * that lets an operator edit the settings at runtime (a database table, a secret store) declares its
 * own bean of this type, which replaces the default. The storage service asks the source on every
 * call and rebuilds its S3 clients when {@link #revision()} differs from the one it built them for.
 * <p>
 * Contract:
 * <ul>
 *   <li>{@link #revision()} is called on every storage operation and must be cheap: a counter or a
 *       timestamp the application keeps in memory, never a query.</li>
 *   <li>The value changes whenever {@link #s3()} would return different settings. Settings that
 *       changed without a new revision are not noticed.</li>
 *   <li>{@link #s3()} is called only when the revision changed. It returns incomplete settings
 *       (no bucket, no credentials) while the installation is not configured yet; storage then
 *       reports {@code NOT_CONFIGURED} instead of failing at startup.</li>
 *   <li>Implementations are thread safe.</li>
 * </ul>
 */
public interface StorageConfigurationSource {

    /**
     * Current S3 settings. The returned object is copied by the caller, so a source may hand out a
     * mutable instance.
     *
     * @return the settings, never null
     */
    StorageProperties.S3StorageConfig s3();

    /**
     * Identifier of the current settings. Equal values mean equal settings.
     *
     * @return a value that differs whenever the settings differ
     */
    long revision();
}
