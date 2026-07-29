package dev.simplecore.simplix.license.core;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.accesscore.license.sdk.model.LicenseModel.RegistrationRecord;
import dev.accesscore.license.sdk.spi.LicenseSpi.LicenseStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;

/**
 * File-backed {@link LicenseStore} used when the application contributes no durable store.
 *
 * <p>Two files are involved. The token file holds the signed license and can be placed by hand,
 * which is how a development deployment and a closed-network installation are provisioned. The
 * state file holds the mutable local state — activation credentials, the verification
 * watermark, the last heartbeat — and is written by the application.
 *
 * <p>Both files are created with owner-only permissions where the filesystem supports it.
 */
public class FileLicenseStore implements LicenseStore {

    private static final Logger log = LoggerFactory.getLogger(FileLicenseStore.class);

    private static final Set<PosixFilePermission> OWNER_ONLY = Set.of(
            PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);

    private final Path tokenPath;
    private final Path statePath;
    private final ObjectMapper objectMapper;

    /**
     * @param tokenPath where the signed token is read from and written to
     * @param statePath where the mutable local state is kept
     * @param objectMapper how the state file is written
     */
    public FileLicenseStore(Path tokenPath, Path statePath, ObjectMapper objectMapper) {
        this.tokenPath = tokenPath;
        this.statePath = statePath;
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<RegistrationRecord> load() {
        String token = readToken().orElse(null);
        State state = readState().orElse(null);

        if (token == null && state == null) {
            return Optional.empty();
        }
        if (state == null) {
            return Optional.of(RegistrationRecord.ofToken(token));
        }
        return Optional.of(new RegistrationRecord(
                state.productKey(),
                token,
                state.activationId(),
                state.activationSecret(),
                state.instanceId(),
                state.verificationWatermark(),
                state.lastHeartbeatAt(),
                state.revokedAt()));
    }

    @Override
    public void save(RegistrationRecord record) {
        if (record.hasToken()) {
            write(tokenPath, record.licenseToken());
        }
        State state = new State(
                record.productKey(),
                record.activationId(),
                record.activationSecret(),
                record.instanceId(),
                record.verificationWatermark(),
                record.lastHeartbeatAt(),
                record.revokedAt());
        try {
            write(statePath, objectMapper.writeValueAsString(state));
        } catch (IOException e) {
            log.error("Failed to serialize license state to {}", statePath, e);
        }
    }

    @Override
    public void clear() {
        deleteQuietly(tokenPath);
        deleteQuietly(statePath);
    }

    /**
     * @return the token file's contents, or empty when the file is absent or unreadable
     */
    private Optional<String> readToken() {
        if (!Files.exists(tokenPath)) {
            return Optional.empty();
        }
        try {
            String token = Files.readString(tokenPath, StandardCharsets.UTF_8).trim();
            return token.isEmpty() ? Optional.empty() : Optional.of(token);
        } catch (IOException e) {
            log.error("Failed to read the license token at {}", tokenPath, e);
            return Optional.empty();
        }
    }

    /**
     * @return the state file's contents, or empty when the file is absent or unreadable
     */
    private Optional<State> readState() {
        if (!Files.exists(statePath)) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(Files.readString(statePath,
                    StandardCharsets.UTF_8), State.class));
        } catch (IOException e) {
            log.error("Failed to read the license state at {}", statePath, e);
            return Optional.empty();
        }
    }

    /**
     * @param path the file to write
     * @param content what to write into it
     */
    private void write(Path path, String content) {
        try {
            Path parent = path.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(path, content, StandardCharsets.UTF_8);
            restrictToOwner(path);
        } catch (IOException e) {
            log.error("Failed to write {}", path, e);
        }
    }

    /**
     * @param path the file to remove
     */
    private void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            log.error("Failed to delete {}", path, e);
        }
    }

    /**
     * @param path the file to restrict
     */
    private static void restrictToOwner(Path path) throws IOException {
        if (!path.getFileSystem().supportedFileAttributeViews().contains("posix")) {
            return;
        }
        Files.setPosixFilePermissions(path, OWNER_ONLY);
    }

    /**
     * The mutable half of a registration, as the state file holds it.
     *
     * <p>The token is not here: it lives in its own file so an operator can drop one in by
     * hand, which is the whole provisioning story for a closed network.
     *
     * @param productKey the customer-facing key the activation was obtained with
     * @param activationId the server-side activation identifier
     * @param activationSecret the secret heartbeat and release requests are signed with
     * @param instanceId this deployment's stable identifier
     * @param verificationWatermark the latest instant a judgement succeeded at
     * @param lastHeartbeatAt the latest instant a heartbeat succeeded at
     * @param revokedAt when the license server reported this activation revoked
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record State(
            String productKey,
            String activationId,
            String activationSecret,
            String instanceId,
            Instant verificationWatermark,
            Instant lastHeartbeatAt,
            Instant revokedAt
    ) {
    }
}
