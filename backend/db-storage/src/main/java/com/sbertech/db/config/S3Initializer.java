package com.sbertech.db.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CORSConfiguration;
import software.amazon.awssdk.services.s3.model.CORSRule;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.PutBucketCorsRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

/** Creates the local bucket and permits browser playback without making objects public. */
@Component
@ConditionalOnProperty(name = "s3.initialize-bucket", havingValue = "true", matchIfMissing = true)
public class S3Initializer implements CommandLineRunner {

    private final S3Client s3Client;
    private final String bucketName;
    private final String allowedOrigin;
    private final String region;

    public S3Initializer(S3Client s3Client,
                         @Value("${s3.bucket-name}") String bucketName,
                         @Value("${s3.cors.allowed-origin:${frontend.url:http://localhost:5173}}") String allowedOrigin,
                         @Value("${s3.region:us-east-1}") String region) {
        this.s3Client = s3Client;
        this.bucketName = bucketName;
        this.allowedOrigin = allowedOrigin;
        this.region = region;
    }

    @Override
    public void run(String... args) {
        try {
            s3Client.headBucket(HeadBucketRequest.builder().bucket(bucketName).build());
        } catch (S3Exception exception) {
            if (exception.statusCode() != 404) {
                throw exception;
            }
            CreateBucketRequest.Builder request = CreateBucketRequest.builder().bucket(bucketName);
            if (!"us-east-1".equals(region)) {
                request.createBucketConfiguration(configuration -> configuration.locationConstraint(region));
            }
            s3Client.createBucket(request.build());
        }

        CORSRule browserPlayback = CORSRule.builder()
                .id("smartcast-browser-playback")
                .allowedOrigins(allowedOrigin)
                .allowedMethods("GET", "HEAD")
                .allowedHeaders("Range", "Content-Type")
                .exposeHeaders("Content-Range", "Accept-Ranges", "Content-Length", "ETag")
                .maxAgeSeconds(3600)
                .build();
        s3Client.putBucketCors(PutBucketCorsRequest.builder()
                .bucket(bucketName)
                .corsConfiguration(CORSConfiguration.builder().corsRules(browserPlayback).build())
                .build());
    }
}
