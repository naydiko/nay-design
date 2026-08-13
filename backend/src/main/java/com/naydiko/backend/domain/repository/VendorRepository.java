package com.naydiko.backend.domain.repository;

import com.naydiko.backend.domain.entity.Vendor;
import com.naydiko.backend.domain.enums.VendorStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for the {@link Vendor} aggregate root.
 */
public interface VendorRepository extends JpaRepository<Vendor, UUID> {

    Optional<Vendor> findByName(String name);

    List<Vendor> findByStatus(VendorStatus status);
}

