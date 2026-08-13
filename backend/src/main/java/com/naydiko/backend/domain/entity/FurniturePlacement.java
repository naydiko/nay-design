package com.naydiko.backend.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
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
import java.util.Objects;
import java.util.UUID;

/**
 * The placement of a {@link Product} instance within a {@link Room}, at a
 * given position, rotation, and scale.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "furniture_placement")
public class FurniturePlacement {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @NotNull
    @Column(name = "x_mm", nullable = false, precision = 12, scale = 4)
    @Builder.Default
    private BigDecimal xMm = BigDecimal.ZERO;

    @NotNull
    @Column(name = "y_mm", nullable = false, precision = 12, scale = 4)
    @Builder.Default
    private BigDecimal yMm = BigDecimal.ZERO;

    @NotNull
    @Column(name = "z_mm", nullable = false, precision = 12, scale = 4)
    @Builder.Default
    private BigDecimal zMm = BigDecimal.ZERO;

    @NotNull
    @DecimalMin("0.00")
    @Column(name = "rotation_angle", nullable = false, precision = 6, scale = 2)
    @Builder.Default
    private BigDecimal rotationAngle = BigDecimal.ZERO;

    @NotNull
    @DecimalMin("0.0001")
    @Column(nullable = false, precision = 8, scale = 4)
    @Builder.Default
    private BigDecimal scale = BigDecimal.ONE;

    @Column(name = "is_locked", nullable = false)
    @Builder.Default
    private boolean locked = false;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;


    @Override
    public final boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || Hibernate.getClass(this) != Hibernate.getClass(o)) {
            return false;
        }
        FurniturePlacement that = (FurniturePlacement) o;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public final int hashCode() {
        return Hibernate.getClass(this).hashCode();
    }
}

