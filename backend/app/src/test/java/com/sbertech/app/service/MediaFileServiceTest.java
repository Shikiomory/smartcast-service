package com.sbertech.app.service;

import com.sbertech.app.entity.MediaAsset;
import com.sbertech.app.entity.MediaAssetType;
import com.sbertech.app.repository.MediaAssetRepository;
import com.sbertech.db.service.FileStorageService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class MediaFileServiceTest {
    private final FileStorageService storage = mock(FileStorageService.class);
    private final MediaAssetRepository assets = mock(MediaAssetRepository.class);
    private final MediaFileService service = new MediaFileService(storage, assets);

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @ParameterizedTest
    @CsvSource({"episode.mp3,audio/mpeg", "episode.wav,audio/wav", "episode.m4a,audio/mp4",
            "episode.aac,audio/aac", "episode.ogg,audio/ogg", "episode.flac,audio/flac"})
    void acceptsSupportedAudio(String filename, String type) {
        assertDoesNotThrow(() -> service.validateAudio(file(filename, type)));
        verifyNoInteractions(storage, assets);
    }

    @ParameterizedTest
    @CsvSource({"cover.jpg,image/jpeg", "cover.png,image/png", "cover.webp,image/webp"})
    void acceptsSupportedImages(String filename, String type) {
        assertDoesNotThrow(() -> service.validateImage(file(filename, type)));
    }

    @Test
    void rejectsEmptyFilesWrongMimeAndSvgBeforeAnyUpload() {
        var empty = new MockMultipartFile("file", "episode.mp3", "audio/mpeg", new byte[0]);
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class,
                () -> service.validateAudio(empty)).getStatusCode());
        assertEquals(HttpStatus.UNSUPPORTED_MEDIA_TYPE, assertThrows(ResponseStatusException.class,
                () -> service.validateAudio(file("episode.mp3", "image/png"))).getStatusCode());
        assertEquals(HttpStatus.UNSUPPORTED_MEDIA_TYPE, assertThrows(ResponseStatusException.class,
                () -> service.validateImage(file("cover.svg", "image/svg+xml"))).getStatusCode());
        verifyNoInteractions(storage, assets);
    }

    @Test
    void rejectsOversizedAudioAndImagesWithoutReadingFileBytes() {
        MultipartFile audio = mock(MultipartFile.class);
        when(audio.getSize()).thenReturn(100L * 1024 * 1024 + 1);
        MultipartFile image = mock(MultipartFile.class);
        when(image.getSize()).thenReturn(5L * 1024 * 1024 + 1);
        assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, assertThrows(ResponseStatusException.class,
                () -> service.validateAudio(audio)).getStatusCode());
        assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, assertThrows(ResponseStatusException.class,
                () -> service.validateImage(image)).getStatusCode());
        verifyNoInteractions(storage, assets);
    }

    @Test
    void persistsS3LocationAndMetadataAndCleansUpRolledBackUpload() throws IOException {
        TransactionSynchronizationManager.initSynchronization();
        var audio = file("episode.mp3", "audio/mpeg");
        when(storage.getBucketName()).thenReturn("private-media");
        when(storage.uploadFile(audio, "audio")).thenReturn("audio/generated-id.mp3");
        when(assets.save(any())).thenAnswer(call -> call.getArgument(0));
        MediaAsset asset = service.upload(audio, MediaAssetType.ORIGINAL_AUDIO, "audio");
        assertEquals("private-media", asset.getBucket());
        assertEquals("audio/generated-id.mp3", asset.getObjectKey());
        assertEquals("episode.mp3", asset.getOriginalName());
        assertEquals("audio/mpeg", asset.getContentType());
        assertEquals(audio.getSize(), asset.getSizeBytes());
        assertEquals(MediaAssetType.ORIGINAL_AUDIO, asset.getType());
        verify(assets).save(asset);
        complete(TransactionSynchronization.STATUS_ROLLED_BACK);
        verify(storage).deleteFile("private-media", "audio/generated-id.mp3");
    }

    @Test
    void committedUploadIsKept() throws IOException {
        TransactionSynchronizationManager.initSynchronization();
        var audio = file("episode.mp3", "audio/mpeg");
        when(storage.getBucketName()).thenReturn("private-media");
        when(storage.uploadFile(audio, "audio")).thenReturn("audio/generated-id.mp3");
        when(assets.save(any())).thenAnswer(call -> call.getArgument(0));
        service.upload(audio, MediaAssetType.ORIGINAL_AUDIO, "audio");
        complete(TransactionSynchronization.STATUS_COMMITTED);
        verify(storage, never()).deleteFile(any(), any());
    }

    @Test
    void databaseFailureAfterUploadStillHasS3Compensation() throws IOException {
        TransactionSynchronizationManager.initSynchronization();
        var audio = file("episode.mp3", "audio/mpeg");
        when(storage.getBucketName()).thenReturn("private-media");
        when(storage.uploadFile(audio, "audio")).thenReturn("audio/generated-id.mp3");
        when(assets.save(any())).thenThrow(new DataIntegrityViolationException("test failure"));
        assertThrows(DataIntegrityViolationException.class,
                () -> service.upload(audio, MediaAssetType.ORIGINAL_AUDIO, "audio"));
        complete(TransactionSynchronization.STATUS_ROLLED_BACK);
        verify(storage).deleteFile("private-media", "audio/generated-id.mp3");
    }

    private void complete(int status) {
        TransactionSynchronizationManager.getSynchronizations().forEach(sync -> sync.afterCompletion(status));
    }

    private MockMultipartFile file(String filename, String type) {
        return new MockMultipartFile("file", filename, type, new byte[]{1, 2, 3});
    }
}
