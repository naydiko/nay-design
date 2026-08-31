package com.naydiko.backend.domain.repository;

import com.naydiko.backend.domain.entity.Room;
import com.naydiko.backend.domain.enums.RoomType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

/**
 * Repository for the {@link Room} entity.
 */
public interface RoomRepository extends JpaRepository<Room, UUID> {

    List<Room> findByLevelId(UUID levelId);

    List<Room> findByLevelIdAndType(UUID levelId, RoomType type);

    /**
     * Same as {@link #findByLevelId}, but eagerly fetches each room's
     * bordering {@code walls} in one extra query instead of one query per
     * room — use this whenever the walls collection will actually be read
     * (e.g. building a level's geometry document), to avoid an N+1.
     */
    @Query("SELECT DISTINCT r FROM Room r LEFT JOIN FETCH r.walls WHERE r.level.id = :levelId")
    List<Room> findByLevelIdFetchingWalls(@Param("levelId") UUID levelId);
}



