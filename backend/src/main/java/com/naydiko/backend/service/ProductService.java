package com.naydiko.backend.service;

import com.naydiko.backend.domain.entity.Product;
import com.naydiko.backend.domain.entity.Vendor;
import com.naydiko.backend.domain.repository.ProductRepository;
import com.naydiko.backend.domain.repository.VendorRepository;
import com.naydiko.backend.dto.request.CreateProductRequest;
import com.naydiko.backend.dto.request.UpdateProductRequest;
import com.naydiko.backend.dto.response.ProductResponse;
import com.naydiko.backend.exception.DuplicateResourceException;
import com.naydiko.backend.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Business logic for managing {@link Product} catalog items.
 */
@Service
@Transactional(readOnly = true)
public class ProductService {

    private final ProductRepository productRepository;
    private final VendorRepository vendorRepository;

    public ProductService(ProductRepository productRepository, VendorRepository vendorRepository) {
        this.productRepository = productRepository;
        this.vendorRepository = vendorRepository;
    }

    @Transactional
    public ProductResponse createProduct(CreateProductRequest request) {
        Vendor vendor = findVendorOrThrow(request.vendorId());

        if (request.externalId() != null
                && productRepository.findByVendorIdAndExternalId(request.vendorId(), request.externalId()).isPresent()) {
            throw new DuplicateResourceException(
                    "A product with external id '" + request.externalId() + "' already exists for this vendor");
        }

        Product product = Product.builder()
                .vendor(vendor)
                .externalId(request.externalId())
                .name(request.name())
                .sku(request.sku())
                .category(request.category())
                .collection(request.collection())
                .style(request.style())
                .material(request.material())
                .color(request.color())
                .widthMm(request.widthMm())
                .depthMm(request.depthMm())
                .heightMm(request.heightMm())
                .weightGrams(request.weightGrams())
                .priceAmount(request.priceAmount())
                .priceCurrency(request.priceCurrency())
                .status(request.status())
                .build();

        return toResponse(productRepository.save(product));
    }

    @Transactional
    public ProductResponse updateProduct(UUID id, UpdateProductRequest request) {
        Product product = findProductOrThrow(id);

        if (request.externalId() != null) {
            productRepository.findByVendorIdAndExternalId(product.getVendor().getId(), request.externalId())
                    .filter(existing -> !existing.getId().equals(id))
                    .ifPresent(existing -> {
                        throw new DuplicateResourceException(
                                "A product with external id '" + request.externalId() + "' already exists for this vendor");
                    });
        }

        product.setExternalId(request.externalId());
        product.setName(request.name());
        product.setSku(request.sku());
        product.setCategory(request.category());
        product.setCollection(request.collection());
        product.setStyle(request.style());
        product.setMaterial(request.material());
        product.setColor(request.color());
        product.setWidthMm(request.widthMm());
        product.setDepthMm(request.depthMm());
        product.setHeightMm(request.heightMm());
        product.setWeightGrams(request.weightGrams());
        product.setPriceAmount(request.priceAmount());
        product.setPriceCurrency(request.priceCurrency());
        product.setStatus(request.status());

        return toResponse(product);
    }

    public ProductResponse getProduct(UUID id) {
        return toResponse(findProductOrThrow(id));
    }

    public List<ProductResponse> listProductsByVendor(UUID vendorId) {
        return productRepository.findByVendorId(vendorId).stream()
                .map(ProductService::toResponse)
                .toList();
    }

    @Transactional
    public void deleteProduct(UUID id) {
        Product product = findProductOrThrow(id);
        productRepository.delete(product);
    }

    private Product findProductOrThrow(UUID id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + id));
    }

    private Vendor findVendorOrThrow(UUID vendorId) {
        return vendorRepository.findById(vendorId)
                .orElseThrow(() -> new ResourceNotFoundException("Vendor not found: " + vendorId));
    }

    private static ProductResponse toResponse(Product product) {
        return new ProductResponse(
                product.getId(),
                product.getVendor().getId(),
                product.getExternalId(),
                product.getName(),
                product.getSku(),
                product.getCategory(),
                product.getCollection(),
                product.getStyle(),
                product.getMaterial(),
                product.getColor(),
                product.getWidthMm(),
                product.getDepthMm(),
                product.getHeightMm(),
                product.getWeightGrams(),
                product.getPriceAmount(),
                product.getPriceCurrency(),
                product.getStatus(),
                product.getCreatedAt(),
                product.getUpdatedAt()
        );
    }
}

