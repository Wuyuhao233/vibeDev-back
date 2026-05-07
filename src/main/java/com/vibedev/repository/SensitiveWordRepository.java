package com.vibedev.repository;

import com.vibedev.entity.SensitiveWord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SensitiveWordRepository extends JpaRepository<SensitiveWord, String> {

    List<SensitiveWord> findByIsActiveTrue();
}
