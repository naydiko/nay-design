package com.naydiko.backend.domain.repository;

import com.naydiko.backend.domain.entity.FurniturePlacement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * Repository for the {@link FurniturePlacement} entity.
 */
public interface FurniturePlacementRepository extends JpaRepository<FurniturePlacement, UUID> {

    List<FurniturePlacement> findByRoomId(UUID roomId);

    void deleteByRoomId(UUID roomId);
}

