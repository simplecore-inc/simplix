package dev.simplecore.simplix.license.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.simplecore.simplix.license.activation.LicenseActivationClient;
import dev.simplecore.simplix.license.activation.LicenseActivationService;
import dev.simplecore.simplix.license.core.CompromiseCeilingStore;
import dev.simplecore.simplix.license.core.FileLicenseStore;
import dev.simplecore.simplix.license.core.LicenseAuditTrail;
import dev.simplecore.simplix.license.core.LicenseManager;
import dev.simplecore.simplix.license.enforcement.FeatureAccess;
import dev.simplecore.simplix.license.enforcement.FeatureGateAspect;
import dev.simplecore.simplix.license.enforcement.LicenseEnforcementFilter;
import dev.simplecore.simplix.license.enforcement.LicenseQuotaGuard;
import dev.simplecore.simplix.license.integrity.RuntimeIntegrityChecker;
import dev.simplecore.simplix.license.model.LicenseState;
import dev.simplecore.simplix.license.scheduler.LicenseHeartbeatScheduler;
import dev.simplecore.simplix.license.scheduler.LicenseReVerificationScheduler;
import dev.simplecore.simplix.license.controller.DeploymentLicenseRestController;
import dev.simplecore.simplix.license.enforcement.FeatureActivationAspect;
import dev.simplecore.simplix.license.setup.EnvProfileService;
import dev.simplecore.simplix.license.setup.EnvSetupController;
import dev.simplecore.simplix.license.setup.InitializationGateFilter;
import dev.simplecore.simplix.license.setup.LicenseSetupController;
import dev.simplecore.simplix.license.setup.SetupState;
import dev.simplecore.simplix.license.store.JpaLicenseStore;
import dev.accesscore.license.sdk.LicenseKeys;
import dev.accesscore.license.sdk.gate.LicenseGate;
import dev.accesscore.license.sdk.issuing.ProductKeys;
import dev.accesscore.license.sdk.model.LicenseModel.KeyMaterial;
import dev.accesscore.license.sdk.spi.LicenseSpi.ActivationServerDirectory;
import dev.accesscore.license.sdk.spi.LicenseSpi.AuditRecorder;
import dev.accesscore.license.sdk.spi.LicenseSpi.FeatureActivationChecker;
import dev.accesscore.license.sdk.spi.LicenseSpi.FeatureCatalogue;
import dev.accesscore.license.sdk.spi.LicenseSpi.LicenseStore;
import dev.accesscore.license.sdk.spi.LicenseSpi.ProductIdentity;
import dev.accesscore.license.sdk.spi.LicenseSpi.QuotaCounter;
import jakarta.persistence.EntityManager;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.ApplicationListener;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ImportRuntimeHints;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ResourceLoader;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;

/**
 * Assembles this deployment's licensing over the SDK.
 *
 * <p>Every bean here is named for where it comes from. A bean's name is its method's name, and
 * an autoconfiguration that called one {@code licenseRestController} would take that name away
 * from every application that has a controller of its own by that name — silently, because a
 * later definition wins and the application's own bean simply stops existing. Names carry the
 * {@code simplixLicense} prefix so a product can call its own beans whatever it likes.
 *
 * <p>The judgement itself is not switchable: the gate, the state, and the schedulers are always
 * in the context, and no configuration branch removes them. What a product does decide is where
 * the judgement is consulted. Most products answer "on every API request" and take the
 * request-level filter as it comes; one that sells licences of its own answers "only where I
 * would sell something", because its other paths are called by its customers' deployments and
 * cutting those off over its own unpaid invoice would take them down with it. That product sets
 * {@code application.license.enforcement.http-filter-enabled=false} and calls the gate itself.
 *
 * <p>The other optional piece is the durable store — when the application contributes a
 * {@link LicenseStore} that one is used, otherwise the file-backed store takes over.
 *
 * <p>Verification is kicked off once the application is ready rather than during bean creation,
 * so a database-backed store is fully available by then. Until it runs the state is "not
 * activated", which denies licensed features — the safe direction.
 *
 * <p>One thing the application must contribute: a {@link ProductIdentity}. Without it the
 * context does not start, because a deployment that does not know which product it is cannot
 * judge whether a license belongs to it, and defaulting that judgement would make every
 * product's license valid everywhere.
 */
