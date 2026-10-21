package com.sbertech.app.service;

import com.sbertech.app.entity.AudioContent;
import com.sbertech.app.entity.MediaAsset;
import com.sbertech.core.dto.FileUrlDto;
import com.sbertech.core.dto.MaterialDetailsDto;
import com.sbertech.core.dto.MaterialDto;
import com.sbertech.db.service.FileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;

@Component
@RequiredArgsConstructor
public class MaterialMapper {
    private static final Duration LINK_LIFETIME = Duration.ofHours(1);
    private final FileStorageService storageService;

    public MaterialDto toDto(AudioContent content) {
        MaterialDto dto = new MaterialDto();
        fill(content, dto);
        return dto;
    }

    public MaterialDetailsDto toDetails(AudioContent content) {
        MaterialDetailsDto dto = new MaterialDetailsDto();
        fill(content, dto);
        MediaAsset audio = playableAudio(content);
        if (audio != null) {
            dto.setAudioUrl(url(audio));
            dto.setAudioContentType(audio.getContentType());
            dto.setAudioSizeBytes(audio.getSizeBytes());
        }
        return dto;
    }

    public FileUrlDto toAudioUrl(AudioContent content) {
        MediaAsset audio = playableAudio(content);
        if (audio == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Аудиофайл ещё не загружен");
        }
        Instant expiresAt = Instant.now().plus(LINK_LIFETIME);
        return new FileUrlDto(url(audio), audio.getContentType(), audio.getSizeBytes(), expiresAt);
    }

    private void fill(AudioContent content, MaterialDto dto) {
        dto.setId(content.getId());
        dto.setTitle(content.getTitle());
        dto.setDescription(content.getDescription());
        dto.setType(content.getType().name());
        dto.setStatus(content.getStatus().name());
        dto.setAuthorId(content.getAuthor().getId());
        dto.setAuthorName(content.getAuthor().getDisplayName());
        dto.setCategoryId(content.getCategory().getId());
        dto.setCategoryName(content.getCategory().getName());
        dto.setDurationMs(content.getDurationMs());
        dto.setLinksExpireAt(Instant.now().plus(LINK_LIFETIME));
        dto.setCoverUrl(content.getCover() == null ? null : url(content.getCover()));
    }

    private MediaAsset playableAudio(AudioContent content) {
        // Пока отдельной конвертации нет, плеер получает исходный файл.
        return content.getStreamAudio() == null ? content.getSourceAudio() : content.getStreamAudio();
    }

    private String url(MediaAsset asset) {
        return storageService.generatePresignedUrl(asset.getBucket(), asset.getObjectKey(), LINK_LIFETIME);
    }
}
