package ru.smetrix.fgis;

import java.math.BigDecimal;

public record FgisMaterialRecord(
        String code,
        String name,
        String unit,
        BigDecimal price,
        String regionCode,
        String quarter,
        BigDecimal consumptionRate
) {
}
