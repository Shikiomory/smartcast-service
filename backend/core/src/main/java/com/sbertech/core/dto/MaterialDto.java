package com.sbertech.core.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
public class MaterialDto {
    private Long id;
    private String title;
    private String description;
    private String type;
    private String status;
    private Long authorId;
    private String authorName;
    private Long categoryId;
    private String categoryName;
    private String coverUrl;
    private Long durationMs;
    private Instant linksExpireAt;
}
