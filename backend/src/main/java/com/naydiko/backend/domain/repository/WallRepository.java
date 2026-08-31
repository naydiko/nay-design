package com.naydiko.backend.domain.repository;

import com.naydiko.backend.domain.entity.Wall;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * Repository for the {@link Wall} entity.
 */
public interface WallRepository extends JpaRepository<Wall, UUID> {

    List<Wall> findByLevelId(UUID levelId);
}