@AutoConfiguration
@EnableConfigurationProperties(LicenseProperties.class)
@ImportRuntimeHints(LicenseRuntimeHints.class)
public class LicenseAutoConfiguration {

    /** Environment key holding the expected checksum of the deployed binary. */
    private static final String BINARY_HASH_PROPERTY = "APP_BINARY_HASH";

    /**
     * The verification keys this build carries, with their names and their standing.
     *
     * <p>Read from a fixed classpath location. A deployment that could point at keys of its own
     * could verify a license it minted itself, and at that moment the signature check defends
     * nothing. Several keys are carried so that replacing the issuer's signing key does not
     * refuse every license the previous one signed; which of them checks a given token is the
     * payload's name for it, and that choice is the SDK's.
     *
     * @param resourceLoader where the embedded resource is read from
     * @param objectMapper how the resource is read
     * @return every key this deployment accepts tokens under
     * @throws IOException when the embedded resource cannot be read
     */
    @Bean
    public VerificationKeys simplixLicenseVerificationKeyRegistry(ResourceLoader resourceLoader,
                                                                  ObjectMapper objectMapper)
            throws IOException {
        try (InputStream stream = resourceLoader
                .getResource(LicenseProperties.VERIFICATION_KEYS_LOCATION).getInputStream()) {
            return new VerificationKeys(objectMapper.readValue(stream.readAllBytes(),
                    new TypeReference<List<VerificationKey>>() {
                    }));
        }
    }

    /**
     * @param verificationKeys the keys this build carries
     * @return those keys in the form the SDK takes them
     */
    @Bean
    public List<KeyMaterial> simplixLicenseVerificationKeys(VerificationKeys verificationKeys) {
        return verificationKeys.materials();
    }

    /**
     * @return the gate every request-time check reads
     */
    @Bean
    public LicenseGate simplixLicenseGate(ObjectProvider<FeatureActivationChecker> activationChecker) {
        return new LicenseGate(activationChecker.getIfAvailable());
    }

    /**
     * @param gate the gate holding the judgement
     * @return this deployment's reading of what the core last concluded
     */
    @Bean
    public LicenseState simplixLicenseState(LicenseGate gate) {
        return new LicenseState(gate);
    }

    /**
     * @return the key format, shared with the issuing ledger so a support request matches
     */
    @Bean
    public ProductKeys simplixLicenseProductKeys() {
        return new ProductKeys();
    }

    /**
     * The database-backed store, preferred wherever JPA is on the classpath.
     *
     * <p>Declared ahead of the file-backed fallback so the fallback's missing-bean condition
     * sees it. A replaced container comes back licensed without a mounted volume, and the
     * secrets sit under the same encryption as the rest of the deployment's credentials.
     *
     * <p>Declared as the concrete class rather than as {@code LicenseStore}, because the same
     * object also keeps the fixed ceiling and a product injecting that has to be able to find
     * it. A product contributing a store of its own contributes both halves or the context does
     * not start — a deployment silently unable to keep a ceiling is worse than one that says so.
     *
     * @param objectMapper how the fixed ceiling is written into its column
     * @return the database-backed store
     */
    @Bean
    @ConditionalOnClass(EntityManager.class)
    @ConditionalOnMissingBean(LicenseStore.class)
    public JpaLicenseStore simplixLicenseJpaLicenseStore(ObjectMapper objectMapper) {
        return new JpaLicenseStore(objectMapper);
    }

    /**
     * @param properties where the store paths are configured
     * @param objectMapper how the state file is written
     * @return the file-backed store, used when neither JPA nor the application supplies one
     */
    @Bean
    @ConditionalOnMissingBean(LicenseStore.class)
    public FileLicenseStore simplixLicenseFileLicenseStore(LicenseProperties properties,
                                                           ObjectMapper objectMapper) {
        return new FileLicenseStore(
                Path.of(properties.getTokenPath()),
                Path.of(properties.getStatePath()),
                objectMapper);
    }

