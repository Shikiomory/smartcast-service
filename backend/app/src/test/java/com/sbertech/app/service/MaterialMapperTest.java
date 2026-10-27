package com.sbertech.app.service;

import com.sbertech.app.entity.*;
import com.sbertech.core.dto.FileUrlDto;
import com.sbertech.core.dto.MaterialDetailsDto;
import com.sbertech.db.service.FileStorageService;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MaterialMapperTest {
    private final FileStorageService storage = mock(FileStorageService.class);
    private final MaterialMapper mapper = new MaterialMapper(storage);

    @Test
    void includesCompositionMetadataAndFreshSourceAndCoverLinks() {
        AudioContent content = content();
        content.setSourceAudio(asset("original.mp3", "audio/mpeg"));
        content.setCover(asset("cover.png", "image/png"));
        when(storage.generatePresignedUrl("private", "original.mp3", Duration.ofHours(1))).thenReturn("audio-url");
        when(storage.generatePresignedUrl("private", "cover.png", Duration.ofHours(1))).thenReturn("cover-url");
        MaterialDetailsDto details = mapper.toDetails(content);
        assertEquals(42L, details.getId());
        assertEquals("Episode", details.getTitle());
        assertEquals("Author", details.getAuthorName());
        assertEquals("Talks", details.getCategoryName());
        assertEquals("PENDING_MODERATION", details.getStatus());
        assertEquals("audio-url", details.getAudioUrl());
        assertEquals("cover-url", details.getCoverUrl());
        assertEquals("audio/mpeg", details.getAudioContentType());
        assertTrue(details.getLinksExpireAt().isAfter(Instant.now().plusSeconds(3500)));
        assertEquals("original.mp3", content.getSourceAudio().getObjectKey());
    }

    @Test
    void playbackUsesConvertedVersionWhenAvailable() {
        AudioContent content = content();
        content.setSourceAudio(asset("original.wav", "audio/wav"));
        content.setStreamAudio(asset("stream.mp3", "audio/mpeg"));
        when(storage.generatePresignedUrl("private", "stream.mp3", Duration.ofHours(1))).thenReturn("stream-url");
        FileUrlDto stream = mapper.toAudioUrl(content);
        assertEquals("stream-url", stream.getUrl());
        assertEquals("audio/mpeg", stream.getContentType());
        verify(storage, never()).generatePresignedUrl("private", "original.wav", Duration.ofHours(1));
    }

    private AudioContent content() {
        AppUser author = new AppUser();
        author.setId(1L);
        author.setDisplayName("Author");
        Category category = new Category();
        category.setId(2L);
        category.setName("Talks");
        AudioContent content = new AudioContent();
        content.setId(42L);
        content.setTitle("Episode");
        content.setType(ContentType.PODCAST);
        content.setStatus(ContentStatus.PENDING_MODERATION);
        content.setAuthor(author);
        content.setCategory(category);
        return content;
    }

    private MediaAsset asset(String key, String contentType) {
        MediaAsset asset = new MediaAsset();
        asset.setBucket("private");
        asset.setObjectKey(key);
        asset.setContentType(contentType);
        asset.setSizeBytes(123L);
        return asset;
    }
}
