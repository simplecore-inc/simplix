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

import java.util.HashMap;
import java.util.Map;

@Entity
@Table(name = "hard_delete_items")
@EntityListeners(EntityEventPublishingListener.class)
@EntityEventConfig(onCreate = "HARD_ITEM_CREATED", onUpdate = "HARD_ITEM_UPDATED", onDelete = "HARD_ITEM_DELETED")
public class HardDeleteItem implements EntityEventPayloadProvider {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    private String name;

    private String ownerId;

    protected HardDeleteItem() {}

    public HardDeleteItem(String name, String ownerId) {
        this.name = name;
        this.ownerId = ownerId;
    }

    @Override
    public Map<String, Object> getEventPayloadData() {
        Map<String, Object> data = new HashMap<>();
        data.put("ownerId", ownerId);
        return data;
    }

    public String getId() { return id; }

    public String getName() { return name; }

    public void setName(String name) { this.name = name; }
}