    /**
     * The application's own license screen.
     *
     * <p>Registered here rather than found by a component scan: an application scans its own
     * packages, and a scan declared from an auto-configuration does not reliably reach this
     * one. Every bean this module contributes is therefore named explicitly.
     *
     * @param licenseManager the source of the current judgement
     * @param activationService what claims and releases activations
     * @param featureAccess what the license grants
     * @param quotaGuard what the license caps
     * @param productKeys the key format
     * @param properties where the product key prefix is configured
     * @return the license screen
     */
    @Bean
    @ConditionalOnMissingBean(DeploymentLicenseRestController.class)
    public DeploymentLicenseRestController simplixDeploymentLicenseRestController(LicenseManager licenseManager,
                                                       LicenseActivationService activationService,
                                                       FeatureAccess featureAccess,
                                                       LicenseQuotaGuard quotaGuard,
                                                       ProductKeys productKeys,
                                                       LicenseProperties properties) {
        return new DeploymentLicenseRestController(licenseManager, activationService, featureAccess,
                quotaGuard, productKeys, properties);
    }

    /**
     * The installer's license step, reachable only before setup completes.
     *
     * @param licenseManager the source of the current judgement
     * @param activationService what claims activations
     * @param setupState what the application answers about setup
     * @param productKeys the key format
     * @param properties where the product key prefix is configured
     * @return the installer license path
     */
    @Bean
    @ConditionalOnBean(SetupState.class)
    @ConditionalOnMissingBean(LicenseSetupController.class)
    public LicenseSetupController simplixLicenseSetupController(LicenseManager licenseManager,
                                                         LicenseActivationService activationService,
                                                         SetupState setupState,
                                                         ProductKeys productKeys,
                                                         LicenseProperties properties) {
        return new LicenseSetupController(licenseManager, activationService, setupState,
                productKeys, properties);
    }

    /**
     * @param environment where the env directory is configured
     * @return the env-profile editor the installer uses
     */
    @Bean
    @ConditionalOnMissingBean(EnvProfileService.class)
    public EnvProfileService simplixLicenseEnvProfileService(Environment environment) {
        return new EnvProfileService(environment);
    }

    /**
     * @param envProfileService the env-profile editor
     * @param setupState what the application answers about setup
     * @return the installer env path
     */
    @Bean
    @ConditionalOnBean(SetupState.class)
    @ConditionalOnMissingBean(EnvSetupController.class)
    public EnvSetupController simplixLicenseEnvSetupController(EnvProfileService envProfileService,
                                                 SetupState setupState) {
        return new EnvSetupController(envProfileService, setupState);
    }

