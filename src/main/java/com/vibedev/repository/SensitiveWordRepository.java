package com.vibedev.repository;

import com.vibedev.entity.SensitiveWord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SensitiveWordRepository extends JpaRepository<SensitiveWord, String> {

    List<SensitiveWord> findByIsActiveTrue();

    @Query("SELECT s FROM SensitiveWord s WHERE :search IS NULL OR s.word LIKE %:search%")
    Page<SensitiveWord> findByWordContaining(@Param("search") String search, Pageable pageable);

    boolean existsByWord(String word);
}
