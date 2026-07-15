package dev.simplecore.simplix.hibernate.transaction.it;

import dev.simplecore.simplix.core.entity.SoftDeletable;
import dev.simplecore.simplix.core.entity.annotation.EntityEventConfig;
import dev.simplecore.simplix.hibernate.event.EntityEventPublishingListener;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.SQLDelete;

@Entity
@Table(name = "soft_delete_items")
@SQLDelete(sql = "UPDATE soft_delete_items SET deleted = true WHERE id = ?")
@EntityListeners(EntityEventPublishingListener.class)
@EntityEventConfig(onCreate = "SOFT_ITEM_CREATED", onUpdate = "SOFT_ITEM_UPDATED", onDelete = "SOFT_ITEM_DELETED")
public class SoftDeleteItem implements SoftDeletable {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    private String name;

    @Column(nullable = false)
    private boolean deleted = false;

    protected SoftDeleteItem() {}

    public SoftDeleteItem(String name) {
        this.name = name;
    }

    @Override
    public boolean isDeleted() { return deleted; }

    public String getId() { return id; }
}
