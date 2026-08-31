package com.naydiko.backend.service;

import com.naydiko.backend.domain.entity.Level;
import com.naydiko.backend.domain.entity.Project;
import com.naydiko.backend.domain.repository.LevelRepository;
import com.naydiko.backend.domain.repository.ProjectRepository;
import com.naydiko.backend.dto.request.CreateLevelRequest;
import com.naydiko.backend.dto.request.UpdateLevelRequest;
import com.naydiko.backend.dto.response.LevelResponse;
import com.naydiko.backend.exception.ResourceNotFoundException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Business logic for managing {@link Level}s within a {@link Project}.
 *
 * <p>Every operation is scoped to the requesting user via the parent
 * project's ownership: a level id alone is never sufficient to read or
 * modify it — the caller must also own the project it belongs to.
 */
@Service
@Transactional(readOnly = true)
public class LevelService {

    private final LevelRepository levelRepository;
    private final ProjectRepository projectRepository;

    public LevelService(LevelRepository levelRepository, ProjectRepository projectRepository) {
        this.levelRepository = levelRepository;
        this.projectRepository = projectRepository;
    }

    @Transactional
    public LevelResponse createLevel(UUID projectId, UUID requesterId, CreateLevelRequest request) {
        Project project = findOwnedProjectOrThrow(projectId, requesterId);

        Level level = Level.builder()
                .project(project)
                .name(request.name())
                .elevationMm(request.elevationMm() != null ? request.elevationMm() : BigDecimal.ZERO)
                .orderIndex(request.orderIndex() != null ? request.orderIndex() : 0)
                .build();

        return toResponse(levelRepository.save(level));
    }

    @Transactional
    public LevelResponse updateLevel(UUID id, UUID requesterId, UpdateLevelRequest request) {
        Level level = findOwnedLevelOrThrow(id, requesterId);

        level.setName(request.name());
        level.setElevationMm(request.elevationMm());
        level.setOrderIndex(request.orderIndex());
        level.setVisible(request.visible());

        return toResponse(level);
    }

    public LevelResponse getLevel(UUID id, UUID requesterId) {
        return toResponse(findOwnedLevelOrThrow(id, requesterId));
    }

    public List<LevelResponse> listLevelsByProject(UUID projectId, UUID requesterId) {
        findOwnedProjectOrThrow(projectId, requesterId);
        return levelRepository.findByProjectIdOrderByOrderIndexAsc(projectId).stream()
                .map(LevelService::toResponse)
                .toList();
    }

    @Transactional
    public void deleteLevel(UUID id, UUID requesterId) {
        Level level = findOwnedLevelOrThrow(id, requesterId);
        levelRepository.delete(level);
    }

    /**
     * Loads a level the requester is entitled to see or modify, verified
     * transitively through its parent project's ownership. Missing ids
     * yield 404; ids belonging to someone else's project yield 403 — never
     * leaking existence to a non-owner. Package-visible so {@link RoomService}
     * can reuse the same ownership check for level-scoped room operations.
     */
    Level findOwnedLevelOrThrow(UUID id, UUID requesterId) {
        Level level = findLevelOrThrow(id);
        if (!level.getProject().getOwner().getId().equals(requesterId)) {
            throw new AccessDeniedException("You do not have access to this level");
        }
        return level;
    }

    private Project findOwnedProjectOrThrow(UUID projectId, UUID requesterId) {
        Project project = findProjectOrThrow(projectId);
        if (!project.getOwner().getId().equals(requesterId)) {
            throw new AccessDeniedException("You do not have access to this project");
        }
        return project;
    }

    private Level findLevelOrThrow(UUID id) {
        return levelRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Level not found: " + id));
    }

    private Project findProjectOrThrow(UUID projectId) {
        return projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found: " + projectId));
    }

    private static LevelResponse toResponse(Level level) {
        return new LevelResponse(
                level.getId(),
                level.getProject().getId(),
                level.getName(),
                level.getElevationMm(),
                level.getOrderIndex(),
                level.isVisible(),
                level.getMinXMm(),
                level.getMinYMm(),
                level.getMaxXMm(),
                level.getMaxYMm(),
                level.getCreatedAt(),
                level.getUpdatedAt()
        );
    }
}

