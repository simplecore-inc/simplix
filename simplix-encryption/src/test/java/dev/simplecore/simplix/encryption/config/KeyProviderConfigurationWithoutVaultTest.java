package dev.simplecore.simplix.encryption.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The Vault configuration is only considered when Spring Vault is on the classpath.
 *
 * <p>Without the condition, an application that lacks Spring Vault fails to start under the prod
 * profile: the configuration class is enhanced before its bean conditions are read, and enhancing
 * it loads {@code VaultTemplate}. That failure needs a classpath without Spring Vault to
 * reproduce, which this module's tests do not have, so the condition itself is pinned here.
 */
class KeyProviderConfigurationWithoutVaultTest {

    @Test
    void the_vault_configuration_requires_spring_vault_on_the_classpath() {
        ConditionalOnClass condition =
                KeyProviderConfiguration.VaultKeyProviderConfig.class.getAnnotation(ConditionalOnClass.class);
        assertThat(condition).isNotNull();
        assertThat(condition.name()).contains("org.springframework.vault.core.VaultTemplate");
    }
}
