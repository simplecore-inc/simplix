package dev.simplecore.simplix.hibernate.transaction.it;

import dev.simplecore.simplix.core.entity.EntityEventPayloadProvider;
import dev.simplecore.simplix.core.entity.annotation.EntityEventConfig;
import dev.simplecore.simplix.hibernate.event.EntityEventPublishingListener;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.Map;

/**
 * Entity whose payload construction always fails, using the default
 * {@code failOnError = true} so the publication failure must abort the commit.
 */
@Entity
@Table(name = "strict_items")
@EntityListeners(EntityEventPublishingListener.class)
@EntityEventConfig(onCreate = "STRICT_CREATED")
public class StrictPayloadItem implements EntityEventPayloadProvider {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    private String name;

    protected StrictPayloadItem() {}

    public StrictPayloadItem(String name) {
        this.name = name;
    }

    @Override
    public Map<String, Object> getEventPayloadData() {
        throw new IllegalStateException("payload build failure (test)");
    }

    public String getId() { return id; }
}
