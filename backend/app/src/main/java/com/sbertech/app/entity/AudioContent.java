package com.sbertech.app.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "material")
@Getter
@Setter
public class AudioContent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id", nullable = false)
    private AppUser author;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 32)
    private ContentType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private ContentStatus status = ContentStatus.DRAFT;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "material_tag",
            joinColumns = @JoinColumn(name = "material_id", nullable = false),
            inverseJoinColumns = @JoinColumn(name = "tag_id", nullable = false)
    )
    private Set<Tag> tags = new HashSet<>();

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_audio_id", unique = true)
    private MediaAsset sourceAudio;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stream_audio_id", unique = true)
    private MediaAsset streamAudio;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cover_id", unique = true)
    private MediaAsset cover;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(name = "media_version", nullable = false)
    private Integer mediaVersion = 1;
}
