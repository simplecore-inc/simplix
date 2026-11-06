package dev.simplecore.simplix.core.repository.filtered;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** An entity that inherits its session filter from {@link FilteredBase}. */
@Entity
@Table(name = "inherited_filter_items")
@Getter
@Setter
@NoArgsConstructor
public class InheritedFilterItem extends FilteredBase {

    @Id
    @Column(name = "item_id")
    private String itemId;

    public InheritedFilterItem(String itemId) {
        this.itemId = itemId;
    }
}
