package com.naydiko.backend.service;

import com.naydiko.backend.domain.entity.Vendor;
import com.naydiko.backend.domain.repository.VendorRepository;
import com.naydiko.backend.dto.request.CreateVendorRequest;
import com.naydiko.backend.dto.request.UpdateVendorRequest;
import com.naydiko.backend.dto.response.VendorResponse;
import com.naydiko.backend.exception.DuplicateResourceException;
import com.naydiko.backend.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Business logic for managing {@link Vendor} suppliers.
 */
@Service
@Transactional(readOnly = true)
public class VendorService {

    private final VendorRepository vendorRepository;

    public VendorService(VendorRepository vendorRepository) {
        this.vendorRepository = vendorRepository;
    }

    @Transactional
    public VendorResponse createVendor(CreateVendorRequest request) {
        if (vendorRepository.findByName(request.name()).isPresent()) {
            throw new DuplicateResourceException("A vendor named '" + request.name() + "' already exists");
        }

        Vendor vendor = Vendor.builder()
                .name(request.name())
                .country(request.country())
                .website(request.website())
                .logoUrl(request.logoUrl())
                .status(request.status())
                .build();

        return toResponse(vendorRepository.save(vendor));
    }

    @Transactional
    public VendorResponse updateVendor(UUID id, UpdateVendorRequest request) {
        Vendor vendor = findVendorOrThrow(id);

        vendorRepository.findByName(request.name())
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> {
                    throw new DuplicateResourceException("A vendor named '" + request.name() + "' already exists");
                });

        vendor.setName(request.name());
        vendor.setCountry(request.country());
        vendor.setWebsite(request.website());
        vendor.setLogoUrl(request.logoUrl());
        vendor.setStatus(request.status());

        return toResponse(vendor);
    }

    public VendorResponse getVendor(UUID id) {
        return toResponse(findVendorOrThrow(id));
    }

    public List<VendorResponse> listVendors() {
        return vendorRepository.findAll().stream()
                .map(VendorService::toResponse)
                .toList();
    }

    @Transactional
    public void deleteVendor(UUID id) {
        Vendor vendor = findVendorOrThrow(id);
        vendorRepository.delete(vendor);
    }

    private Vendor findVendorOrThrow(UUID id) {
        return vendorRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Vendor not found: " + id));
    }

    private static VendorResponse toResponse(Vendor vendor) {
        return new VendorResponse(
                vendor.getId(),
                vendor.getName(),
                vendor.getCountry(),
                vendor.getWebsite(),
                vendor.getLogoUrl(),
                vendor.getStatus(),
                vendor.getCreatedAt(),
                vendor.getUpdatedAt()
        );
    }
}

