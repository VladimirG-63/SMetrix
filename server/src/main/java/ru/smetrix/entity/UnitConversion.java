package ru.smetrix.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Table(name = "unit_conversions",
        uniqueConstraints = @UniqueConstraint(name = "uk_unit_conversion_fgis",
                columnNames = "fgis_unit"))
@Data
@NoArgsConstructor
public class UnitConversion {
    @Id
    @jakarta.persistence.Convert(converter = ru.smetrix.config.UuidStringConverter.class)
    @Column(nullable = false, updatable = false, columnDefinition = "uuid")
    private String id;

    @NotBlank
    @Column(name = "fgis_unit", nullable = false, length = 30)
    private String fgisUnit;

    @NotBlank
    @Column(name = "app_unit", nullable = false, length = 30)
    private String appUnit;

    @DecimalMin(value = "0.0", inclusive = false)
    @Column(name = "conversion_factor", nullable = false, precision = 19, scale = 8)
    private BigDecimal conversionFactor;
}
