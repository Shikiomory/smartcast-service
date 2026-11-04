package com.sbertech.app.repository;

import com.sbertech.app.entity.AudioContent;
import com.sbertech.app.entity.ContentStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AudioContentRepository extends JpaRepository<AudioContent, Long> {

    List<AudioContent> findAllByAuthor_Id(Long authorId);

    List<AudioContent> findAllByStatus(ContentStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select material from AudioContent material where material.id = :id")
    Optional<AudioContent> findByIdForUpdate(@Param("id") Long id);
}
