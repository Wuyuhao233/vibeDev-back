package com.vibedev.repository;

import com.vibedev.entity.UserExportTask;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserExportTaskRepository extends JpaRepository<UserExportTask, String> {

    Optional<UserExportTask> findByIdAndUserId(String id, String userId);

    long countByUserIdAndCreatedAtAfter(String userId, java.time.Instant since);
}
