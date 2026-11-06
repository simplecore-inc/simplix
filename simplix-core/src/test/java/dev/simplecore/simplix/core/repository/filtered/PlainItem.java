package dev.simplecore.simplix.core.repository.filtered;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** An entity with no session filter, whose reads must stay untouched. */
@Entity
@Table(name = "plain_items")
@Getter
@Setter
@NoArgsConstructor
public class PlainItem {

    @Id
    @Column(name = "item_id")
    private String itemId;

    @Column(name = "deleted", nullable = false)
    private Boolean deleted = false;

    public PlainItem(String itemId) {
        this.itemId = itemId;
    }
}
