package com.naydiko.backend.service;

import com.naydiko.backend.domain.entity.Project;
import com.naydiko.backend.domain.entity.User;
import com.naydiko.backend.domain.repository.ProjectRepository;
import com.naydiko.backend.domain.repository.UserRepository;
import com.naydiko.backend.dto.request.CreateProjectRequest;
import com.naydiko.backend.dto.request.UpdateProjectRequest;
import com.naydiko.backend.dto.response.ProjectResponse;
import com.naydiko.backend.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Business logic for managing {@link Project}s.
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
    public ProjectResponse createProject(CreateProjectRequest request) {
        User owner = findUserOrThrow(request.ownerId());

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
    public ProjectResponse updateProject(UUID id, UpdateProjectRequest request) {
        Project project = findProjectOrThrow(id);

        project.setName(request.name());
        project.setDescription(request.description());
        project.setProjectType(request.projectType());
        project.setStatus(request.status());
        project.setBudgetMin(request.budgetMin());
        project.setBudgetMax(request.budgetMax());
        project.setCurrency(request.currency());

        return toResponse(project);
    }

    public ProjectResponse getProject(UUID id) {
        return toResponse(findProjectOrThrow(id));
    }

    public List<ProjectResponse> listProjectsByOwner(UUID ownerId) {
        return projectRepository.findByOwnerId(ownerId).stream()
                .map(ProjectService::toResponse)
                .toList();
    }

    @Transactional
    public void deleteProject(UUID id) {
        Project project = findProjectOrThrow(id);
        projectRepository.delete(project);
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

