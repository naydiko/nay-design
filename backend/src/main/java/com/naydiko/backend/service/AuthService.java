package com.naydiko.backend.service;

import com.naydiko.backend.domain.entity.User;
import com.naydiko.backend.domain.enums.AuthProvider;
import com.naydiko.backend.domain.enums.UserRole;
import com.naydiko.backend.domain.enums.UserStatus;
import com.naydiko.backend.domain.repository.UserRepository;
import com.naydiko.backend.dto.request.ChangePasswordRequest;
import com.naydiko.backend.dto.request.ForgotPasswordRequest;
import com.naydiko.backend.dto.request.GoogleLoginRequest;
import com.naydiko.backend.dto.request.LoginRequest;
import com.naydiko.backend.dto.request.RegisterRequest;
import com.naydiko.backend.dto.request.ResetPasswordRequest;
import com.naydiko.backend.dto.response.AuthResponse;
import com.naydiko.backend.dto.response.UserResponse;
import com.naydiko.backend.exception.DuplicateResourceException;
import com.naydiko.backend.exception.InvalidCredentialsException;
import com.naydiko.backend.security.CustomUserDetails;
import com.naydiko.backend.security.GoogleIdTokenVerifierService;
import com.naydiko.backend.security.JwtService;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Business logic for user registration and login (Stage 1 authentication),
 * including Google sign-in, change/forgot/reset password and email
 * verification triggering.
 */
@Service
@Transactional(readOnly = true)
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final GoogleIdTokenVerifierService googleIdTokenVerifierService;
    private final PasswordResetService passwordResetService;
    private final EmailVerificationService emailVerificationService;
    private final String frontendBaseUrl;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            AuthenticationManager authenticationManager,
            GoogleIdTokenVerifierService googleIdTokenVerifierService,
            PasswordResetService passwordResetService,
            EmailVerificationService emailVerificationService,
            @Value("${app.frontend.base-url}") String frontendBaseUrl) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.authenticationManager = authenticationManager;
        this.googleIdTokenVerifierService = googleIdTokenVerifierService;
        this.passwordResetService = passwordResetService;
        this.emailVerificationService = emailVerificationService;
        this.frontendBaseUrl = frontendBaseUrl;
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
        emailVerificationService.sendVerificationEmail(user, frontendBaseUrl);

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

    /**
     * Authenticates via a verified Google ID token. Creates a new user on
     * first sign-in, or links to an existing LOCAL account with the same
     * normalized email (never creating a duplicate user).
     */
    @Transactional
    public AuthResponse loginWithGoogle(GoogleLoginRequest request) {
        GoogleIdToken.Payload payload = googleIdTokenVerifierService.verify(request.idToken());
        String email = payload.getEmail() == null ? null : payload.getEmail().toLowerCase();
        String googleId = payload.getSubject();

        if (email == null || Boolean.FALSE.equals(payload.getEmailVerified())) {
            throw new InvalidCredentialsException("Google account email is not verified");
        }

        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) {
            String name = (String) payload.get("name");
            user = User.builder()
                    .email(email)
                    .displayName(name != null && !name.isBlank() ? name : email)
                    .firstName((String) payload.get("given_name"))
                    .lastName((String) payload.get("family_name"))
                    .authProvider(AuthProvider.GOOGLE)
                    .googleId(googleId)
                    .role(UserRole.CLIENT)
                    .status(UserStatus.ACTIVE)
                    .emailVerifiedAt(Instant.now())
                    .build();
            user = userRepository.save(user);
        } else if (user.getGoogleId() == null) {
            // Link existing LOCAL account to this Google identity.
            user.setGoogleId(googleId);
            if (user.getEmailVerifiedAt() == null) {
                user.setEmailVerifiedAt(Instant.now());
            }
        }

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new InvalidCredentialsException("Account is not active");
        }

        String token = jwtService.generateToken(new CustomUserDetails(user));
        return AuthResponse.bearer(token, toResponse(user));
    }

    @Transactional
    public void changePassword(UUID userId, ChangePasswordRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new InvalidCredentialsException("Invalid user"));

        if (user.getPasswordHash() == null
                || !passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new InvalidCredentialsException("Current password is incorrect");
        }

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
    }

    @Transactional
    public void forgotPassword(ForgotPasswordRequest request) {
        passwordResetService.requestReset(request.email(), frontendBaseUrl);
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        passwordResetService.resetPassword(request.token(), request.newPassword());
    }

    @Transactional
    public void verifyEmail(String token) {
        emailVerificationService.verifyEmail(token);
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

