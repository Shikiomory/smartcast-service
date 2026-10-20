package com.sbertech.db.service;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class FileStorageServiceTest {

    private final S3Client s3Client = mock(S3Client.class);
    private final S3Presigner presigner = mock(S3Presigner.class);
    private final FileStorageService service = new FileStorageService(s3Client, presigner, "upload-bucket");

    @Test
    void uploadUsesUuidInsteadOfOriginalPathAndClosesStream() throws IOException {
        byte[] bytes = "test audio".getBytes(StandardCharsets.UTF_8);
        TrackingStream stream = new TrackingStream(bytes);
        MultipartFile file = mock(MultipartFile.class);
        when(file.getOriginalFilename()).thenReturn("../../private-recording.MP3");
        when(file.getContentType()).thenReturn("audio/mpeg");
        when(file.getSize()).thenReturn((long) bytes.length);
        when(file.getInputStream()).thenReturn(stream);
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class))).thenAnswer(invocation -> {
            PutObjectRequest request = invocation.getArgument(0);
            RequestBody body = invocation.getArgument(1);
            assertThat(request.bucket()).isEqualTo("upload-bucket");
            assertThat(request.contentType()).isEqualTo("audio/mpeg");
            assertThat(request.contentDisposition()).isEqualTo("inline");
            assertThat(stream.closed).isFalse();
            assertThat(body.contentStreamProvider().newStream().readAllBytes()).isEqualTo(bytes);
            return PutObjectResponse.builder().build();
        });

        String key = service.uploadFile(file, "audio");

        assertThat(key).matches("audio/[0-9a-f-]{36}\\.mp3");
        assertThat(key).doesNotContain("private-recording", "..");
        assertThat(stream.closed).isTrue();
    }

    @Test
    void unsafeExtensionIsNotIncludedInObjectKey() throws IOException {
        MockMultipartFile file = new MockMultipartFile("file", "cover.svg?injected", "image/png", new byte[]{1});

        String key = service.uploadFile(file, "covers");

        assertThat(key).matches("covers/[0-9a-f-]{36}");
    }

    @Test
    void missingExtensionDoesNotTurnImageIntoMp3() throws IOException {
        MockMultipartFile file = new MockMultipartFile("file", "cover", "image/png", new byte[]{1});

        String key = service.uploadFile(file, "covers");

        assertThat(key).matches("covers/[0-9a-f-]{36}");
    }

    @Test
    void uploadFailureStillClosesInputStreamAndPropagatesError() throws IOException {
        TrackingStream stream = new TrackingStream(new byte[]{1});
        MultipartFile file = mock(MultipartFile.class);
        when(file.getInputStream()).thenReturn(stream);
        when(file.getSize()).thenReturn(1L);
        S3Exception.Builder failureBuilder = S3Exception.builder();
        failureBuilder.statusCode(503);
        failureBuilder.message("Storage unavailable");
        S3Exception failure = (S3Exception) failureBuilder.build();
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class))).thenThrow(failure);

        assertThatThrownBy(() -> service.uploadFile(file, "audio")).isSameAs(failure);

        assertThat(stream.closed).isTrue();
    }

    @Test
    void rejectsEmptyFileBeforeContactingStorage() {
        MockMultipartFile file = new MockMultipartFile("file", new byte[0]);

        assertThatThrownBy(() -> service.uploadFile(file, "audio"))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(s3Client);
    }

    @Test
    void rejectsFolderTraversalBeforeContactingStorage() {
        MockMultipartFile file = new MockMultipartFile("file", new byte[]{1});

        assertThatThrownBy(() -> service.uploadFile(file, "../other-bucket"))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(s3Client);
    }

    @Test
    void deleteUsesStoredBucketInsteadOfUploadDefault() {
        service.deleteFile("stored-bucket", "audio/old.mp3");

        verify(s3Client).deleteObject(argThat((DeleteObjectRequest request) ->
                request.bucket().equals("stored-bucket") && request.key().equals("audio/old.mp3")));
        assertThat(service.getBucketName()).isEqualTo("upload-bucket");
    }

    private static class TrackingStream extends ByteArrayInputStream {
        private boolean closed;

        private TrackingStream(byte[] bytes) {
            super(bytes);
        }

        @Override
        public void close() throws IOException {
            closed = true;
            super.close();
        }
    }
}
