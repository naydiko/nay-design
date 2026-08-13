package com.naydiko.backend.domain.entity;

import com.naydiko.backend.domain.enums.WallKind;
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
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
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
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * A straight wall segment spanning between two {@link Node}s on a {@link Level}.
 * May host zero or more {@link Opening}s and border one or two {@link Room}s.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "wall")
public class Wall {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "level_id", nullable = false)
    private Level level;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "start_node_id", nullable = false)
    private Node startNode;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "end_node_id", nullable = false)
    private Node endNode;

    @NotNull
    @Positive
    @Column(name = "thickness_mm", nullable = false, precision = 10, scale = 2)
    private BigDecimal thicknessMm;

    @NotNull
    @Positive
    @Column(name = "height_mm", nullable = false, precision = 10, scale = 2)
    private BigDecimal heightMm;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private WallKind kind;

    @Builder.Default
    @OneToMany(mappedBy = "wall", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<Opening> openings = new HashSet<>();

    @Builder.Default
    @ManyToMany(mappedBy = "walls", fetch = FetchType.LAZY)
    private Set<Room> rooms = new HashSet<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * Adds an opening and keeps both sides of the bidirectional association in sync.
     */
    public void addOpening(Opening opening) {
        openings.add(opening);
        opening.setWall(this);
    }

    /**
     * Removes an opening and keeps both sides of the bidirectional association in sync.
     */
    public void removeOpening(Opening opening) {
        openings.remove(opening);
        opening.setWall(null);
    }

    @Override
    public final boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || Hibernate.getClass(this) != Hibernate.getClass(o)) {
            return false;
        }
        Wall wall = (Wall) o;
        return id != null && Objects.equals(id, wall.id);
    }

    @Override
    public final int hashCode() {
        return Hibernate.getClass(this).hashCode();
    }
}



