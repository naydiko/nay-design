package com.naydiko.backend.service;

import com.naydiko.backend.domain.entity.User;
import com.naydiko.backend.domain.enums.UserRole;
import com.naydiko.backend.domain.enums.UserStatus;
import com.naydiko.backend.domain.repository.UserRepository;
import com.naydiko.backend.dto.request.LoginRequest;
import com.naydiko.backend.dto.request.RegisterRequest;
import com.naydiko.backend.dto.response.AuthResponse;
import com.naydiko.backend.dto.response.UserResponse;
import com.naydiko.backend.exception.DuplicateResourceException;
import com.naydiko.backend.exception.InvalidCredentialsException;
import com.naydiko.backend.security.CustomUserDetails;
import com.naydiko.backend.security.JwtService;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Business logic for user registration and login (Stage 1 authentication).
 */
@Service
@Transactional(readOnly = true)
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            AuthenticationManager authenticationManager) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.authenticationManager = authenticationManager;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new DuplicateResourceException("A user with email '" + request.email() + "' already exists");
        }

        User user = User.builder()
                .email(request.email())
                .displayName(request.displayName())
                .firstName(request.firstName())
                .lastName(request.lastName())
                .phoneNumber(request.phoneNumber())
                .passwordHash(passwordEncoder.encode(request.password()))
                .role(UserRole.CLIENT)
                .status(UserStatus.ACTIVE)
                .build();

        user = userRepository.save(user);

        String token = jwtService.generateToken(new CustomUserDetails(user));
        return AuthResponse.bearer(token, toResponse(user));
    }

    public AuthResponse login(LoginRequest request) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.email(), request.password()));
        } catch (BadCredentialsException ex) {
            throw new InvalidCredentialsException("Invalid email or password");
        }

        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new InvalidCredentialsException("Invalid email or password"));

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new InvalidCredentialsException("Account is not active");
        }

        String token = jwtService.generateToken(new CustomUserDetails(user));
        return AuthResponse.bearer(token, toResponse(user));
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

