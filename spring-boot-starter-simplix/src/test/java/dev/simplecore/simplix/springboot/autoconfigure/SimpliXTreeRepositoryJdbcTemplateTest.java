package dev.simplecore.simplix.springboot.autoconfigure;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.transaction.autoconfigure.TransactionAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The {@link JdbcTemplate} the tree repository configuration provides runs on the application's
 * {@link DataSource} bean: the pool the JPA transaction manager binds, closed with the context.
 */
class SimpliXTreeRepositoryJdbcTemplateTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(DataSourceAutoConfiguration.class,
                    HibernateJpaAutoConfiguration.class, TransactionAutoConfiguration.class,
                    SimpliXTreeRepositoryAutoConfiguration.class))
            .withPropertyValues("spring.datasource.url=jdbc:h2:mem:jdbc-template-" + UUID.randomUUID()
                    + ";DB_CLOSE_DELAY=-1");

    @Test
    void the_jdbc_template_runs_on_the_data_source_the_jpa_transaction_manager_binds() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(DataSource.class).hasSingleBean(JdbcTemplate.class);
            DataSource dataSource = context.getBean(DataSource.class);
            PlatformTransactionManager transactions = context.getBean(PlatformTransactionManager.class);

            assertThat(context.getBean(JdbcTemplate.class).getDataSource()).isSameAs(dataSource);
            assertThat(transactions).isInstanceOf(JpaTransactionManager.class);
            assertThat(((JpaTransactionManager) transactions).getDataSource()).isSameAs(dataSource);
        });
    }

    @Test
    void a_jdbc_template_write_rolls_back_with_the_transaction_it_runs_in() {
        runner.run(context -> {
            JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
            jdbc.execute("create table probe (id varchar(36) primary key)");

            new TransactionTemplate(context.getBean(PlatformTransactionManager.class)).executeWithoutResult(status -> {
                jdbc.update("insert into probe (id) values (?)", "rolled-back");
                status.setRollbackOnly();
            });

            assertThat(jdbc.queryForObject("select count(*) from probe", Integer.class)).isZero();
        });
    }

    @Test
    void closing_the_context_closes_the_pool_the_jdbc_template_used() {
        AtomicReference<DataSource> used = new AtomicReference<>();
        runner.run(context -> {
            JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
            assertThat(jdbc.queryForObject("select 1", Integer.class)).isEqualTo(1);
            used.set(jdbc.getDataSource());
        });

        assertThat(used.get()).isInstanceOf(HikariDataSource.class);
        assertThat(((HikariDataSource) used.get()).isClosed()).isTrue();
    }

    @Test
    void with_several_data_sources_the_jdbc_template_runs_on_the_primary_one() {
        runner.withUserConfiguration(TwoDataSources.class).run(context -> {
            assertThat(context.getBean(JdbcTemplate.class).getDataSource()).isSameAs(context.getBean("main"));
        });
    }

    @Test
    void with_several_data_sources_and_no_primary_the_application_declares_its_own_jdbc_template() {
        runner.withUserConfiguration(TwoUnrankedDataSources.class).run(context ->
                assertThat(context).doesNotHaveBean(JdbcTemplate.class));
    }

    @Configuration(proxyBeanMethods = false)
    static class TwoDataSources {

        @Bean
        @Primary
        HikariDataSource main() {
            return pool("main");
        }

        @Bean
        HikariDataSource reports() {
            return pool("reports");
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class TwoUnrankedDataSources {

        @Bean
        HikariDataSource first() {
            return pool("first");
        }

        @Bean
        HikariDataSource second() {
            return pool("second");
        }
    }

    private static HikariDataSource pool(String name) {
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setJdbcUrl("jdbc:h2:mem:" + name + "-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        return dataSource;
    }
}
