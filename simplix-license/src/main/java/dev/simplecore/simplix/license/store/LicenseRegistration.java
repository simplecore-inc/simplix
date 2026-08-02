package dev.simplecore.simplix.license.store;

import dev.simplecore.simplix.core.entity.SimpliXBaseEntity;
import dev.simplecore.simplix.core.hibernate.UuidV7Generator;
import dev.simplecore.simplix.encryption.persistence.converter.AesEncryptionConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Comment;

import java.time.Instant;

/**
 * Single-row license registration for this deployment.
 * <p>
 * No REST surface is generated for this entity: the license is managed through the
 * {@code /license} surface, which speaks in tokens and activation outcomes rather than rows.
 * The product key, the signed token, and the activation secret are encrypted at rest.
 * <p>
 * The timestamps are filled by JPA callbacks rather than Spring Data auditing, because this
 * entity ships with the framework and cannot assume the application enabled auditing. Nothing
 * records who wrote the row: the first registration happens during setup, before an account
 * exists to attribute it to.
 */
@Entity
@Table(name = "license_registration")
@Comment("Single-row license registration and activation state")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LicenseRegistration extends SimpliXBaseEntity<String> {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID, generator = "uuid-v7")
    @UuidV7Generator
    @Column(name = "license_registration_id", nullable = false, unique = true, updatable = false)
    @Comment("Unique identifier for the registration row")
    private String licenseRegistrationId;

    @Column(name = "product_key", length = 128)
    @Convert(converter = AesEncryptionConverter.class)
    @Comment("Customer-facing product key (encrypted)")
    private String productKey;

    @Column(name = "license_token", length = 8192)
    @Convert(converter = AesEncryptionConverter.class)
    @Comment("Signed license token (encrypted)")
    private String licenseToken;

    @Column(name = "activation_id", length = 64)
    @Comment("Server-side activation identifier")
    private String activationId;

    @Column(name = "activation_secret", length = 256)
    @Convert(converter = AesEncryptionConverter.class)
    @Comment("Secret signing heartbeat and release requests (encrypted)")
    private String activationSecret;

    @Column(name = "instance_id", length = 64)
    @Comment("This deployment's stable identifier")
    private String instanceId;

    @Column(name = "machine_fingerprint", length = 128)
    @Comment("Machine fingerprint the token was activated for")
    private String machineFingerprint;

    @Column(name = "verification_watermark")
    @Comment("Latest instant verification succeeded at; never moves backwards")
    private Instant verificationWatermark;

    @Column(name = "last_heartbeat_at")
    @Comment("Latest instant a heartbeat succeeded at")
    private Instant lastHeartbeatAt;

    @Column(name = "revoked_at")
    @Comment("When the license server reported this activation revoked")
    private Instant revokedAt;

    @Column(name = "compromise_ceiling", length = 4096)
    @Comment("What was held when a compromised signing key was first learned of, as JSON")
    private String compromiseCeiling;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Comment("When the registration row was first written")
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    @Comment("When the registration row was last written")
    private Instant updatedAt;

    @Version
    @Column(name = "version")
    @Comment("Optimistic locking version")
    private Long version;

    @PrePersist
    void onInsert() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    @Override
    public String getId() {
        return getLicenseRegistrationId();
    }

    @Override
    public void setId(String id) {
        setLicenseRegistrationId(id);
    }
}
