package com.sbertech.app.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
public class MediaAsset {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String bucket;

    @Column(nullable = false, length = 1024)
    private String objectKey;

    private String originalName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MediaAssetType type;

    @Column(nullable = false, length = 100)
    private String contentType;

    @Column(nullable = false)
    private Long sizeBytes;
}
