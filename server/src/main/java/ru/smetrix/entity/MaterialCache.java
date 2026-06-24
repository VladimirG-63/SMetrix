package ru.smetrix.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import jakarta.validation.constraints.PositiveOrZero;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "material_cache",
        uniqueConstraints = @UniqueConstraint(name = "uk_material_code_region",
                columnNames = {"code", "region"}),
        indexes = {
                @Index(name = "idx_material_code", columnList = "code"),
                @Index(name = "idx_material_region_name", columnList = "region,name")
        })
public class MaterialCache {

    @Id
    @jakarta.persistence.Convert(converter = ru.smetrix.config.UuidStringConverter.class)
    @Column(nullable = false, updatable = false, columnDefinition = "uuid")
    private String id;

    @Column(nullable = false)
    private String code;

    @Column(nullable = false, length = 4000)
    private String name;

    private String unit;

    @Column(precision = 19, scale = 4)
    @PositiveOrZero
    private BigDecimal price;

    private String region;

    @Column(length = 7)
    private String quarter;

    @Column(precision = 19, scale = 4)
    @PositiveOrZero
    private BigDecimal consumptionRate;

    @Column(nullable = false)
    private Long lastUpdated;

    @Column(nullable = false, columnDefinition = "integer default 0")
    private int popularityScore;
}
