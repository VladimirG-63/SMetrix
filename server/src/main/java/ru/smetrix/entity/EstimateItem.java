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
@Table(name = "estimate_items", indexes = {
        @Index(name = "idx_estimate_items_room_id", columnList = "project_room_id"),
        @Index(name = "idx_estimate_items_fgis_code", columnList = "fgis_code"),
        @Index(name = "idx_estimate_items_updated_at", columnList = "updated_at")
})
public class EstimateItem {

    @Id
    @Column(nullable = false, updatable = false, columnDefinition = "uuid")
    private UUID id;

    @Column(name = "project_room_id", nullable = false, columnDefinition = "uuid")
    private UUID projectRoomId;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_room_id", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_estimate_items_room"))
    private ProjectRoom projectRoom;

    private String materialId;

    private String fgisCode;

    @Column(nullable = false, length = 4000)
    private String name;

    private String unitMeasure;

    @Column(precision = 19, scale = 4)
    @PositiveOrZero
    private BigDecimal basePrice;

    @Column(precision = 19, scale = 6)
    @PositiveOrZero
    private BigDecimal consumptionRate;

    @Column(precision = 19, scale = 6)
    @PositiveOrZero
    private BigDecimal quantity;

    private String unit;

    @Column(precision = 19, scale = 4)
    @PositiveOrZero
    private BigDecimal price;

    @Column(precision = 19, scale = 4)
    @PositiveOrZero
    private BigDecimal finalPrice;

    @Column(precision = 19, scale = 2)
    @PositiveOrZero
    private BigDecimal total;

    @Column(precision = 19, scale = 2)
    @PositiveOrZero
    private BigDecimal totalPrice;

    @Column(nullable = false)
    private String type;

    private String calculationMethod;

    @Column(precision = 19, scale = 4)
    private BigDecimal wastePercent;

    private Integer layers;

    @Column(precision = 19, scale = 4)
    private BigDecimal thicknessMeters;

    @Column(precision = 19, scale = 6)
    private BigDecimal manualQuantity;

    @Column(precision = 19, scale = 6)
    private BigDecimal coveragePerPiece;

    @Column(precision = 19, scale = 6)
    private BigDecimal coveragePerPackage;

    @Column(precision = 19, scale = 6)
    private BigDecimal packageSize;

    @Column(length = 1000)
    private String formulaDescription;

    private String status;

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
