package com.sbertech.app.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String keycloakId;

    @Column(nullable = false, length = 100)
    private String displayName;

    @Column(length = 1000)
    private String bio;

    @OneToOne(fetch = FetchType.LAZY)
    private MediaAsset avatar;
}
