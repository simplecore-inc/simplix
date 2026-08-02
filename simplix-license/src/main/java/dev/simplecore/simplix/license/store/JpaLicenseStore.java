package dev.simplecore.simplix.license.store;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.simplecore.simplix.license.core.CompromiseCeiling;
import dev.simplecore.simplix.license.core.CompromiseCeilingStore;
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
public class JpaLicenseStore implements LicenseStore, CompromiseCeilingStore {

    private static final String SELECT_SINGLETON =
            "SELECT r FROM LicenseRegistration r ORDER BY r.licenseRegistrationId ASC";

    @PersistenceContext
    private EntityManager entityManager;

    private final ObjectMapper objectMapper;

    /**
     * @param objectMapper how the fixed ceiling is written into its column and read back
     */
    public JpaLicenseStore(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<RegistrationRecord> load() {
        return findRow().filter(JpaLicenseStore::holdsRegistration).map(this::toRecord);
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
        persist(row);
    }

    @Override
    @Transactional
    public void clear() {
        findRow().ifPresent(entityManager::remove);
    }

    @Override
    public Optional<CompromiseCeiling> loadCeiling() {
        return findRow()
                .map(LicenseRegistration::getCompromiseCeiling)
                .flatMap(this::readCeiling);
    }

    @Override
    @Transactional
    public void saveCeiling(CompromiseCeiling ceiling) {
        LicenseRegistration row = findRow().orElseGet(LicenseRegistration::new);
        row.setCompromiseCeiling(writeCeiling(ceiling));
        persist(row);
    }

    /**
     * Whether the singleton row carries a registration rather than only the fixed ceiling.
     *
     * <p>The ceiling is fixed before anything is registered on a deployment that carries a
     * compromised key, and it is kept in this same row. Reported as a registration, that row
     * would answer every "is anything registered here" with yes — down to offering to release a
     * seat this deployment never took.
     *
     * @param row the singleton row
     * @return whether anything of the registration itself is stored in it
     */
    private static boolean holdsRegistration(LicenseRegistration row) {
        return row.getProductKey() != null
                || row.getLicenseToken() != null
                || row.getActivationId() != null
                || row.getActivationSecret() != null
                || row.getInstanceId() != null
                || row.getVerificationWatermark() != null
                || row.getLastHeartbeatAt() != null
                || row.getRevokedAt() != null;
    }

    /**
     * @param row the singleton row, new or loaded
     */
    private void persist(LicenseRegistration row) {
        if (row.getLicenseRegistrationId() == null) {
            entityManager.persist(row);
        } else {
            entityManager.merge(row);
        }
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

    /**
     * The fixed ceiling as the column holds it.
     *
     * <p>A column that cannot be read stops the deployment rather than answering "no ceiling
     * fixed". Answering that would let the next judgement fix a fresh ceiling from whatever
     * token is stored, which is precisely what an unreadable column would be worth arranging.
     *
     * @param stored the column's contents
     * @return the ceiling it holds, empty when the column is unset
     * @throws IllegalStateException when the column holds something that is not a ceiling
     */
    private Optional<CompromiseCeiling> readCeiling(String stored) {
        if (stored == null || stored.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(stored, CompromiseCeiling.class));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(
                    "The fixed license ceiling in the registration row cannot be read", e);
        }
    }

    /**
     * @param ceiling the ceiling to persist
     * @return its JSON form
     * @throws IllegalStateException when it cannot be written
     */
    private String writeCeiling(CompromiseCeiling ceiling) {
        try {
            return objectMapper.writeValueAsString(ceiling);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(
                    "The fixed license ceiling cannot be written into the registration row", e);
        }
    }
}
