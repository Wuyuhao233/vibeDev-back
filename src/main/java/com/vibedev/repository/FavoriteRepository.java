package com.vibedev.repository;

import com.vibedev.entity.Favorite;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FavoriteRepository extends JpaRepository<Favorite, String> {

    Page<Favorite> findByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);
}
