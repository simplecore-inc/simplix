package dev.simplecore.simplix.core.validator.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Test entity for unique validation tests.
 */
@Entity
@Table(name = "test_users")
@Getter
@Setter
@NoArgsConstructor
public class TestUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true)
    private String email;

    @Column(unique = true)
    private String username;

    private String name;

    /**
     * Boolean-based soft delete flag.
     */
    private Boolean deleted;

    /**
     * Timestamp-based soft delete field.
     */
    private LocalDateTime deletedAt;

    public TestUser(String email, String username, String name) {
        this.email = email;
        this.username = username;
        this.name = name;
    }

    /**
     * Marks this user as soft-deleted using boolean flag.
     */
    public void softDeleteBoolean() {
        this.deleted = true;
    }

    /**
     * Marks this user as soft-deleted using timestamp.
     */
    public void softDeleteTimestamp() {
        this.deletedAt = LocalDateTime.now();
    }
}
