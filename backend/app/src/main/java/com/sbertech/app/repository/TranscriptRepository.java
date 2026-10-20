package com.sbertech.app.repository;

import com.sbertech.app.entity.Transcript;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TranscriptRepository extends JpaRepository<Transcript, Long> {

    Optional<Transcript> findByContent_Id(Long contentId);
}
