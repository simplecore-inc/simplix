package dev.simplecore.simplix.license.setup;

import dev.simplecore.simplix.license.setup.dto.EnvProfileDTOs.EncryptionKeyResult;
import dev.simplecore.simplix.license.setup.dto.EnvProfileDTOs.EnvSection;
import dev.simplecore.simplix.license.setup.dto.EnvProfileDTOs.EnvSetting;
import dev.simplecore.simplix.license.setup.dto.EnvProfileDTOs.ProfileDetail;
import dev.simplecore.simplix.license.setup.dto.EnvProfileDTOs.ProfileSummary;
import dev.simplecore.simplix.core.exception.ErrorCode;
import dev.simplecore.simplix.core.exception.SimpliXGeneralException;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads and writes the deployment env profiles that back the installer's
 * server-configuration step. Secrets are masked on read and applied only when a
 * new value is supplied on write; comments and file layout are preserved.
 *
 * <p>The env directory defaults to {@code env/} relative to the working directory
 * and is overridable via {@code PACS_ENV_DIR}. Env files are loaded at boot (by
 * the gradle launcher in development), so edits saved here take effect on the next
 * restart, not the running JVM. Callers gate these operations to the uninitialized
 * state; this service performs no initialization check of its own.
 */
@Service
public class EnvProfileService {

    private static final String ENV_DIR_KEY = "PACS_ENV_DIR";
    private static final String DEFAULT_ENV_DIR = "env";
    private static final String ENV_SUFFIX = ".env";
    private static final String SAMPLE_SUFFIX = ".env.sample";
    private static final int ENCRYPTION_KEY_BYTES = 32; // AES-256

    /** Substrings that mark a variable as secret; matched case-sensitively on the key. */
    private static final List<String> SECRET_PATTERNS = List.of(
            "PASSWORD", "SECRET", "TOKEN", "API_KEY", "ACCESS_KEY", "ENCRYPTION_KEY", "STATIC_KEY", "_PRIVATE");

    private final Environment environment;
    private final Path envDir;

    public EnvProfileService(Environment environment) {
        this.environment = environment;
        String configured = environment.getProperty(ENV_DIR_KEY);
        if (!StringUtils.hasText(configured)) {
            configured = System.getenv().getOrDefault(ENV_DIR_KEY, DEFAULT_ENV_DIR);
        }
        this.envDir = Path.of(configured);
    }

    /**
     * Lists the available env profiles (files ending in {@code .env}, excluding
     * {@code .sample} templates), marking the one the running JVM was launched with.
     *
     * @return the profile summaries, ordered by name
     */
    public List<ProfileSummary> listProfiles() {
        if (!Files.isDirectory(envDir)) {
            return List.of();
        }
        List<ProfileSummary> real = summariesFor(ENV_SUFFIX);
        if (!real.isEmpty()) {
            return real;
        }
        // Fresh deployment with no real .env yet: present the .env.sample templates
        // so the installer has something to fill in. The first save materializes the
        // real .env from the chosen template.
        return summariesFor(SAMPLE_SUFFIX);
    }

    private List<ProfileSummary> summariesFor(String suffix) {
        List<String> active = List.of(environment.getActiveProfiles());
        try (var stream = Files.list(envDir)) {
            return stream
                    .filter(Files::isRegularFile)
                    .map(p -> p.getFileName().toString())
                    .filter(n -> n.endsWith(suffix))
                    .map(n -> n.substring(0, n.length() - suffix.length()))
                    .sorted()
                    .map(name -> new ProfileSummary(name, active.contains(name)))
                    .toList();
        } catch (IOException e) {
            throw new SimpliXGeneralException(
                    ErrorCode.GEN_INTERNAL_SERVER_ERROR, "{error.system.envDirUnreadable}", null);
        }
    }

