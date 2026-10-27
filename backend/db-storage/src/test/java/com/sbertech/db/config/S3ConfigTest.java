package com.sbertech.db.config;

import com.sbertech.db.service.FileStorageService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.net.URI;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class S3ConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(S3Config.class)
            .withPropertyValues(
                    "s3.endpoint=http://internal-rustfs:9000",
                    "s3.public-endpoint=http://localhost:9000",
                    "s3.access-key=fake-test-access",
                    "s3.secret-key=fake-test-secret",
                    "s3.region=us-east-1");

    @Test
    void signedUrlUsesBrowserHostAndPathStyleWithoutExtraSignedHeaders() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            S3Presigner presigner = context.getBean(S3Presigner.class);

            var signed = presigner.presignGetObject(GetObjectPresignRequest.builder()
                    .signatureDuration(Duration.ofMinutes(15))
                    .getObjectRequest(GetObjectRequest.builder()
                            .bucket("stored-bucket")
                            .key("audio/test.mp3")
                            .build())
                    .build());

            assertThat(signed.url().getHost()).isEqualTo("localhost");
            assertThat(signed.url().getPort()).isEqualTo(9000);
            assertThat(signed.url().getPath()).isEqualTo("/stored-bucket/audio/test.mp3");
            assertThat(signed.isBrowserExecutable()).isTrue();
            assertThat(signed.signedHeaders()).containsOnlyKeys("host");
        });
    }

    @Test
    void serviceSignsUsingStoredBucketAndKeepsDefaultBucketOverload() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            FileStorageService service = new FileStorageService(mock(S3Client.class),
                    context.getBean(S3Presigner.class), "default-bucket");

            URI stored = URI.create(service.generatePresignedUrl("stored-bucket", "covers/test.png", Duration.ofMinutes(5)));
            URI defaultUrl = URI.create(service.generatePresignedUrl("audio/test.mp3", Duration.ofMinutes(5)));

            assertThat(stored.getHost()).isEqualTo("localhost");
            assertThat(stored.getPath()).isEqualTo("/stored-bucket/covers/test.png");
            assertThat(defaultUrl.getPath()).isEqualTo("/default-bucket/audio/test.mp3");
        });
    }
}
