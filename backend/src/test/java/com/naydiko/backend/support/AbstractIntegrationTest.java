package com.naydiko.backend.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.naydiko.backend.domain.entity.User;
import com.naydiko.backend.domain.enums.UserRole;
import com.naydiko.backend.domain.enums.UserStatus;
import com.naydiko.backend.domain.repository.UserRepository;
import com.naydiko.backend.security.CustomUserDetails;
import com.naydiko.backend.security.JwtService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base class for Stage 1 Spring Boot integration tests.
 *
 * <p>Boots the full application context ({@code @SpringBootTest} +
 * {@code MockMvc}) against a real, disposable PostgreSQL instance started
 * via Testcontainers, rather than mocking the persistence layer or using an
 * in-memory database — so tests exercise the same Flyway migrations, JPA
 * mappings, and PostgreSQL-specific behavior (types, constraints, cascade
 * rules) that production uses.
 *
 * <p>The container is created with the "singleton container" pattern: a
 * plain {@code static} field, started eagerly in a static initializer
 * (never explicitly stopped — Testcontainers' Ryuk reaper cleans it up when
 * the JVM exits) rather than JUnit-managed via {@code @Container}. This
 * keeps its connection details identical across every test class that
 * extends this one, which in turn lets Spring's test context cache reuse a
 * single {@link org.springframework.context.ApplicationContext} for the
 * whole suite instead of restarting it (and re-running Flyway) per class.
 * {@link ServiceConnection} wires the container's JDBC connection details
 * into the datasource auto-configuration, taking priority over the
 * {@code local} profile's hard-coded {@code spring.datasource.*} properties
 * (see {@code application-local.properties}) without needing a
 * {@code @DynamicPropertySource}.
 *
 * <p>The {@code local} Spring profile (active by default, see
 * {@code application.properties}) stays active for tests too, so
 * {@code ddl-auto=validate} and the {@code db/dev-seed} Flyway location
 * (demo vendors/products) behave exactly as they do for local development.
 */
@SpringBootTest
@AutoConfigureMockMvc
public abstract class AbstractIntegrationTest {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("udesign_test")
            .withUsername("postgres")
            .withPassword("postgres");

    static {
        POSTGRES.start();
    }

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected UserRepository userRepository;

    @Autowired
    protected PasswordEncoder passwordEncoder;

    @Autowired
    protected JwtService jwtService;

    protected final ObjectMapper objectMapper = new ObjectMapper();

    /** Creates (and persists) a new active {@link User} with a unique email. */
    protected User createActiveUser(String emailPrefix) {
        return userRepository.save(User.builder()
                .email(emailPrefix + "-" + java.util.UUID.randomUUID() + "@example.com")
                .displayName("Test User")
                .passwordHash(passwordEncoder.encode("SuperSecret123"))
                .role(UserRole.CLIENT)
                .status(UserStatus.ACTIVE)
                .build());
    }

    /** Mints a valid JWT for the given user, as if they had just logged in. */
    protected String tokenFor(User user) {
        return jwtService.generateToken(new CustomUserDetails(user));
    }

    /** Convenience: {@code Authorization: Bearer <token>} header value. */
    protected String bearer(String token) {
        return "Bearer " + token;
    }

    /** Creates a user and immediately mints a token for it. */
    protected String createUserAndToken(String emailPrefix) {
        return tokenFor(createActiveUser(emailPrefix));
    }
}

