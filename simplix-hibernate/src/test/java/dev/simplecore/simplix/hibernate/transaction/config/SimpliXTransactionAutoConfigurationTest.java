package dev.simplecore.simplix.hibernate.transaction.config;

import dev.simplecore.simplix.hibernate.transaction.SimpliXJpaTransactionManager;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(OutputCaptureExtension.class)
@DisplayName("SimpliXTransactionAutoConfiguration - registration and startup validation (R-1(f))")
class SimpliXTransactionAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    DataSourceAutoConfiguration.class,       // embedded H2 (test classpath)
                    HibernateJpaAutoConfiguration.class,
                    SimpliXTransactionAutoConfiguration.class));

    @Test
    @DisplayName("default: SimpliXJpaTransactionManager is the single active TM, INFO logged")
    void registersSimplixTransactionManagerByDefault(CapturedOutput output) {
        runner.run(context -> {
            assertThat(context).hasSingleBean(PlatformTransactionManager.class);
            assertThat(context.getBean(PlatformTransactionManager.class))
                    .isInstanceOf(SimpliXJpaTransactionManager.class);
            assertThat(output).contains("SimpliX transaction manager active");
        });
    }

    @Test
    @DisplayName("user-defined plain JpaTransactionManager: context starts, ERROR logged")
    void plainJpaTransactionManager_logsError(CapturedOutput output) {
        runner.withUserConfiguration(PlainJpaTransactionManagerConfig.class).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(PlatformTransactionManager.class))
                    .isNotInstanceOf(SimpliXJpaTransactionManager.class); // auto-config backed off
            assertThat(output).contains("BEFORE_COMMIT entity-event delivery is NOT guaranteed");
        });
    }

    @Test
    @DisplayName("fail-fast property: startup fails when the active TM is not SimpliX")
    void plainJpaTransactionManager_failFastFailsStartup() {
        runner.withUserConfiguration(PlainJpaTransactionManagerConfig.class)
                .withPropertyValues("simplix.transaction.fail-fast=true")
                .run(context -> assertThat(context).hasFailed());
    }

    @Configuration(proxyBeanMethods = false)
    static class PlainJpaTransactionManagerConfig {
        @Bean
        PlatformTransactionManager customTransactionManager(EntityManagerFactory entityManagerFactory) {
            return new JpaTransactionManager(entityManagerFactory);
        }
    }
}
