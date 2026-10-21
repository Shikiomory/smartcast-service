package com.sbertech.db.config;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.PutBucketCorsRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class S3InitializerTest {

    private final S3Client client = mock(S3Client.class);
    private final S3Initializer initializer = new S3Initializer(client, "test-bucket", "http://localhost:5173", "us-east-1");

    @Test
    void createsBucketOnlyWhenMissingAndConfiguresBrowserPlaybackCors() {
        when(client.headBucket(any(HeadBucketRequest.class)))
                .thenThrow(S3Exception.builder().statusCode(404).build());

        initializer.run();

        verify(client).createBucket(argThat((CreateBucketRequest request) -> request.bucket().equals("test-bucket")));
        ArgumentCaptor<PutBucketCorsRequest> captor = ArgumentCaptor.forClass(PutBucketCorsRequest.class);
        verify(client).putBucketCors(captor.capture());
        var rule = captor.getValue().corsConfiguration().corsRules().get(0);
        assertThat(captor.getValue().bucket()).isEqualTo("test-bucket");
        assertThat(rule.allowedOrigins()).containsExactly("http://localhost:5173");
        assertThat(rule.allowedMethods()).containsExactly("GET", "HEAD");
        assertThat(rule.allowedHeaders()).contains("Range");
        assertThat(rule.exposeHeaders()).contains("Content-Range", "Accept-Ranges", "Content-Length", "ETag");
    }

    @Test
    void doesNotCreateExistingBucket() {
        initializer.run();

        verify(client, never()).createBucket(any(CreateBucketRequest.class));
        verify(client).putBucketCors(any(PutBucketCorsRequest.class));
    }

    @Test
    void forbiddenBucketFailsStartupWithoutAttemptingCreation() {
        S3Exception.Builder failureBuilder = S3Exception.builder();
        failureBuilder.statusCode(403);
        failureBuilder.message("Forbidden");
        S3Exception forbidden = (S3Exception) failureBuilder.build();
        when(client.headBucket(any(HeadBucketRequest.class))).thenThrow(forbidden);

        assertThatThrownBy(initializer::run).isSameAs(forbidden);

        verify(client, never()).createBucket(any(CreateBucketRequest.class));
        verify(client, never()).putBucketCors(any(PutBucketCorsRequest.class));
    }

    @Test
    void connectionFailureIsNotTreatedAsMissingBucket() {
        SdkClientException unavailable = SdkClientException.create("Connection refused");
        when(client.headBucket(any(HeadBucketRequest.class))).thenThrow(unavailable);

        assertThatThrownBy(initializer::run).isSameAs(unavailable);

        verify(client, never()).createBucket(any(CreateBucketRequest.class));
        verify(client, never()).putBucketCors(any(PutBucketCorsRequest.class));
    }

    @Test
    void initializationCanBeDisabledForManagedStorage() {
        new ApplicationContextRunner()
                .withUserConfiguration(S3Initializer.class)
                .withBean(S3Client.class, () -> client)
                .withPropertyValues("s3.initialize-bucket=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(S3Initializer.class);
                });

        verify(client, never()).headBucket(any(HeadBucketRequest.class));
        verify(client, never()).createBucket(any(CreateBucketRequest.class));
        verify(client, never()).putBucketCors(any(PutBucketCorsRequest.class));
    }
}
