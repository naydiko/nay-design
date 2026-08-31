package com.naydiko.backend.service;

import com.naydiko.backend.domain.entity.Project;
import com.naydiko.backend.domain.entity.User;
import com.naydiko.backend.domain.repository.ProjectRepository;
import com.naydiko.backend.domain.repository.UserRepository;
import com.naydiko.backend.dto.request.CreateProjectRequest;
import com.naydiko.backend.dto.request.UpdateProjectRequest;
import com.naydiko.backend.dto.response.ProjectResponse;
import com.naydiko.backend.exception.ResourceNotFoundException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Business logic for managing {@link Project}s. Every read/write is scoped
 * to the requesting owner: a user attempting to access another user's
 * project (even by guessing/changing an id) gets a 403, not the resource.
 */
@Service
@Transactional(readOnly = true)
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;

    public ProjectService(ProjectRepository projectRepository, UserRepository userRepository) {
        this.projectRepository = projectRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public ProjectResponse createProject(UUID ownerId, CreateProjectRequest request) {
        User owner = findUserOrThrow(ownerId);

        Project project = Project.builder()
                .owner(owner)
                .name(request.name())
                .description(request.description())
                .projectType(request.projectType())
                .budgetMin(request.budgetMin())
                .budgetMax(request.budgetMax())
                .currency(request.currency())
                .build();

        return toResponse(projectRepository.save(project));
    }

    @Transactional
    public ProjectResponse updateProject(UUID id, UUID requesterId, UpdateProjectRequest request) {
        Project project = findOwnedProjectOrThrow(id, requesterId);

        project.setName(request.name());
        project.setDescription(request.description());
        project.setProjectType(request.projectType());
        project.setStatus(request.status());
        project.setBudgetMin(request.budgetMin());
        project.setBudgetMax(request.budgetMax());
        project.setCurrency(request.currency());

        return toResponse(project);
    }

    public ProjectResponse getProjectForOwner(UUID id, UUID requesterId) {
        return toResponse(findOwnedProjectOrThrow(id, requesterId));
    }

    public List<ProjectResponse> listProjectsByOwner(UUID ownerId) {
        return projectRepository.findByOwnerId(ownerId).stream()
                .map(ProjectService::toResponse)
                .toList();
    }

    @Transactional
    public void deleteProject(UUID id, UUID requesterId) {
        Project project = findOwnedProjectOrThrow(id, requesterId);
        projectRepository.delete(project);
    }

    /**
     * Loads a project the requester is entitled to see or modify. Returns
     * {@link ResourceNotFoundException} for a missing id and
     * {@link AccessDeniedException} when the project exists but belongs to
     * someone else — never leaking whether the id exists to a non-owner.
     */
    private Project findOwnedProjectOrThrow(UUID id, UUID requesterId) {
        Project project = findProjectOrThrow(id);
        if (!project.getOwner().getId().equals(requesterId)) {
            throw new AccessDeniedException("You do not have access to this project");
        }
        return project;
    }

    private Project findProjectOrThrow(UUID id) {
        return projectRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found: " + id));
    }

    private User findUserOrThrow(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
    }


    private static ProjectResponse toResponse(Project project) {
        return new ProjectResponse(
                project.getId(),
                project.getOwner().getId(),
                project.getName(),
                project.getDescription(),
                project.getProjectType(),
                project.getStatus(),
                project.getBudgetMin(),
                project.getBudgetMax(),
                project.getCurrency(),
                project.getCreatedAt(),
                project.getUpdatedAt()
        );
    }
}

