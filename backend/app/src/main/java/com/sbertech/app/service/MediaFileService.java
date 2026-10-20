package com.sbertech.app.service;

import com.sbertech.app.entity.MediaAsset;
import com.sbertech.app.entity.MediaAssetType;
import com.sbertech.app.repository.MediaAssetRepository;
import com.sbertech.db.service.FileStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class MediaFileService {
    private static final long MAX_AUDIO_SIZE = 100L * 1024 * 1024;
    private static final long MAX_IMAGE_SIZE = 5L * 1024 * 1024;
    private static final Map<String, Set<String>> AUDIO_TYPES = Map.of(
            "mp3", Set.of("audio/mpeg", "audio/mp3"),
            "wav", Set.of("audio/wav", "audio/x-wav", "audio/wave"),
            "m4a", Set.of("audio/mp4", "audio/x-m4a"),
            "aac", Set.of("audio/aac", "audio/x-aac"),
            "ogg", Set.of("audio/ogg", "application/ogg"),
            "flac", Set.of("audio/flac", "audio/x-flac")
    );
    private static final Map<String, Set<String>> IMAGE_TYPES = Map.of(
            "jpg", Set.of("image/jpeg"), "jpeg", Set.of("image/jpeg"),
            "png", Set.of("image/png"), "webp", Set.of("image/webp")
    );

    private final FileStorageService storageService;
    private final MediaAssetRepository assetRepository;

    public void validateAudio(MultipartFile file) {
        validate(file, AUDIO_TYPES, MAX_AUDIO_SIZE);
    }

    public void validateImage(MultipartFile file) {
        validate(file, IMAGE_TYPES, MAX_IMAGE_SIZE);
    }

    private void validate(MultipartFile file, Map<String, Set<String>> allowedTypes, long maxSize) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Файл не должен быть пустым");
        }
        if (file.getSize() > maxSize) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Превышен допустимый размер файла");
        }
        String name = file.getOriginalFilename();
        if (name == null || name.isBlank() || name.length() > 255) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Недопустимое имя файла");
        }
        String extension = name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        String contentType = file.getContentType();
        if (!allowedTypes.containsKey(extension) || contentType == null
                || !allowedTypes.get(extension).contains(contentType.toLowerCase(Locale.ROOT))) {
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Формат файла не поддерживается");
        }
    }

    // Вызывается внутри транзакции публикации. При откате удаляем уже загруженный объект.
    public MediaAsset upload(MultipartFile file, MediaAssetType type, String folder) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException("Для загрузки файла требуется транзакция");
        }
        String bucket = storageService.getBucketName();
        String key;
        try {
            key = storageService.uploadFile(file, folder);
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Не удалось прочитать и загрузить файл");
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) {
                    try {
                        storageService.deleteFile(bucket, key);
                    } catch (RuntimeException exception) {
                        log.warn("Не удалось удалить объект после отката БД: {}", key);
                    }
                }
            }
        });

        MediaAsset asset = new MediaAsset();
        asset.setBucket(bucket);
        asset.setObjectKey(key);
        asset.setOriginalName(file.getOriginalFilename());
        asset.setType(type);
        asset.setContentType(file.getContentType().toLowerCase(Locale.ROOT));
        asset.setSizeBytes(file.getSize());
        return assetRepository.save(asset);
    }

    public void deleteAfterCommit(MediaAsset asset) {
        assetRepository.delete(asset);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    storageService.deleteFile(asset.getBucket(), asset.getObjectKey());
                } catch (RuntimeException exception) {
                    log.warn("Не удалось удалить прежнюю обложку из S3: {}", asset.getObjectKey());
                }
            }
        });
    }
}
