package com.elandinnople.loadpilot.domain.user.entity;

import com.elandinnople.loadpilot.common.entity.BaseEntity;
import com.elandinnople.loadpilot.domain.loadtest.entity.LoadTest;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "users")
@Getter @Setter
@NoArgsConstructor
public class User extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String username;

    @Column(nullable = false)
    private String email;

    @Column(name = "keycloak_id", nullable = false, unique = true)
    private String keycloakId;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL)
    private List<LoadTest> loadTests = new ArrayList<>();
}
