package com.sbertech.core.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;

@Getter
@AllArgsConstructor
public class FileUrlDto {
    private final String url;
    private final String contentType;
    private final Long sizeBytes;
    private final Instant expiresAt;
}
