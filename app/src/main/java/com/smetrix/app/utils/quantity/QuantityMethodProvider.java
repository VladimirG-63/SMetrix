package com.smetrix.app.utils.quantity;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class QuantityMethodProvider {

    public static List<QuantityCalculationMethod> getAvailableMethods(UnitGroup group) {
        if (group == null) return Collections.singletonList(QuantityCalculationMethod.MANUAL_QUANTITY);

        switch (group) {
            case AREA:
                return Arrays.asList(
                        QuantityCalculationMethod.AREA_FLOOR,
                        QuantityCalculationMethod.AREA_WALLS,
                        QuantityCalculationMethod.AREA_CEILING,
                        QuantityCalculationMethod.AREA_ALL_SURFACES,
                        QuantityCalculationMethod.MANUAL_QUANTITY
                );
            case VOLUME:
                return Arrays.asList(
                        QuantityCalculationMethod.VOLUME_AREA_THICKNESS,
                        QuantityCalculationMethod.VOLUME_ROOM,
                        QuantityCalculationMethod.MANUAL_QUANTITY
                );
            case LENGTH:
                return Arrays.asList(
                        QuantityCalculationMethod.LENGTH_PERIMETER,
                        QuantityCalculationMethod.LENGTH_PERIMETER_MINUS_OPENINGS,
                        QuantityCalculationMethod.MANUAL_QUANTITY
                );
            case MASS:
                return Arrays.asList(
                        QuantityCalculationMethod.CONSUMPTION_PER_M2,
                        QuantityCalculationMethod.MANUAL_QUANTITY
                );
            case LIQUID:
                return Arrays.asList(
                        QuantityCalculationMethod.CONSUMPTION_PER_M2,
                        QuantityCalculationMethod.MANUAL_QUANTITY
                );
            case PIECE:
                return Arrays.asList(
                        QuantityCalculationMethod.MANUAL_QUANTITY,
                        QuantityCalculationMethod.PIECES_BY_DOORS,
                        QuantityCalculationMethod.PIECES_BY_WINDOWS,
                        QuantityCalculationMethod.PIECES_BY_AREA_COVERAGE
                );
            case PACKAGE:
                return Arrays.asList(
                        QuantityCalculationMethod.MANUAL_QUANTITY,
                        QuantityCalculationMethod.PACKAGES_BY_COVERAGE
                );
            case UNKNOWN:
            default:
                return Collections.singletonList(
                        QuantityCalculationMethod.MANUAL_QUANTITY
                );
        }
    }
}