    /**
     * Reads one profile's settings grouped into sections, with secret values
     * withheld (reported only as configured / not configured).
     *
     * @param name the profile name
     * @return the grouped, masked settings
     * @throws SimpliXGeneralException GEN_NOT_FOUND when the profile file is absent
     */
    public ProfileDetail getProfile(String name) {
        Path file = resolveReadable(name);
        Map<String, String> values = readValues(file);

        LinkedHashMap<String, List<EnvSetting>> grouped = new LinkedHashMap<>();
        values.forEach((key, value) -> {
            if (isWithheld(key)) {
                return;
            }
            boolean secret = isSecret(key);
            EnvSetting setting = secret
                    ? new EnvSetting(key, null, true, StringUtils.hasText(value))
                    : new EnvSetting(key, value, false, null);
            grouped.computeIfAbsent(sectionCode(key), k -> new ArrayList<>()).add(setting);
        });

        List<EnvSection> sections = grouped.entrySet().stream()
                .map(e -> new EnvSection(e.getKey(), e.getValue()))
                .toList();

        boolean active = List.of(environment.getActiveProfiles()).contains(name);
        return new ProfileDetail(name, active, sections);
    }

    /**
     * Writes edits back to a profile, preserving comments and line order. Non-secret
     * keys are overwritten with the supplied value; secret keys are overwritten only
     * when a non-blank value is supplied (a blank or absent secret keeps the stored
     * value). Keys absent from the file are ignored.
     *
     * @param name the profile name
     * @param updates key to new-value edits
     * @throws SimpliXGeneralException GEN_NOT_FOUND when the profile file is absent
     */
    public void saveProfile(String name, Map<String, String> updates) {
        Path file = resolve(name);
        if (!Files.isRegularFile(file)) {
            // Materialize the real .env from its .sample template on first save so a
            // fresh deployment can start from the shipped template.
            Path sample = envDir.resolve(name + SAMPLE_SUFFIX);
            if (!Files.isRegularFile(sample)) {
                throw new SimpliXGeneralException(ErrorCode.GEN_NOT_FOUND, "{error.system.envProfileNotFound}", null);
            }
            try {
                Files.copy(sample, file);
            } catch (IOException e) {
                throw new SimpliXGeneralException(ErrorCode.GEN_INTERNAL_SERVER_ERROR, "{error.system.envProfileNotWritable}", null);
            }
        }
        if (updates == null || updates.isEmpty()) {
            return;
        }
        // Reject control characters so a value cannot smuggle extra lines
        // (KEY=value\nINJECTED=...) into the env file.
        for (String value : updates.values()) {
            if (value != null && (value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0)) {
                throw new SimpliXGeneralException(ErrorCode.GEN_BAD_REQUEST, "{error.system.envProfileValueInvalid}", null);
            }
        }

        List<String> lines;
        try {
            lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new SimpliXGeneralException(ErrorCode.GEN_INTERNAL_SERVER_ERROR, "{error.system.envProfileUnreadable}", null);
        }

        List<String> rewritten = new ArrayList<>(lines.size());
        for (String line : lines) {
            String key = keyOf(line);
            if (key != null && updates.containsKey(key) && !isWithheld(key)) {
                String newValue = updates.get(key);
                boolean skipSecret = isSecret(key) && !StringUtils.hasText(newValue);
                rewritten.add(skipSecret ? line : key + "=" + (newValue == null ? "" : newValue));
            } else {
                rewritten.add(line);
            }
        }

        try {
            Files.write(file, rewritten, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new SimpliXGeneralException(ErrorCode.GEN_INTERNAL_SERVER_ERROR, "{error.system.envProfileNotWritable}", null);
        }
    }

    /**
     * Generates a fresh AES-256 encryption key, base64-encoded. Callers restrict
     * this to the pre-initialization state so an existing dataset's key is never
     * rotated out from under its ciphertext.
     *
     * @return the generated key
     */
    public EncryptionKeyResult generateEncryptionKey() {
        byte[] key = new byte[ENCRYPTION_KEY_BYTES];
        new SecureRandom().nextBytes(key);
        return new EncryptionKeyResult(Base64.getEncoder().encodeToString(key));
    }

    // ---------------------------------------------------------------- internals

    private Path resolve(String name) {
        if (name == null || !name.matches("[a-zA-Z0-9_-]+")) {
            throw new SimpliXGeneralException(ErrorCode.GEN_BAD_REQUEST, "{error.system.envProfileNameInvalid}", null);
        }
        return envDir.resolve(name + ENV_SUFFIX);
    }

    /**
     * The readable path for a profile: the real {@code .env} if present, otherwise
     * its {@code .env.sample} template. Throws NOT_FOUND when neither exists.
     */
    private Path resolveReadable(String name) {
        Path env = resolve(name);
        if (Files.isRegularFile(env)) {
            return env;
        }
        Path sample = envDir.resolve(name + SAMPLE_SUFFIX);
        if (Files.isRegularFile(sample)) {
            return sample;
        }
        throw new SimpliXGeneralException(ErrorCode.GEN_NOT_FOUND, "{error.system.envProfileNotFound}", null);
    }

    private Map<String, String> readValues(Path file) {
        LinkedHashMap<String, String> values = new LinkedHashMap<>();
        try {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                String key = keyOf(line);
                if (key != null) {
                    values.put(key, valueOf(line));
                }
            }
        } catch (IOException e) {
            throw new SimpliXGeneralException(ErrorCode.GEN_INTERNAL_SERVER_ERROR, "{error.system.envProfileUnreadable}", null);
        }
        return values;
    }

