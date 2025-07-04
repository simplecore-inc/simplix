package dev.simplecore.simplix.demo.config;

import liquibase.integration.spring.SpringLiquibase;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.liquibase.LiquibaseAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;

import javax.sql.DataSource;

@Configuration
@EnableAutoConfiguration(exclude = LiquibaseAutoConfiguration.class)
@Profile("dev")
public class LiquibaseConfiguration {
    
    private final DataSource dataSource;

    public LiquibaseConfiguration(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @EventListener(ContextRefreshedEvent.class)
    public void onApplicationEvent() {
        SpringLiquibase liquibase = new SpringLiquibase();
        liquibase.setDataSource(dataSource);
        liquibase.setChangeLog("classpath:/db/changelog/db.changelog-master.yaml");
        liquibase.setContexts("development");
        liquibase.setDefaultSchema("public");
        liquibase.setDropFirst(false);
        liquibase.setShouldRun(true);
        try {
            liquibase.afterPropertiesSet();
        } catch (Exception e) {
            throw new RuntimeException("Liquibase initialization failed", e);
        }
    }
}