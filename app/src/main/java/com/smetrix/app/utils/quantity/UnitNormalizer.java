package com.smetrix.app.utils.quantity;

import androidx.annotation.NonNull;

import java.math.BigDecimal;

public class UnitNormalizer {

    public static class NormalizationResult {
        public final UnitGroup group;
        public final String canonicalUnit;

        /**
         * factorToCanonical означает: 1 единица ФГИС = factorToCanonical canonical units.
         * Пример: 1 т (ФГИС) = 1000 кг (canonical).
         * Следовательно, fgisPriceQuantity = displayQuantity / factorToCanonical.
         * Пример: 500 кг / 1000 = 0.5 т.
         */
        public final BigDecimal factorToCanonical;

        public NormalizationResult(UnitGroup group, String canonicalUnit, BigDecimal factorToCanonical) {
            this.group = group;
            this.canonicalUnit = canonicalUnit;
            this.factorToCanonical = factorToCanonical;
        }
    }

    @NonNull
    public static NormalizationResult normalize(String unitMeasure) {
        if (unitMeasure == null || unitMeasure.trim().isEmpty()) {
            return new NormalizationResult(UnitGroup.UNKNOWN, "UNKNOWN", BigDecimal.ONE);
        }

        String unit = unitMeasure.trim().toLowerCase();

        // AREA
        if (unit.matches("^(м²|м2|кв\\.?\\s*м\\.?|м\\.?\\s*кв\\.?|квадратный\\s+метр|квадратные\\s+метры)$")) {
            return new NormalizationResult(UnitGroup.AREA, "M2", BigDecimal.ONE);
        }
        if (unit.matches("^(100\\s*м²|100\\s*м2|100\\s*кв\\.?\\s*м\\.?|100\\s*м\\.?\\s*кв\\.?)$")) {
            return new NormalizationResult(UnitGroup.AREA, "M2", new BigDecimal("100"));
        }

        // VOLUME
        if (unit.matches("^(м³|м3|куб\\.?\\s*м\\.?|м\\.?\\s*куб\\.?|кубический\\s+метр|кубические\\s+метры)$")) {
            return new NormalizationResult(UnitGroup.VOLUME, "M3", BigDecimal.ONE);
        }

        // LENGTH
        if (unit.matches("^(м|пог\\.?\\s*м\\.?|п\\.?м\\.?|метр|метры)$")) {
            return new NormalizationResult(UnitGroup.LENGTH, "M", BigDecimal.ONE);
        }

        // MASS
        if (unit.matches("^(кг|килограмм|килограммы)$")) {
            return new NormalizationResult(UnitGroup.MASS, "KG", BigDecimal.ONE);
        }
        if (unit.matches("^(т|тонна|тонны)$")) {
            return new NormalizationResult(UnitGroup.MASS, "KG", new BigDecimal("1000"));
        }
        if (unit.matches("^(г|грамм|граммы)$")) {
            return new NormalizationResult(UnitGroup.MASS, "KG", new BigDecimal("0.001"));
        }

        // LIQUID
        if (unit.matches("^(л|литр|литры)$")) {
            return new NormalizationResult(UnitGroup.LIQUID, "L", BigDecimal.ONE);
        }
        if (unit.matches("^(мл|миллилитр|миллилитры)$")) {
            return new NormalizationResult(UnitGroup.LIQUID, "L", new BigDecimal("0.001"));
        }

        // PIECE
        if (unit.matches("^(шт\\.?|штука|штуки)$")) {
            return new NormalizationResult(UnitGroup.PIECE, "PCS", BigDecimal.ONE);
        }
        if (unit.matches("^(1000\\s*шт\\.?|тыс\\.?\\s*шт\\.?)$")) {
            return new NormalizationResult(UnitGroup.PIECE, "PCS", new BigDecimal("1000"));
        }
        if (unit.matches("^(100\\s*шт\\.?)$")) {
            return new NormalizationResult(UnitGroup.PIECE, "PCS", new BigDecimal("100"));
        }

        // PACKAGE
        if (unit.matches("^(упак\\.?|упаковка|упаковки|рул\\.?|рулон|меш\\.?|мешок|компл\\.?|комплект|набор|пара|пар)$")) {
            return new NormalizationResult(UnitGroup.PACKAGE, "PKG", BigDecimal.ONE);
        }

        return new NormalizationResult(UnitGroup.UNKNOWN, "UNKNOWN", BigDecimal.ONE);
    }
}
