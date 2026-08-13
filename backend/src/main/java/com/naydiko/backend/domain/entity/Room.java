package com.naydiko.backend.domain.entity;

import com.naydiko.backend.domain.enums.CeilingType;
import com.naydiko.backend.domain.enums.RoomType;
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
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
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
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * A named, typed room defined by the set of {@link Wall}s that enclose it
 * on a given {@link Level}. Hosts {@link FurniturePlacement}s.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "room")
public class Room {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "level_id", nullable = false)
    private Level level;

    @NotBlank
    @Size(max = 160)
    @Column(nullable = false, length = 160)
    private String name;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private RoomType type;

    @Size(max = 120)
    @Column(name = "floor_finish", length = 120)
    private String floorFinish;

    @Size(max = 120)
    @Column(name = "wall_finish", length = 120)
    private String wallFinish;

    @Size(max = 120)
    @Column(name = "ceiling_finish", length = 120)
    private String ceilingFinish;

    @Enumerated(EnumType.STRING)
    @Column(name = "ceiling_type", length = 60)
    private CeilingType ceilingType;

    @Positive
    @Column(name = "ceiling_height_mm", precision = 10, scale = 2)
    private BigDecimal ceilingHeightMm;

    @Builder.Default
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "room_wall",
            joinColumns = @JoinColumn(name = "room_id"),
            inverseJoinColumns = @JoinColumn(name = "wall_id")
    )
    private Set<Wall> walls = new HashSet<>();

    @Builder.Default
    @OneToMany(mappedBy = "room", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<FurniturePlacement> furniturePlacements = new HashSet<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * Adds a bordering wall and keeps the {@link Wall#getRooms()} inverse
     * side in sync.
     */
    public void addWall(Wall wall) {
        walls.add(wall);
        wall.getRooms().add(this);
    }

    /**
     * Removes a bordering wall and keeps the {@link Wall#getRooms()} inverse
     * side in sync.
     */
    public void removeWall(Wall wall) {
        walls.remove(wall);
        wall.getRooms().remove(this);
    }

    /**
     * Adds a furniture placement and keeps both sides of the bidirectional
     * association in sync.
     */
    public void addFurniturePlacement(FurniturePlacement placement) {
        furniturePlacements.add(placement);
        placement.setRoom(this);
    }

    /**
     * Removes a furniture placement and keeps both sides of the bidirectional
     * association in sync.
     */
    public void removeFurniturePlacement(FurniturePlacement placement) {
        furniturePlacements.remove(placement);
        placement.setRoom(null);
    }

    @Override
    public final boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || Hibernate.getClass(this) != Hibernate.getClass(o)) {
            return false;
        }
        Room room = (Room) o;
        return id != null && Objects.equals(id, room.id);
    }

    @Override
    public final int hashCode() {
        return Hibernate.getClass(this).hashCode();
    }
}

