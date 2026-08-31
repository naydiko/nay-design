package com.naydiko.backend.controller;

import com.naydiko.backend.domain.entity.User;
import com.naydiko.backend.domain.enums.UserRole;
import com.naydiko.backend.domain.enums.UserStatus;
import com.naydiko.backend.domain.repository.UserRepository;
import com.naydiko.backend.security.CustomUserDetails;
import com.naydiko.backend.security.JwtService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies that the Stage 1 development seed data (see
 * {@code db/dev-seed/R__seed_dev_catalog.sql}) is actually reachable
 * through the public catalog API — i.e. that the fictional demo vendors
 * and products come back from {@code GET /api/vendors} and
 * {@code GET /api/products} once authenticated.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ProductCatalogSeedDataTest {

    private static final String TEST_EMAIL = "catalog-seed-test-user@example.com";
    private static final String TEST_PASSWORD = "SuperSecret123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    private String authToken;

    @AfterEach
    void cleanUp() {
        userRepository.findByEmail(TEST_EMAIL).ifPresent(userRepository::delete);
    }

    @Test
    void vendorsEndpoint_returnsSeededDemoVendors() throws Exception {
        mockMvc.perform(get("/api/vendors").header("Authorization", "Bearer " + authToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(3)))
                .andExpect(jsonPath("$[?(@.name == 'Nordbo Living')]").exists())
                .andExpect(jsonPath("$[?(@.name == 'Casa Milano')]").exists())
                .andExpect(jsonPath("$[?(@.name == 'Oakwell & Co.')]").exists());
    }

    @Test
    void productsEndpoint_returnsSeededDemoProductsWithRealisticData() throws Exception {
        mockMvc.perform(get("/api/products").header("Authorization", "Bearer " + authToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(25)))
                .andExpect(jsonPath("$[?(@.sku == 'NB-SF-KALLBY3')]").exists())
                .andExpect(jsonPath("$[?(@.sku == 'NB-SF-KALLBY3')].widthMm").value(org.hamcrest.Matchers.hasItem(2100.0)))
                .andExpect(jsonPath("$[?(@.sku == 'NB-SF-KALLBY3')].status").value(org.hamcrest.Matchers.hasItem("ACTIVE")))
                .andExpect(jsonPath("$[?(@.sku == 'NB-SF-KALLBY3')].primaryImageUrl").exists());
    }

    @Test
    void productsEndpoint_supportsCategoryFilterOverSeedData() throws Exception {
        mockMvc.perform(get("/api/products?category=Sofa").header("Authorization", "Bearer " + authToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(3)))
                .andExpect(jsonPath("$[*].category").value(org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.equalTo("Sofa"))));
    }

    private String authToken() {
        if (authToken == null) {
            User user = userRepository.findByEmail(TEST_EMAIL).orElseGet(() -> userRepository.save(
                    User.builder()
                            .email(TEST_EMAIL)
                            .displayName("Catalog Seed Test User")
                            .passwordHash(passwordEncoder.encode(TEST_PASSWORD))
                            .role(UserRole.CLIENT)
                            .status(UserStatus.ACTIVE)
                            .build()));
            authToken = jwtService.generateToken(new CustomUserDetails(user));
        }
        return authToken;
    }
}


