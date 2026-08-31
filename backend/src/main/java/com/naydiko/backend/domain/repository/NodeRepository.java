package com.naydiko.backend.domain.repository;

import com.naydiko.backend.domain.entity.Node;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * Repository for the {@link Node} entity.
 */
public interface NodeRepository extends JpaRepository<Node, UUID> {

    List<Node> findByLevelId(UUID levelId);
}

