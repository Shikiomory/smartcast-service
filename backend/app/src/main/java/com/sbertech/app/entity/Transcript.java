package com.sbertech.app.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
public class Transcript {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    private AudioContent content;

    @Column(length = 20)
    private String language;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String fullText;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String subtitlesJson;
}
