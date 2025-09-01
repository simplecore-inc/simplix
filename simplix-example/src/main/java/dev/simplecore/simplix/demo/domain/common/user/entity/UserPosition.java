package dev.simplecore.simplix.demo.domain.common.user.entity;

import dev.simplecore.simplix.core.annotation.DisplayName;
import dev.simplecore.simplix.demo.domain.AuditingBaseEntity;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Comment;
import dev.simplecore.simplix.core.hibernate.UuidV7Generator;

import jakarta.persistence.*;


@Entity
@Getter
@Setter
@Table(name = "user_position")
@Comment("User Position: Official position hierarchy information within the organization")
public class UserPosition extends AuditingBaseEntity<String> {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID, generator = "uuid-v7")
    @UuidV7Generator
    @Column(name="position_id", unique = true, nullable = false, updatable = false, length = 36)
    @Comment("Position ID: Unique UUID Version 7 used in the system")
    private String positionId;

    @Column(nullable = false, unique = true)
    @Comment("Position Name: Official position name within the organization")
    @DisplayName(description = "Position name for display")
    private String name;  // e.g., Employee, Assistant Manager, Manager

    @Column(length = 500)
    @Comment("Position Description: Detailed description and purpose of the position")
    private String description;

    @Column(name = "item_order", nullable = false)
    @Comment("Order")
    private Integer itemOrder;

        
    //----------------------------------

    @Override
    public String getId() {
        return this.positionId;
    }

    @Override
    public void setId(String id) {
        this.positionId = id;
    }
}