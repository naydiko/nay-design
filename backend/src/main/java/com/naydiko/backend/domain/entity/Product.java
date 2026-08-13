package com.naydiko.backend.domain.entity;

import com.naydiko.backend.domain.enums.ProductStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.Hibernate;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * A catalog item sourced from a {@link Vendor}, placeable into rooms via
 * {@link FurniturePlacement}.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
        name = "product",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_product_vendor_external",
                columnNames = {"vendor_id", "external_id"}
        )
)
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vendor_id", nullable = false)
    private Vendor vendor;

    @Size(max = 120)
    @Column(name = "external_id", length = 120)
    private String externalId;

    @NotBlank
    @Size(max = 180)
    @Column(nullable = false, length = 180)
    private String name;

    @Size(max = 100)
    @Column(length = 100)
    private String sku;

    @NotBlank
    @Size(max = 120)
    @Column(nullable = false, length = 120)
    private String category;

    @Size(max = 160)
    @Column(length = 160)
    private String collection;

    @Size(max = 120)
    @Column(length = 120)
    private String style;

    @Size(max = 120)
    @Column(length = 120)
    private String material;

    @Size(max = 120)
    @Column(length = 120)
    private String color;

    @Positive
    @Column(name = "width_mm", precision = 10, scale = 2)
    private BigDecimal widthMm;

    @Positive
    @Column(name = "depth_mm", precision = 10, scale = 2)
    private BigDecimal depthMm;

    @Positive
    @Column(name = "height_mm", precision = 10, scale = 2)
    private BigDecimal heightMm;

    @PositiveOrZero
    @Column(name = "weight_grams", precision = 10, scale = 2)
    private BigDecimal weightGrams;

    @DecimalMin("0.00")
    @Column(name = "price_amount", precision = 12, scale = 2)
    private BigDecimal priceAmount;

    @Size(min = 3, max = 3)
    @Column(name = "price_currency", length = 3)
    private String priceCurrency;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ProductStatus status;

    /**
     * Media gallery for this product, ordered by {@link ProductMedia#getOrderIndex()}.
     */
    @Builder.Default
    @OneToMany(mappedBy = "product", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("orderIndex ASC")
    private List<ProductMedia> media = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * Adds a media item and keeps both sides of the bidirectional association in sync.
     */
    public void addMedia(ProductMedia productMedia) {
        media.add(productMedia);
        productMedia.setProduct(this);
    }

    /**
     * Removes a media item and keeps both sides of the bidirectional association in sync.
     */
    public void removeMedia(ProductMedia productMedia) {
        media.remove(productMedia);
        productMedia.setProduct(null);
    }

    @Override
    public final boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || Hibernate.getClass(this) != Hibernate.getClass(o)) {
            return false;
        }
        Product product = (Product) o;
        return id != null && Objects.equals(id, product.id);
    }

    @Override
    public final int hashCode() {
        return Hibernate.getClass(this).hashCode();
    }
}

