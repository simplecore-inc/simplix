package dev.simplecore.simplix.demo.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Primary;
import org.springframework.core.annotation.Order;

import javax.sql.DataSource;

@Configuration
@ConditionalOnProperty(prefix = "spring.liquibase", name = "enabled", havingValue = "false", matchIfMissing = false)
@DependsOn("entityManagerFactory")
public class LiquibaseConfiguration {

    @Value("${spring.liquibase.change-log:classpath:/db/changelog/db.changelog-master.yaml}")
    private String changeLog;

    @Value("${spring.liquibase.default-schema:public}")
    private String defaultSchema;

    @Value("${spring.liquibase.contexts:dev}")
    private String contexts;

    @Value("${spring.liquibase.drop-first:false}")
    private boolean dropFirst;

    @Bean
    @Primary
    @Order(-20)
    public Object liquibase(DataSource dataSource) {
        try {
            // Dynamically load Liquibase classes to avoid class loading issues
            Class<?> springLiquibaseClass = Class.forName("liquibase.integration.spring.SpringLiquibase");
            Object liquibase = springLiquibaseClass.getDeclaredConstructor().newInstance();

            // Set properties using reflection
            springLiquibaseClass.getMethod("setDataSource", javax.sql.DataSource.class).invoke(liquibase, dataSource);
            springLiquibaseClass.getMethod("setChangeLog", String.class).invoke(liquibase, changeLog);
            springLiquibaseClass.getMethod("setDefaultSchema", String.class).invoke(liquibase, defaultSchema);
            springLiquibaseClass.getMethod("setContexts", String.class).invoke(liquibase, contexts);
            springLiquibaseClass.getMethod("setDropFirst", boolean.class).invoke(liquibase, dropFirst);
            springLiquibaseClass.getMethod("setShouldRun", boolean.class).invoke(liquibase, true);

            // Set classpath resource loader
            Class<?> resourceLoaderClass = Class.forName("org.springframework.core.io.DefaultResourceLoader");
            Object resourceLoader = resourceLoaderClass.getDeclaredConstructor().newInstance();
            springLiquibaseClass.getMethod("setResourceLoader", Class.forName("org.springframework.core.io.ResourceLoader"))
                .invoke(liquibase, resourceLoader);

            return liquibase;
        } catch (Exception e) {
            throw new RuntimeException("Failed to create Liquibase bean", e);
        }
    }
}