    /**
     * Blocks the API until setup completes.
     *
     * <p>Ordered ahead of the license filter: while a deployment is uninitialized there is no
     * license to judge, and the installer has to stay reachable.
     *
     * @param setupState what the application answers about setup
     * @param apiPrefix the versioned API prefix
     * @param setupToken the bootstrap token permitting remote setup, blank to require loopback
     * @return the registered setup gate
     */
    @Bean
    @ConditionalOnBean(SetupState.class)
    public FilterRegistrationBean<InitializationGateFilter> simplixLicenseInitializationGateFilter(
            SetupState setupState,
            @Value("${api.version.prefix:/api/v1}") String apiPrefix,
            @Value("${simplix.license.setup.token:}") String setupToken) {
        FilterRegistrationBean<InitializationGateFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new InitializationGateFilter(setupState, apiPrefix, setupToken));
        registration.addUrlPatterns("/*");
        registration.setName("initializationGateFilter");
        registration.setOrder(40);
        return registration;
    }

    /**
     * @param activationChecker where the application decides whether a feature is switched on
     * @return the aspect refusing a licensed but deactivated feature
     */
    @Bean
    @ConditionalOnBean(FeatureActivationChecker.class)
    @ConditionalOnMissingBean(FeatureActivationAspect.class)
    public FeatureActivationAspect simplixLicenseFeatureActivationAspect(FeatureActivationChecker activationChecker) {
        return new FeatureActivationAspect(activationChecker);
    }

    /**
     * @param environment where the expected binary checksum is configured
     * @return the binary checksum verifier
     */
    @Bean
    public RuntimeIntegrityChecker simplixLicenseRuntimeIntegrityChecker(Environment environment) {
        return new RuntimeIntegrityChecker(environment.getProperty(BINARY_HASH_PROPERTY));
    }

    /**
     * The way in to the audit trail. The recorder itself is contributed by the application, so
     * the provider stays unresolved in a deployment that contributes none.
     *
     * @param recorder the application-contributed recorder, if any
     * @return the audit trail
     */
    @Bean
    public LicenseAuditTrail simplixLicenseAuditTrail(ObjectProvider<AuditRecorder> recorder) {
        return new LicenseAuditTrail(recorder);
    }

    /**
     * The SDK manager: the store, the keys, the counters, and the judgement.
     *
     * @param gate the gate the judgement is published to
     * @param store where the registration is persisted
     * @param identity which product this deployment is
     * @param keys the verification keys this build carries
     * @param catalogue the feature keys this deployment gates on
     * @param counters the application-contributed quota counters
     * @param directory where the activation server address is kept
     * @param properties the license configuration
     * @return the SDK manager
     */
    @Bean
    // Fully qualified: LicenseManager stands for dev.simplecore.simplix.license.core.LicenseManager in this file.
    public dev.accesscore.license.sdk.LicenseManager sdkLicenseManager(
            LicenseGate gate,
            LicenseStore store,
            ProductIdentity identity,
            List<KeyMaterial> keys,
            ObjectProvider<FeatureCatalogue> catalogue,
            ObjectProvider<QuotaCounter> counters,
            ObjectProvider<ActivationServerDirectory> directory,
            LicenseProperties properties) {
        dev.accesscore.license.sdk.LicenseManager.Builder builder =
                dev.accesscore.license.sdk.LicenseManager.builder(store, identity, keys)
                        .gate(gate)
                        .counters(counters.orderedStream().toList())
                        .configuredServerUrl(properties.getActivation().getServerUrl());
        FeatureCatalogue features = catalogue.getIfAvailable();
        if (features != null) {
            builder.catalogue(features);
        }
        ActivationServerDirectory registry = directory.getIfAvailable();
        if (registry != null) {
            builder.directory(registry);
        }
        return builder.build();
    }

    /**
     * @param properties the license configuration
     * @param sdk the SDK manager
     * @param integrityChecker the binary checksum verifier
     * @param state the shared runtime state every gate reads
     * @param auditTrail where status transitions are recorded
     * @param verificationKeys the keys this build carries, and which of them are compromised
     * @param ceilingStore where the fixed ceiling is kept
     * @param keys the verification keys this build carries
     * @return the orchestrator every enforcement point reads through
     */
    @Bean
    public LicenseManager simplixLicenseManager(LicenseProperties properties,
                                         dev.accesscore.license.sdk.LicenseManager sdk,
                                         RuntimeIntegrityChecker integrityChecker,
                                         LicenseState state,
                                         LicenseAuditTrail auditTrail,
                                         VerificationKeys verificationKeys,
                                         CompromiseCeilingStore ceilingStore,
                                         List<KeyMaterial> keys) {
        return new LicenseManager(properties, sdk, integrityChecker, state, auditTrail,
                verificationKeys, ceilingStore, fingerprintOf(keys));
    }

    /**
     * Runs the first judgement once the context is up, so a database-backed store is readable
     * by the time the license is loaded.
     *
     * @param licenseManager the orchestrator to initialize
     * @return the startup listener
     */
    @Bean
    public ApplicationListener<ApplicationReadyEvent> simplixLicenseInitializer(
            LicenseManager licenseManager) {
        return event -> licenseManager.initialize();
    }

    /**
     * @param properties the license configuration
     * @param objectMapper the mapper the envelope is read with
     * @param directory where the activation server address is kept
     * @param sdk the SDK manager that writes the bodies and reads the answers
     * @return the activation transport
     */
    @Bean
    public LicenseActivationClient simplixLicenseActivationClient(
            LicenseProperties properties,
            ObjectMapper objectMapper,
            ObjectProvider<ActivationServerDirectory> directory,
            ObjectProvider<ContactIdentity> contactIdentity,
            dev.accesscore.license.sdk.LicenseManager sdk) {
        return new LicenseActivationClient(properties.getActivation().getServerUrl(),
                objectMapper, directory, contactIdentity, sdk);
    }

    /**
     * @param client the activation transport
     * @param licenseManager the orchestrator holding the registration
     * @param productKeys the key format
     * @param properties the license configuration
     * @param auditTrail where registration lifecycle events are recorded
     * @param sdk the SDK manager
     * @return what drives registration
     */
    @Bean
    public LicenseActivationService simplixLicenseActivationService(
            LicenseActivationClient client,
            LicenseManager licenseManager,
            ProductKeys productKeys,
            LicenseProperties properties,
            LicenseAuditTrail auditTrail,
            dev.accesscore.license.sdk.LicenseManager sdk) {
        return new LicenseActivationService(client, licenseManager, productKeys,
                properties.getProductKeyPrefix(), auditTrail, sdk);
    }

    /**
     * @param gate the gate every enforcement point reads
     * @return the single place a feature's availability is judged
     */
    @Bean
    public FeatureAccess simplixLicenseFeatureAccess(LicenseGate gate) {
        return new FeatureAccess(gate);
    }

    /**
     * @param gate the source of the current ceilings
     * @param counters the application-contributed counters
     * @param auditTrail where refusals are recorded
     * @return the ceiling guard
     */
    @Bean
    public LicenseQuotaGuard simplixLicenseQuotaGuard(LicenseGate gate,
                                               ObjectProvider<QuotaCounter> counters,
                                               LicenseAuditTrail auditTrail) {
        return new LicenseQuotaGuard(gate, counters.orderedStream().toList(), auditTrail);
    }

    /**
     * @param gate the gate every enforcement point reads
     * @return the aspect enforcing the feature annotation
     */
    @Bean
    public FeatureGateAspect simplixLicenseFeatureGateAspect(LicenseGate gate) {
        return new FeatureGateAspect(gate);
    }

    /**
     * Refuses every API request while the licence does not permit one.
     *
     * <p>Switched off by a product that consults the gate at named points instead. Turning it
     * off does not weaken the judgement — it moves where the judgement is asked.
     *
     * @param licenseManager the source of the current judgement
     * @param objectMapper how the refusal envelope is written
     * @param messageSource where the message key is translated
     * @param environment where the API version prefix is configured
     * @return the request-level enforcement filter
     */
    @Bean
    @ConditionalOnProperty(name = "application.license.enforcement.http-filter-enabled",
            havingValue = "true", matchIfMissing = true)
    public FilterRegistrationBean<LicenseEnforcementFilter> simplixLicenseEnforcementFilter(
            LicenseManager licenseManager,
            ObjectMapper objectMapper,
            MessageSource messageSource,
            Environment environment) {
        String apiVersionPrefix = environment.getProperty("api.version.prefix", "/api/v1");
        FilterRegistrationBean<LicenseEnforcementFilter> registration =
                new FilterRegistrationBean<>();
        registration.setFilter(new LicenseEnforcementFilter(
                licenseManager, objectMapper, messageSource, apiVersionPrefix));
        registration.addUrlPatterns("/api/*");
        registration.setName("licenseEnforcementFilter");
        registration.setOrder(50);
        return registration;
    }

    /**
     * @param licenseManager what judges the registration
     * @return the periodic re-verification
     */
    @Bean
    public LicenseReVerificationScheduler simplixLicenseReVerificationScheduler(
            LicenseManager licenseManager) {
        return new LicenseReVerificationScheduler(licenseManager);
    }

    /**
     * @param activationService what refreshes the token
     * @param licenseManager the source of the current judgement
     * @param properties the license configuration
     * @return the heartbeat schedule
     */
    @Bean
    public LicenseHeartbeatScheduler simplixLicenseHeartbeatScheduler(
            LicenseActivationService activationService,
            LicenseManager licenseManager,
            LicenseProperties properties) {
        return new LicenseHeartbeatScheduler(activationService, licenseManager, properties);
    }

    /**
     * The fingerprint of the key this build verifies with, for the line the deployment logs at
     * startup and the value its license status reports.
     *
     * <p>An operator compares it against the one the issuing server logs for its signing key.
     * Two different values mean every license that server issues would be refused here as
     * unverifiable — a mismatch worth finding at startup rather than at a first activation.
     *
     * @param keys the verification keys this build carries
     * @return the fingerprint of the first, or an empty string when none could be read
     */
    private static String fingerprintOf(List<KeyMaterial> keys) {
        if (keys.isEmpty()) {
            return "";
        }
        return new LicenseKeys().fingerprintOf(keys.get(0).publicKey());
    }
}
