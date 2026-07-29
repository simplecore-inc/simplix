package dev.simplecore.simplix.license.store;

import dev.accesscore.license.sdk.model.LicenseModel.RegistrationRecord;
import dev.accesscore.license.sdk.spi.LicenseSpi.LicenseStore;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Database-backed {@link LicenseStore}.
 * <p>
 * Keeping the registration in the database rather than on disk means a replaced container
 * comes back licensed without a mounted volume, and the secrets sit under the same encryption
 * as the rest of the deployment's credentials.
 * <p>
 * Queries run through the {@link EntityManager} rather than a Spring Data repository on
 * purpose. A repository interface has to be found by a scan, and declaring
 * {@code @EnableJpaRepositories} from a framework module switches off the application's own
 * repository auto-configuration. One entity holding one row does not need the indirection.
 */
@Transactional(readOnly = true)
public class JpaLicenseStore implements LicenseStore {

    private static final String SELECT_SINGLETON =
            "SELECT r FROM LicenseRegistration r ORDER BY r.licenseRegistrationId ASC";

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public Optional<RegistrationRecord> load() {
        return findRow().map(this::toRecord);
    }

    @Override
    @Transactional
    public void save(RegistrationRecord record) {
        LicenseRegistration row = findRow().orElseGet(LicenseRegistration::new);
        row.setProductKey(record.productKey());
        row.setLicenseToken(record.licenseToken());
        row.setActivationId(record.activationId());
        row.setActivationSecret(record.activationSecret());
        row.setInstanceId(record.instanceId());
        row.setVerificationWatermark(record.verificationWatermark());
        row.setLastHeartbeatAt(record.lastHeartbeatAt());
        row.setRevokedAt(record.revokedAt());
        if (row.getLicenseRegistrationId() == null) {
            entityManager.persist(row);
        } else {
            entityManager.merge(row);
        }
    }

    @Override
    @Transactional
    public void clear() {
        findRow().ifPresent(entityManager::remove);
    }

    /**
     * @return the singleton registration row, if it exists
     */
    private Optional<LicenseRegistration> findRow() {
        return entityManager.createQuery(SELECT_SINGLETON, LicenseRegistration.class)
                .setMaxResults(1)
                .getResultStream()
                .findFirst();
    }

    private RegistrationRecord toRecord(LicenseRegistration row) {
        return new RegistrationRecord(
                row.getProductKey(),
                row.getLicenseToken(),
                row.getActivationId(),
                row.getActivationSecret(),
                row.getInstanceId(),
                row.getVerificationWatermark(),
                row.getLastHeartbeatAt(),
                row.getRevokedAt());
    }
}
