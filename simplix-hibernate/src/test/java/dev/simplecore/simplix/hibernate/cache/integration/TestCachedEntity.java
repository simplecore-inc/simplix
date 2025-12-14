package dev.simplecore.simplix.hibernate.cache.integration;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

/**
 * Test entity with Hibernate L2 cache enabled.
 * Used for integration testing @EvictCache functionality.
 */
@Entity
@Table(name = "test_cached_entity")
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
public class TestCachedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    private String status;

    public TestCachedEntity() {
    }

    public TestCachedEntity(Long id, String name, String status) {
        this.id = id;
        this.name = name;
        this.status = status;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    /**
     * Builder pattern for creating test entities.
     */
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String name;
        private String status;

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder status(String status) {
            this.status = status;
            return this;
        }

        public TestCachedEntity build() {
            TestCachedEntity entity = new TestCachedEntity();
            entity.setName(name);
            entity.setStatus(status);
            return entity;
        }
    }
}
