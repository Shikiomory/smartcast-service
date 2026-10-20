package com.sbertech.app.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "transcript")
@Getter
@Setter
public class Transcript {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "material_id", nullable = false, unique = true)
    private AudioContent content;

    // Версия аудио, для которой создана расшифровка.
    @Column(name = "source_version", nullable = false)
    private Integer sourceVersion;

    @Column(name = "language", length = 20)
    private String language;

    @Column(name = "full_text", nullable = false, columnDefinition = "TEXT")
    private String fullText;

    @Column(name = "subtitles_json", nullable = false, columnDefinition = "TEXT")
    private String subtitlesJson;
}
