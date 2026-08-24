package com.naydiko.backend.service;

import com.naydiko.backend.domain.entity.User;
import com.naydiko.backend.domain.enums.UserRole;
import com.naydiko.backend.domain.repository.UserRepository;
import com.naydiko.backend.dto.request.CreateUserRequest;
import com.naydiko.backend.dto.request.UpdateUserRequest;
import com.naydiko.backend.dto.response.UserResponse;
import com.naydiko.backend.exception.DuplicateResourceException;
import com.naydiko.backend.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Business logic for managing {@link User} accounts.
 */
@Service
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional
    public UserResponse createUser(CreateUserRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new DuplicateResourceException("A user with email '" + request.email() + "' already exists");
        }

        User user = User.builder()
                .email(request.email())
                .displayName(request.displayName())
                .firstName(request.firstName())
                .lastName(request.lastName())
                .phoneNumber(request.phoneNumber())
                .role(request.role() != null ? request.role() : UserRole.CLIENT)
                .build();

        return toResponse(userRepository.save(user));
    }

    @Transactional
    public UserResponse updateUser(UUID id, UpdateUserRequest request) {
        User user = findUserOrThrow(id);

        user.setDisplayName(request.displayName());
        user.setFirstName(request.firstName());
        user.setLastName(request.lastName());
        user.setPhoneNumber(request.phoneNumber());

        return toResponse(user);
    }

    public UserResponse getUser(UUID id) {
        return toResponse(findUserOrThrow(id));
    }

    public List<UserResponse> listUsers() {
        return userRepository.findAll().stream()
                .map(UserService::toResponse)
                .toList();
    }

    @Transactional
    public void deleteUser(UUID id) {
        User user = findUserOrThrow(id);
        userRepository.delete(user);
    }

    private User findUserOrThrow(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + id));
    }

    private static UserResponse toResponse(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getDisplayName(),
                user.getFirstName(),
                user.getLastName(),
                user.getPhoneNumber(),
                user.getRole(),
                user.getStatus(),
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }
}



