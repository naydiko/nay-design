package com.naydiko.backend.controller;

import com.naydiko.backend.domain.entity.Product;
import com.naydiko.backend.domain.entity.Vendor;
import com.naydiko.backend.domain.enums.ProductStatus;
import com.naydiko.backend.domain.enums.VendorStatus;
import com.naydiko.backend.domain.repository.ProductRepository;
import com.naydiko.backend.domain.repository.VendorRepository;
import com.naydiko.backend.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.UUID;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for the read-only catalog API
 * ({@code /api/vendors}, {@code /api/products}), using freshly-created
 * fixtures rather than the dev-seed data so assertions don't depend on its
 * contents (see {@link ProductCatalogSeedDataTest} for seed-data-specific
 * checks).
 */
class CatalogControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private VendorRepository vendorRepository;
    @Autowired
    private ProductRepository productRepository;

    @Test
    void listVendors_includesFreshlyCreatedVendor() throws Exception {
        Vendor vendor = createVendor("Catalog Test Vendor " + UUID.randomUUID());
        String token = bearer(createUserAndToken("catalog-caller"));

        mockMvc.perform(get("/api/vendors").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id").value(hasItem(vendor.getId().toString())));
    }

    @Test
    void getVendor_returnsVendorById() throws Exception {
        Vendor vendor = createVendor("Catalog Test Vendor " + UUID.randomUUID());
        String token = bearer(createUserAndToken("catalog-caller"));

        mockMvc.perform(get("/api/vendors/{id}", vendor.getId()).header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(vendor.getId().toString()))
                .andExpect(jsonPath("$.name").value(vendor.getName()))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void getVendor_unknownId_returnsNotFound() throws Exception {
        mockMvc.perform(get("/api/vendors/{id}", UUID.randomUUID())
                        .header("Authorization", bearer(createUserAndToken("catalog-caller"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void listProducts_includesFreshlyCreatedProduct() throws Exception {
        Vendor vendor = createVendor("Catalog Test Vendor " + UUID.randomUUID());
        Product product = createProduct(vendor, "Test Armchair " + UUID.randomUUID(), "Chair");
        String token = bearer(createUserAndToken("catalog-caller"));

        mockMvc.perform(get("/api/products").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id").value(hasItem(product.getId().toString())));
    }

    @Test
    void listProducts_filtersByVendorId() throws Exception {
        Vendor vendor = createVendor("Filter Vendor " + UUID.randomUUID());
        Product product = createProduct(vendor, "Filtered Product " + UUID.randomUUID(), "Table");
        Vendor otherVendor = createVendor("Other Vendor " + UUID.randomUUID());
        createProduct(otherVendor, "Other Product " + UUID.randomUUID(), "Table");
        String token = bearer(createUserAndToken("catalog-caller"));

        mockMvc.perform(get("/api/products").queryParam("vendorId", vendor.getId().toString())
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id").value(hasItem(product.getId().toString())))
                .andExpect(jsonPath("$[*].vendorId").value(org.hamcrest.Matchers.everyItem(
                        org.hamcrest.Matchers.equalTo(vendor.getId().toString()))));
    }

    @Test
    void getProduct_returnsProductById() throws Exception {
        Vendor vendor = createVendor("Catalog Test Vendor " + UUID.randomUUID());
        Product product = createProduct(vendor, "Test Sofa " + UUID.randomUUID(), "Sofa");
        String token = bearer(createUserAndToken("catalog-caller"));

        mockMvc.perform(get("/api/products/{id}", product.getId()).header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(product.getId().toString()))
                .andExpect(jsonPath("$.vendorId").value(vendor.getId().toString()))
                .andExpect(jsonPath("$.category").value("Sofa"))
                .andExpect(jsonPath("$.widthMm").value(1800.0));
    }

    @Test
    void getProduct_unknownId_returnsNotFound() throws Exception {
        mockMvc.perform(get("/api/products/{id}", UUID.randomUUID())
                        .header("Authorization", bearer(createUserAndToken("catalog-caller"))))
                .andExpect(status().isNotFound());
    }

    private Vendor createVendor(String name) {
        return vendorRepository.save(Vendor.builder()
                .name(name)
                .status(VendorStatus.ACTIVE)
                .build());
    }

    private Product createProduct(Vendor vendor, String name, String category) {
        return productRepository.save(Product.builder()
                .vendor(vendor)
                .name(name)
                .category(category)
                .widthMm(new BigDecimal("1800.00"))
                .depthMm(new BigDecimal("800.00"))
                .heightMm(new BigDecimal("850.00"))
                .status(ProductStatus.ACTIVE)
                .build());
    }
}

