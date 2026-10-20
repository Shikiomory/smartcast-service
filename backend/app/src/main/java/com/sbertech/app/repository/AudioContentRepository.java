package com.sbertech.app.repository;

import com.sbertech.app.entity.AudioContent;
import com.sbertech.app.entity.ContentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AudioContentRepository extends JpaRepository<AudioContent, Long> {

    List<AudioContent> findAllByAuthor_Id(Long authorId);

    List<AudioContent> findAllByStatus(ContentStatus status);
}
