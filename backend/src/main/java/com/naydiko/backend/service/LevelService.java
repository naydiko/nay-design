package com.naydiko.backend.service;

import com.naydiko.backend.domain.entity.Level;
import com.naydiko.backend.domain.entity.Project;
import com.naydiko.backend.domain.repository.LevelRepository;
import com.naydiko.backend.domain.repository.ProjectRepository;
import com.naydiko.backend.dto.request.CreateLevelRequest;
import com.naydiko.backend.dto.request.UpdateLevelRequest;
import com.naydiko.backend.dto.response.LevelResponse;
import com.naydiko.backend.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Business logic for managing {@link Level}s within a {@link Project}.
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
    public LevelResponse createLevel(UUID projectId, CreateLevelRequest request) {
        Project project = findProjectOrThrow(projectId);

        Level level = Level.builder()
                .project(project)
                .name(request.name())
                .elevationMm(request.elevationMm() != null ? request.elevationMm() : BigDecimal.ZERO)
                .orderIndex(request.orderIndex() != null ? request.orderIndex() : 0)
                .build();

        return toResponse(levelRepository.save(level));
    }

    @Transactional
    public LevelResponse updateLevel(UUID id, UpdateLevelRequest request) {
        Level level = findLevelOrThrow(id);

        level.setName(request.name());
        level.setElevationMm(request.elevationMm());
        level.setOrderIndex(request.orderIndex());
        level.setVisible(request.visible());

        return toResponse(level);
    }

    public LevelResponse getLevel(UUID id) {
        return toResponse(findLevelOrThrow(id));
    }

    public List<LevelResponse> listLevelsByProject(UUID projectId) {
        return levelRepository.findByProjectIdOrderByOrderIndexAsc(projectId).stream()
                .map(LevelService::toResponse)
                .toList();
    }

    @Transactional
    public void deleteLevel(UUID id) {
        Level level = findLevelOrThrow(id);
        levelRepository.delete(level);
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

