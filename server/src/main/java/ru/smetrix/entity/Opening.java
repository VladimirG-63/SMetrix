package ru.smetrix.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;
import jakarta.validation.constraints.PositiveOrZero;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "openings", indexes = {
        @Index(name = "idx_openings_room_id", columnList = "project_room_id"),
        @Index(name = "idx_openings_updated_at", columnList = "updated_at")
})
public class Opening {
    @Id
    @Column(nullable = false, updatable = false, columnDefinition = "uuid")
    private UUID id;

    @Column(name = "project_room_id", nullable = false, columnDefinition = "uuid")
    private UUID projectRoomId;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_room_id", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_openings_room"))
    private ProjectRoom projectRoom;

    @Column(nullable = false)
    private String type;

    @Column(precision = 19, scale = 6)
    @PositiveOrZero
    private BigDecimal width;

    @Column(precision = 19, scale = 6)
    @PositiveOrZero
    private BigDecimal height;

    @Column(precision = 19, scale = 6)
    @PositiveOrZero
    private BigDecimal depth;

    private String placementType;

    @Column(nullable = false)
    private Long version;

    @Version
    @Column(name = "lock_version", nullable = false, columnDefinition = "bigint default 0")
    private Long lockVersion;

    @Column(nullable = false)
    private Long createdAt;

    @Column(nullable = false)
    private Long updatedAt;

    private Long deletedAt;
}
