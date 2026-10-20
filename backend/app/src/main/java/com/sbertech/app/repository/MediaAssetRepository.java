package com.sbertech.app.repository;

import com.sbertech.app.entity.MediaAsset;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MediaAssetRepository extends JpaRepository<MediaAsset, Long> {

    Optional<MediaAsset> findByBucketAndObjectKey(String bucket, String objectKey);
}
