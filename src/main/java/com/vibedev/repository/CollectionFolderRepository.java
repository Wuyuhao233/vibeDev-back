package com.vibedev.repository;

import com.vibedev.entity.CollectionFolder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CollectionFolderRepository extends JpaRepository<CollectionFolder, String> {

    List<CollectionFolder> findByUserIdOrderByCreatedAtAsc(String userId);

    Optional<CollectionFolder> findByIdAndUserId(String id, String userId);

    boolean existsByUserIdAndName(String userId, String name);
}
