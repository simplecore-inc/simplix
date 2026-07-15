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
 * Entity whose payload construction always fails, configured with
 * {@code failOnError = false} so the publication failure must not abort the commit.
 */
@Entity
@Table(name = "best_effort_items")
@EntityListeners(EntityEventPublishingListener.class)
@EntityEventConfig(onCreate = "BEST_EFFORT_CREATED", failOnError = false)
public class BestEffortPayloadItem implements EntityEventPayloadProvider {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    private String name;

    protected BestEffortPayloadItem() {}

    public BestEffortPayloadItem(String name) {
        this.name = name;
    }

    @Override
    public Map<String, Object> getEventPayloadData() {
        throw new IllegalStateException("payload build failure (test)");
    }

    public String getId() { return id; }
}
