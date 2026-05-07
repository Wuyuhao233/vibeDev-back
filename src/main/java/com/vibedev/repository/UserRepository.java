package com.vibedev.repository;

import com.vibedev.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, String> {

    @Query("SELECT u FROM User u WHERE (u.username = :account OR u.email = :account) AND u.isActivated = true AND u.isDeactivated = false")
    Optional<User> findByUsernameOrEmailActive(String account);

    Optional<User> findByUsernameAndIsActivatedTrueAndIsDeactivatedFalse(String username);

    Optional<User> findByEmailAndIsActivatedTrueAndIsDeactivatedFalse(String email);

    Optional<User> findByUsername(String username);

    Optional<User> findByEmail(String email);

    boolean existsByUsernameAndIsActivatedTrueAndIsDeactivatedFalse(String username);

    boolean existsByEmailAndIsActivatedTrueAndIsDeactivatedFalse(String email);

    Optional<User> findByCasId(String casId);

    // Admin user search with filters
    @Query("SELECT u FROM User u WHERE " +
            "(:search IS NULL OR u.username LIKE %:search% OR u.email LIKE %:search% OR u.nickname LIKE %:search%) AND " +
            "(:role IS NULL OR u.role = :role) AND " +
            "(:status IS NULL OR " +
            "  (:status = 'active' AND u.isBanned = false AND u.isActivated = true AND u.isDeactivated = false) OR " +
            "  (:status = 'banned' AND u.isBanned = true))")
    Page<User> findUsersForAdmin(@Param("search") String search,
                                  @Param("role") String role,
                                  @Param("status") String status,
                                  Pageable pageable);

    @Query("SELECT COUNT(u) FROM User u WHERE u.createdAt >= :since")
    long countByCreatedAtSince(@Param("since") java.time.Instant since);
}
