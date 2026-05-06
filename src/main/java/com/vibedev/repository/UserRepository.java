package com.vibedev.repository;

import com.vibedev.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

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
}
