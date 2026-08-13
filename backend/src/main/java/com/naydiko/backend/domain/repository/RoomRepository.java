package com.naydiko.backend.domain.repository;

import com.naydiko.backend.domain.entity.Room;
import com.naydiko.backend.domain.enums.RoomType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * Repository for the {@link Room} entity.
 */
public interface RoomRepository extends JpaRepository<Room, UUID> {

    List<Room> findByLevelId(UUID levelId);

    List<Room> findByLevelIdAndType(UUID levelId, RoomType type);
}

