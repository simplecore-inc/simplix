package dev.simplecore.simplix.hibernate.transaction.config;

import dev.simplecore.simplix.hibernate.transaction.SimpliXJpaTransactionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.autoconfigure.transaction.TransactionManagerCustomizers;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionManager;

import java.util.List;

/**
 * Registers {@link SimpliXJpaTransactionManager} as the default JPA transaction manager
 * and validates at startup that it is the active one.
 * <p>
 * Runs BEFORE {@link HibernateJpaAutoConfiguration} so Spring Boot's default
 * {@code JpaTransactionManager} (guarded by {@code @ConditionalOnMissingBean}) backs off.
 * An application-defined transaction manager still wins; the startup validator then
 * reports it - ERROR log by default, startup failure when
 * {@code simplix.transaction.fail-fast=true}. Disable with
 * {@code simplix.transaction.enabled=false}.
 */
@AutoConfiguration(before = HibernateJpaAutoConfiguration.class)
@ConditionalOnClass(name = "org.springframework.orm.jpa.JpaTransactionManager")
@ConditionalOnProperty(prefix = "simplix.transaction", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class SimpliXTransactionAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(SimpliXTransactionAutoConfiguration.class);

    /** Property that escalates the startup validation ERROR into a startup failure. */
    public static final String FAIL_FAST_PROPERTY = "simplix.transaction.fail-fast";

    @Bean
    @ConditionalOnMissingBean(TransactionManager.class)
    public SimpliXJpaTransactionManager transactionManager(
            ObjectProvider<TransactionManagerCustomizers> transactionManagerCustomizers) {
        SimpliXJpaTransactionManager transactionManager = new SimpliXJpaTransactionManager();
        transactionManagerCustomizers.ifAvailable(customizers ->
                customizers.customize((TransactionManager) transactionManager));
        return transactionManager;
    }

    @Bean
    public SmartInitializingSingleton simplixTransactionManagerStartupValidator(
            ObjectProvider<PlatformTransactionManager> transactionManagers, Environment environment) {
        boolean failFast = environment.getProperty(FAIL_FAST_PROPERTY, Boolean.class, false);
        return () -> {
            List<PlatformTransactionManager> jpaManagers = transactionManagers.stream()
                    .filter(JpaTransactionManager.class::isInstance)
                    .toList();
            if (jpaManagers.isEmpty()) {
                return; // no JPA transaction manager in this application - nothing to validate
            }
            List<String> offending = jpaManagers.stream()
                    .filter(tm -> !(tm instanceof SimpliXJpaTransactionManager))
                    .map(tm -> tm.getClass().getName())
                    .toList();
            if (offending.isEmpty()) {
                log.info("SimpliX transaction manager active: {}", jpaManagers.get(0).getClass().getName());
                return;
            }
            String message = "JPA transaction manager(s) " + offending
                    + " are not SimpliXJpaTransactionManager - BEFORE_COMMIT entity-event delivery is"
                    + " NOT guaranteed (commit-time flush events are silently dropped)."
                    + " Replace them with SimpliXJpaTransactionManager."
                    + " Set " + FAIL_FAST_PROPERTY + "=true to fail startup on this condition.";
            if (failFast) {
                throw new IllegalStateException(message);
            }
            log.error(message);
        };
    }
}
