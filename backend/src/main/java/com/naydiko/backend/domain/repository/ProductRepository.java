package com.naydiko.backend.domain.repository;

import com.naydiko.backend.domain.entity.Product;
import com.naydiko.backend.domain.enums.ProductStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for the {@link Product} aggregate root.
 */
public interface ProductRepository extends JpaRepository<Product, UUID> {

    List<Product> findByVendorId(UUID vendorId);

    Optional<Product> findByVendorIdAndExternalId(UUID vendorId, String externalId);

    List<Product> findByStatus(ProductStatus status);
}

