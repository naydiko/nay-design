package com.naydiko.backend.domain.repository;

import com.naydiko.backend.domain.entity.Level;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * Repository for the {@link Level} entity.
 */
public interface LevelRepository extends JpaRepository<Level, UUID> {

    List<Level> findByProjectIdOrderByOrderIndexAsc(UUID projectId);
}

