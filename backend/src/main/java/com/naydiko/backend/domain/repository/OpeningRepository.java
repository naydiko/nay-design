package com.naydiko.backend.domain.repository;

import com.naydiko.backend.domain.entity.Opening;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * Repository for the {@link Opening} entity.
 */
public interface OpeningRepository extends JpaRepository<Opening, UUID> {

    List<Opening> findByWallLevelId(UUID levelId);
}

