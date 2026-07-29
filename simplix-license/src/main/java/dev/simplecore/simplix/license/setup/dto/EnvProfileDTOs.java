package dev.simplecore.simplix.license.setup.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.util.Map;

/**
 * DTOs for the first-run installer's server-configuration step: listing env
 * profiles, reading a profile's grouped settings (secrets masked), saving edits,
 * and generating an encryption key.
 */
public class EnvProfileDTOs {

    /**
     * One selectable env profile. {@code active} marks the profile the running
     * JVM was launched with, so the UI can show which file is currently in effect.
     */
    @Getter
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ProfileSummary {
        private String name;
        private Boolean active;
    }

    /**
     * A single environment variable. For secret keys the {@code value} is withheld
     * (null) and {@code configured} reports whether a non-blank value already
     * exists; non-secret keys carry their real value and {@code configured} is null.
     */
    @Getter
    @AllArgsConstructor
    @NoArgsConstructor
    public static class EnvSetting {
        private String key;
        private String value;
        private Boolean secret;
        private Boolean configured;
    }

    /**
     * A UI grouping of related settings. {@code code} is a stable identifier
     * (DATABASE, EMAIL, STORAGE, …); the frontend maps it to a localized label.
     */
    @Getter
    @AllArgsConstructor
    @NoArgsConstructor
    public static class EnvSection {
        private String code;
        private List<EnvSetting> settings;
    }

    /** A profile's full, grouped settings with secrets masked. */
    @Getter
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ProfileDetail {
        private String name;
        private Boolean active;
        private List<EnvSection> sections;
    }

    /**
     * Edit submission: a key to new-value map. Secret keys are applied only when
     * present here (a blank or absent secret leaves the stored value untouched);
     * non-secret keys are written as given.
     */
    @Getter
    @Setter
    @NoArgsConstructor
    public static class ProfileUpdate {
        private Map<String, String> settings;
    }

    /** The freshly generated encryption key, base64-encoded (AES-256). */
    @Getter
    @AllArgsConstructor
    @NoArgsConstructor
    public static class EncryptionKeyResult {
        private String key;
    }
}
