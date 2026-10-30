package dev.simplecore.simplix.springboot.autoconfigure;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import org.hibernate.SessionFactory;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.cfg.Configuration;
import org.hibernate.type.SqlTypes;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Starts a Hibernate session factory on the starter's own classpath with no JSON format mapper
 * configured, so Hibernate discovers every Jackson module through the service loader. A Jackson
 * datatype module built for another Hibernate generation breaks that discovery and the factory
 * never starts.
 */
@DisplayName("Hibernate JSON format mapper discovery on the starter classpath")
class HibernateJsonFormatMapperDiscoveryTest {

    @Entity(name = "JsonDocument")
    public static class JsonDocument {
        @Id
        Long id;

        @JdbcTypeCode(SqlTypes.JSON)
        Map<String, Object> payload;
    }

    @Test
    @DisplayName("session factory starts and round-trips a JSON column")
    void sessionFactoryStartsWithDiscoveredJacksonModules() {
        Configuration configuration = new Configuration()
                .addAnnotatedClass(JsonDocument.class)
                .setProperty("hibernate.connection.url", "jdbc:h2:mem:json-format-mapper;DB_CLOSE_DELAY=-1")
                .setProperty("hibernate.connection.username", "sa")
                .setProperty("hibernate.hbm2ddl.auto", "create-drop");

        try (SessionFactory sessionFactory = configuration.buildSessionFactory()) {
            JsonDocument saved = new JsonDocument();
            saved.id = 1L;
            saved.payload = Map.of("title", "deck");
            sessionFactory.inTransaction(session -> session.persist(saved));

            JsonDocument loaded = sessionFactory.fromTransaction(session -> session.find(JsonDocument.class, 1L));
            assertThat(loaded.payload).containsEntry("title", "deck");
        }
    }
}
