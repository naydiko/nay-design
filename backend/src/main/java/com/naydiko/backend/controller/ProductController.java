package com.naydiko.backend.controller;

import com.naydiko.backend.dto.response.ProductResponse;
import com.naydiko.backend.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Read-only REST API for browsing catalog products.
 *
 * <p>End users select existing products to place into rooms (see
 * {@code FurniturePlacement}); they don't create, edit, or delete catalog
 * entries through the design app, so only browsing operations are exposed
 * here. Catalog management is a vendor/back-office concern.
 */
@Tag(name = "Products")
@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @Operation(summary = "Get a product by id")
    @GetMapping("/{id}")
    public ResponseEntity<ProductResponse> getProduct(@Parameter(description = "Product id") @PathVariable UUID id) {
        return ResponseEntity.ok(productService.getProduct(id));
    }

    @Operation(summary = "List products, optionally filtered by vendor and/or category")
    @GetMapping
    public ResponseEntity<List<ProductResponse>> listProducts(
            @Parameter(description = "Vendor id") @RequestParam(required = false) UUID vendorId,
            @Parameter(description = "Product category") @RequestParam(required = false) String category) {
        return ResponseEntity.ok(productService.listProducts(vendorId, category));
    }
}