    /** The variable name of a {@code KEY=VALUE} line, or null for comments and blanks. */
    private String keyOf(String line) {
        String trimmed = line.trim();
        if (trimmed.isEmpty() || trimmed.startsWith("#")) {
            return null;
        }
        int eq = trimmed.indexOf('=');
        if (eq <= 0) {
            return null;
        }
        return trimmed.substring(0, eq).trim();
    }

    /** The value of a {@code KEY=VALUE} line, surrounding quotes stripped. */
    private String valueOf(String line) {
        String trimmed = line.trim();
        int eq = trimmed.indexOf('=');
        String value = trimmed.substring(eq + 1).trim();
        if (value.length() >= 2
                && ((value.startsWith("\"") && value.endsWith("\"")) || (value.startsWith("'") && value.endsWith("'")))) {
            value = value.substring(1, value.length() - 1);
        }
        return value;
    }

    /**
     * Whether a key is kept out of the installer editor entirely.
     * <p>
     * License settings are withheld: the editor runs before setup with no authenticated
     * operator, so exposing them would let whoever reaches the installer point the
     * deployment at a different activation server or relocate its license state. Licensing
     * is managed after setup through the license screen, which requires a permission.
     *
     * @param key the environment key
     * @return whether the key is hidden from the editor
     */
    private boolean isWithheld(String key) {
        return key.startsWith("LICENSE");
    }

    private boolean isSecret(String key) {
        for (String pattern : SECRET_PATTERNS) {
            if (key.contains(pattern)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Classifies a key into a stable section code; the frontend localizes it.
     * Order matters: more specific substrings (ENCRYPTION, VAULT) are checked
     * before broader prefixes.
     */
    private String sectionCode(String key) {
        if (key.contains("ENCRYPTION")) return "ENCRYPTION";
        if (key.contains("VAULT")) return "VAULT";
        if (key.startsWith("DB_")) return "DATABASE";
        if (key.startsWith("S3_") || key.startsWith("FILE_") || key.contains("STORAGE") || key.contains("THUMBNAIL")) return "STORAGE";
        if (key.startsWith("EMAIL") || key.startsWith("RESEND")) return "EMAIL";
        if (key.startsWith("NATS") || key.startsWith("STREAM") || key.contains("MESSAGING")) return "MESSAGING";
        if (key.startsWith("GOOGLE")) return "OAUTH";
        if (key.startsWith("VISITOR")) return "VISITOR";
        if (key.startsWith("TRACING") || key.startsWith("ZIPKIN")) return "TRACING";
        if (key.startsWith("PACS_DASHBOARD")) return "DASHBOARD";
        if (key.startsWith("SERVER") || key.startsWith("SPRING") || key.startsWith("SESSION") || key.startsWith("CORS")) return "SERVER";
        return "OTHER";
    }
}
