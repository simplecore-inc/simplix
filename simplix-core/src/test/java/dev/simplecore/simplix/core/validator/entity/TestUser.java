package dev.simplecore.simplix.core.validator.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

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

    public TestUser(String email, String username, String name) {
        this.email = email;
        this.username = username;
        this.name = name;
    }
}
