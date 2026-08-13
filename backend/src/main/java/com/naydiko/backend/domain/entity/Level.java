package com.naydiko.backend.domain.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
 * A single floor/storey of a {@link Project}, containing the geometric
 * graph ({@link Node}, {@link Wall}) and {@link Room} definitions.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "level")
public class Level {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @NotBlank
    @Size(max = 120)
    @Column(nullable = false, length = 120)
    private String name;

    @NotNull
    @Column(name = "elevation_mm", nullable = false, precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal elevationMm = BigDecimal.ZERO;

    @NotNull
    @Column(name = "order_index", nullable = false)
    @Builder.Default
    private Integer orderIndex = 0;

    @Column(name = "is_visible", nullable = false)
    @Builder.Default
    private boolean visible = true;

    @Column(name = "min_x_mm", precision = 12, scale = 2)
    private BigDecimal minXMm;

    @Column(name = "min_y_mm", precision = 12, scale = 2)
    private BigDecimal minYMm;

    @Column(name = "max_x_mm", precision = 12, scale = 2)
    private BigDecimal maxXMm;

    @Column(name = "max_y_mm", precision = 12, scale = 2)
    private BigDecimal maxYMm;

    /**
     * Points of the floorplan graph. Intentionally NOT cascaded for removal
     * here: {@link Wall} (a sibling collection) references nodes via
     * {@code start_node_id}/{@code end_node_id} with {@code ON DELETE RESTRICT}.
     * Cascading both {@code nodes} and {@code walls} from Level in Java gives
     * Hibernate no guaranteed deletion order between the two collections,
     * which can attempt to delete a Node while a Wall still references it.
     * Walls (and their Openings) are deleted first via their own cascade;
     * once the Level row itself is removed, PostgreSQL's
     * {@code ON DELETE CASCADE} on {@code node.level_id} cleans up the
     * now-unreferenced nodes automatically and safely.
     */
    @Builder.Default
    @OneToMany(mappedBy = "level", fetch = FetchType.LAZY)
    private Set<Node> nodes = new HashSet<>();

    @Builder.Default
    @OneToMany(mappedBy = "level", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<Wall> walls = new HashSet<>();

    @Builder.Default
    @OneToMany(mappedBy = "level", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<Room> rooms = new HashSet<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * Adds a node and keeps both sides of the bidirectional association in sync.
     */
    public void addNode(Node node) {
        nodes.add(node);
        node.setLevel(this);
    }

    /**
     * Removes a node and keeps both sides of the bidirectional association in sync.
     */
    public void removeNode(Node node) {
        nodes.remove(node);
        node.setLevel(null);
    }

    /**
     * Adds a wall and keeps both sides of the bidirectional association in sync.
     */
    public void addWall(Wall wall) {
        walls.add(wall);
        wall.setLevel(this);
    }

    /**
     * Removes a wall and keeps both sides of the bidirectional association in sync.
     */
    public void removeWall(Wall wall) {
        walls.remove(wall);
        wall.setLevel(null);
    }

    /**
     * Adds a room and keeps both sides of the bidirectional association in sync.
     */
    public void addRoom(Room room) {
        rooms.add(room);
        room.setLevel(this);
    }

    /**
     * Removes a room and keeps both sides of the bidirectional association in sync.
     */
    public void removeRoom(Room room) {
        rooms.remove(room);
        room.setLevel(null);
    }

    @Override
    public final boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || Hibernate.getClass(this) != Hibernate.getClass(o)) {
            return false;
        }
        Level level = (Level) o;
        return id != null && Objects.equals(id, level.id);
    }

    @Override
    public final int hashCode() {
        return Hibernate.getClass(this).hashCode();
    }
}

