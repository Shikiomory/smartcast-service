package com.sbertech.app.repository;

import com.sbertech.app.entity.ProcessingJob;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProcessingJobRepository extends JpaRepository<ProcessingJob, Long> {

    List<ProcessingJob> findAllByContent_Id(Long contentId);
}
