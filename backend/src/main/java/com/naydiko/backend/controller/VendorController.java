package com.naydiko.backend.controller;

import com.naydiko.backend.dto.response.VendorResponse;
import com.naydiko.backend.service.VendorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Read-only REST API for browsing furniture/product vendors.
 *
 * <p>Vendor onboarding, editing, and deactivation are back-office concerns
 * handled outside the design app's user-facing workflow (and per
 * {@link com.naydiko.backend.domain.entity.Vendor}, vendors are never
 * hard-deleted), so only browsing operations are exposed here.
 */
@Tag(name = "Vendors")
@RestController
@RequestMapping("/api/vendors")
public class VendorController {

    private final VendorService vendorService;

    public VendorController(VendorService vendorService) {
        this.vendorService = vendorService;
    }

    @Operation(summary = "Get a vendor by id")
    @GetMapping("/{id}")
    public ResponseEntity<VendorResponse> getVendor(@Parameter(description = "Vendor id") @PathVariable UUID id) {
        return ResponseEntity.ok(vendorService.getVendor(id));
    }

    @Operation(summary = "List all vendors")
    @GetMapping
    public ResponseEntity<List<VendorResponse>> listVendors() {
        return ResponseEntity.ok(vendorService.listVendors());
    }
}

