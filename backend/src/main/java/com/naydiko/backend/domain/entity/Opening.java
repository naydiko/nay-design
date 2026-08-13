package com.naydiko.backend.domain.entity;

import com.naydiko.backend.domain.enums.OpeningDirection;
import com.naydiko.backend.domain.enums.OpeningSwing;
import com.naydiko.backend.domain.enums.OpeningType;
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
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
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
 * A door, window, or archway carved into a {@link Wall} at a given offset.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "opening")
public class Opening {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "wall_id", nullable = false)
    private Wall wall;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private OpeningType type;

    @NotNull
    @PositiveOrZero
    @Column(name = "offset_from_start_mm", nullable = false, precision = 10, scale = 2)
    private BigDecimal offsetFromStartMm;

    @NotNull
    @Positive
    @Column(name = "width_mm", nullable = false, precision = 10, scale = 2)
    private BigDecimal widthMm;

    @NotNull
    @Positive
    @Column(name = "height_mm", nullable = false, precision = 10, scale = 2)
    private BigDecimal heightMm;

    @NotNull
    @PositiveOrZero
    @Column(name = "sill_height_mm", nullable = false, precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal sillHeightMm = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private OpeningDirection direction;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private OpeningSwing swing;

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
        Opening opening = (Opening) o;
        return id != null && Objects.equals(id, opening.id);
    }

    @Override
    public final int hashCode() {
        return Hibernate.getClass(this).hashCode();
    }
}

