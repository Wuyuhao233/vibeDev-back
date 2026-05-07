package com.vibedev.repository;

import com.vibedev.entity.Favorite;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FavoriteRepository extends JpaRepository<Favorite, String> {

    Page<Favorite> findByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);

    Optional<Favorite> findByUserIdAndPostId(String userId, String postId);

    int countByPostId(String postId);

    void deleteByUserIdAndPostId(String userId, String postId);

    List<Favorite> findByCollectionFolderId(String collectionFolderId);
}
