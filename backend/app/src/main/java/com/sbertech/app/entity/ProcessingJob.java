package com.sbertech.app.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
public class ProcessingJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private AudioContent content;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProcessingJobType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProcessingJobStatus status = ProcessingJobStatus.PENDING;

}
