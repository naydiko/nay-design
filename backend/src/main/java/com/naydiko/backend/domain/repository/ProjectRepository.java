package com.naydiko.backend.domain.repository;

import com.naydiko.backend.domain.entity.Project;
import com.naydiko.backend.domain.enums.ProjectStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * Repository for the {@link Project} aggregate root.
 */
public interface ProjectRepository extends JpaRepository<Project, UUID> {

    List<Project> findByOwnerId(UUID ownerId);

    List<Project> findByOwnerIdAndStatus(UUID ownerId, ProjectStatus status);
}

