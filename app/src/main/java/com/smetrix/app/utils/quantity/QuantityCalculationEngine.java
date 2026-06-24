package com.smetrix.app.utils.quantity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class QuantityCalculationEngine {

    public static class EngineParams {
        public String unitMeasure;
        public String normalizedUnit;
        public QuantityCalculationMethod method;
        public RoomGeometryCalculator.GeometryResult geometry;
        
        // Form inputs
        public BigDecimal manualQuantity;
        public BigDecimal manualArea;
        public BigDecimal manualVolume;
        public BigDecimal manualLength;
        public BigDecimal thicknessMeters;
        public BigDecimal consumptionRate;
        public int layers = 1;
        public BigDecimal wastePercent = BigDecimal.ZERO;
        public BigDecimal coveragePerPiece;
        public BigDecimal coveragePerPackage;
        public BigDecimal kgPerPackage;
        public BigDecimal volumePerPackage;
        
        // Selections
        public String selectedAreaType; // "FLOOR", "WALLS", "CEILING", "ALL", "MANUAL"
    }

    public static class QuantityCalculationResult {
        public final BigDecimal quantity;
        public final String formulaDescription;
        public final String warningMessage;
        public final QuantityCalculationMethod usedMethod;
        public final String unitMeasure;
        public final boolean isRounded;
        public final String validationError;

        public QuantityCalculationResult(BigDecimal quantity, String formulaDescription,
                                         String warningMessage, QuantityCalculationMethod usedMethod,
                                         String unitMeasure, boolean isRounded, String validationError) {
            this.quantity = quantity;
            this.formulaDescription = formulaDescription;
            this.warningMessage = warningMessage;
            this.usedMethod = usedMethod;
            this.unitMeasure = unitMeasure;
            this.isRounded = isRounded;
            this.validationError = validationError;
        }

        public static QuantityCalculationResult error(String error) {
            return new QuantityCalculationResult(BigDecimal.ZERO, null, null, null, null, false, error);
        }
    }

    @NonNull
    public static QuantityCalculationResult calculate(@NonNull EngineParams p) {
        if (p.method == null) {
            return QuantityCalculationResult.error("Метод расчёта не выбран");
        }

        BigDecimal wasteFactor = BigDecimal.ONE.add(
                (p.wastePercent != null ? p.wastePercent : BigDecimal.ZERO)
                        .divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP)
        );

        BigDecimal quantity = BigDecimal.ZERO;
        String formula = "";
        boolean isRounded = false;
        String warning = null;
        BigDecimal layersFactor = new BigDecimal(Math.max(1, p.layers));

        try {
            switch (p.method) {
                case MANUAL_QUANTITY:
                case PIECES_MANUAL:
                case PACKAGES_MANUAL:
                case LENGTH_MANUAL:
                case UNKNOWN_MANUAL:
                    if (p.manualQuantity == null || p.manualQuantity.signum() < 0) return QuantityCalculationResult.error("Укажите корректное количество");
                    quantity = p.manualQuantity;
                    formula = "Введено вручную";
                    break;

                case AREA_FLOOR:
                    quantity = p.geometry.floorArea.multiply(wasteFactor);
                    formula = "Площадь пола × (1 + запас)";
                    break;

                case AREA_WALLS:
                    quantity = p.geometry.wallsArea.multiply(wasteFactor);
                    formula = "Площадь стен × (1 + запас)";
                    break;

                case AREA_CEILING:
                    quantity = p.geometry.ceilingArea.multiply(wasteFactor);
                    formula = "Площадь потолка × (1 + запас)";
                    break;

                case AREA_ALL_SURFACES:
                    quantity = p.geometry.allSurfacesArea.multiply(wasteFactor);
                    formula = "Площадь всех поверхностей × (1 + запас)";
                    break;

                case AREA_MANUAL:
                    if (p.manualArea == null || p.manualArea.signum() <= 0) return QuantityCalculationResult.error("Укажите площадь");
                    quantity = p.manualArea.multiply(wasteFactor);
                    formula = "Указанная площадь × (1 + запас)";
                    break;

                case VOLUME_ROOM:
                    quantity = p.geometry.roomVolume.multiply(wasteFactor);
                    formula = "Объём комнаты × (1 + запас)";
                    break;

                case VOLUME_AREA_THICKNESS:
                    if (p.thicknessMeters == null || p.thicknessMeters.signum() <= 0) return QuantityCalculationResult.error("Укажите толщину слоя");
                    BigDecimal areaForVol = getAreaByType(p.selectedAreaType, p.geometry, p.manualArea);
                    if (areaForVol == null) return QuantityCalculationResult.error("Не указана базовая площадь");
                    quantity = areaForVol.multiply(p.thicknessMeters).multiply(wasteFactor);
                    formula = "Площадь × толщина × (1 + запас)";
                    break;

                case VOLUME_MANUAL:
                    if (p.manualVolume == null || p.manualVolume.signum() <= 0) return QuantityCalculationResult.error("Укажите объём");
                    quantity = p.manualVolume.multiply(wasteFactor);
                    formula = "Указанный объём × (1 + запас)";
                    break;

                case LENGTH_PERIMETER:
                    quantity = p.geometry.perimeter.multiply(wasteFactor);
                    formula = "Периметр × (1 + запас)";
                    break;

                case LENGTH_PERIMETER_MINUS_OPENINGS:
                    quantity = p.geometry.perimeterMinusOpenings.multiply(wasteFactor);
                    formula = "(Периметр - проёмы) × (1 + запас)";
                    break;

                case CONSUMPTION_PER_M2:
                    if (p.consumptionRate == null || p.consumptionRate.signum() <= 0) return QuantityCalculationResult.error("Укажите норму расхода");
                    BigDecimal areaForCons = getAreaByType(p.selectedAreaType, p.geometry, p.manualArea);
                    if (areaForCons == null) return QuantityCalculationResult.error("Не указана площадь");
                    quantity = areaForCons.multiply(p.consumptionRate).multiply(layersFactor).multiply(wasteFactor);
                    formula = "Площадь × норма расхода × слои × (1 + запас)";
                    break;

                case CONSUMPTION_PER_M3:
                    if (p.consumptionRate == null || p.consumptionRate.signum() <= 0) return QuantityCalculationResult.error("Укажите норму расхода");
                    BigDecimal volForCons = p.geometry.roomVolume;
                    if ("MANUAL".equals(p.selectedAreaType)) {
                        if (p.manualVolume == null) return QuantityCalculationResult.error("Укажите объём");
                        volForCons = p.manualVolume;
                    }
                    quantity = volForCons.multiply(p.consumptionRate).multiply(layersFactor).multiply(wasteFactor);
                    formula = "Объём × норма расхода × слои × (1 + запас)";
                    break;

                case CONSUMPTION_PER_METER:
                    if (p.consumptionRate == null || p.consumptionRate.signum() <= 0) return QuantityCalculationResult.error("Укажите норму расхода");
                    BigDecimal lenForCons = p.geometry.perimeter;
                    if ("MANUAL".equals(p.selectedAreaType)) {
                        if (p.manualLength == null) return QuantityCalculationResult.error("Укажите длину");
                        lenForCons = p.manualLength;
                    }
                    quantity = lenForCons.multiply(p.consumptionRate).multiply(layersFactor).multiply(wasteFactor);
                    formula = "Длина × норма расхода × слои × (1 + запас)";
                    break;

                case CONSUMPTION_PER_PIECE:
                    if (p.consumptionRate == null || p.consumptionRate.signum() <= 0) return QuantityCalculationResult.error("Укажите норму расхода");
                    if (p.manualQuantity == null || p.manualQuantity.signum() <= 0) return QuantityCalculationResult.error("Укажите количество штук");
                    quantity = p.manualQuantity.multiply(p.consumptionRate).multiply(wasteFactor);
                    formula = "Кол-во штук × норма расхода × (1 + запас)";
                    break;

                case PIECES_BY_DOORS:
                    quantity = new BigDecimal(p.geometry.doorsCount);
                    formula = "Количество дверей";
                    break;

                case PIECES_BY_WINDOWS:
                    quantity = new BigDecimal(p.geometry.windowsCount);
                    formula = "Количество окон";
                    break;

                case PIECES_BY_AREA_COVERAGE:
                    if (p.coveragePerPiece == null || p.coveragePerPiece.signum() <= 0) return QuantityCalculationResult.error("Укажите площадь одной штуки");
                    BigDecimal areaForPieces = getAreaByType(p.selectedAreaType, p.geometry, p.manualArea);
                    if (areaForPieces == null) return QuantityCalculationResult.error("Не указана площадь");
                    quantity = areaForPieces.divide(p.coveragePerPiece, 4, RoundingMode.HALF_UP).multiply(wasteFactor).setScale(0, RoundingMode.CEILING);
                    isRounded = true;
                    formula = "Округление вверх(Площадь / площадь 1 шт × (1 + запас))";
                    break;

                case PACKAGES_BY_COVERAGE:
                    if (p.coveragePerPackage == null || p.coveragePerPackage.signum() <= 0) return QuantityCalculationResult.error("Укажите площадь упаковки");
                    BigDecimal areaForPkg = getAreaByType(p.selectedAreaType, p.geometry, p.manualArea);
                    if (areaForPkg == null) return QuantityCalculationResult.error("Не указана площадь");
                    quantity = areaForPkg.divide(p.coveragePerPackage, 4, RoundingMode.HALF_UP).multiply(wasteFactor).setScale(0, RoundingMode.CEILING);
                    isRounded = true;
                    formula = "Округление вверх(Площадь / площадь упаковки × (1 + запас))";
                    break;

                case PACKAGES_BY_WEIGHT:
                    if (p.kgPerPackage == null || p.kgPerPackage.signum() <= 0) return QuantityCalculationResult.error("Укажите массу упаковки");
                    if (p.manualQuantity == null || p.manualQuantity.signum() <= 0) return QuantityCalculationResult.error("Укажите требуемую массу");
                    quantity = p.manualQuantity.divide(p.kgPerPackage, 4, RoundingMode.HALF_UP).setScale(0, RoundingMode.CEILING);
                    isRounded = true;
                    formula = "Округление вверх(Требуемая масса / масса упаковки)";
                    break;

                case PACKAGES_BY_VOLUME:
                    if (p.volumePerPackage == null || p.volumePerPackage.signum() <= 0) return QuantityCalculationResult.error("Укажите объём упаковки");
                    if (p.manualVolume == null || p.manualVolume.signum() <= 0) return QuantityCalculationResult.error("Укажите требуемый объём");
                    quantity = p.manualVolume.divide(p.volumePerPackage, 4, RoundingMode.HALF_UP).setScale(0, RoundingMode.CEILING);
                    isRounded = true;
                    formula = "Округление вверх(Требуемый объём / объём упаковки)";
                    break;

                default:
                    return QuantityCalculationResult.error("Неизвестный метод расчёта");
            }

            if (!isRounded) {
                quantity = quantity.setScale(4, RoundingMode.HALF_UP);
            }

            if (quantity.signum() < 0) {
                return QuantityCalculationResult.error("Расчётное количество меньше нуля");
            }

            return new QuantityCalculationResult(quantity, formula, warning, p.method, p.unitMeasure, isRounded, null);

        } catch (Exception e) {
            return QuantityCalculationResult.error("Ошибка расчёта: " + e.getMessage());
        }
    }

    @Nullable
    private static BigDecimal getAreaByType(String type, RoomGeometryCalculator.GeometryResult geo, BigDecimal manualArea) {
        if (type == null) return null;
        switch (type) {
            case "FLOOR": return geo.floorArea;
            case "WALLS": return geo.wallsArea;
            case "CEILING": return geo.ceilingArea;
            case "ALL": return geo.allSurfacesArea;
            case "MANUAL": return manualArea;
            default: return null;
        }
    }
}
