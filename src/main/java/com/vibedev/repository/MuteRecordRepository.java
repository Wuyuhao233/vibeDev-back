package com.vibedev.repository;

import com.vibedev.entity.MuteRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MuteRecordRepository extends JpaRepository<MuteRecord, String> {

    Optional<MuteRecord> findByUserIdAndIsActiveTrue(String userId);

    List<MuteRecord> findByUserIdOrderByCreatedAtDesc(String userId);
}
