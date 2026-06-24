package com.smetrix.app.utils.quantity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.smetrix.app.db.entity.OpeningEntity;

import java.math.BigDecimal;
import java.util.List;

public class RoomGeometryCalculator {

    public static class GeometryResult {
        public final BigDecimal floorArea;
        public final BigDecimal ceilingArea;
        public final BigDecimal wallsArea;
        public final BigDecimal allSurfacesArea;
        public final BigDecimal roomVolume;
        public final BigDecimal perimeter;
        public final BigDecimal perimeterMinusOpenings;
        public final int doorsCount;
        public final int windowsCount;

        public GeometryResult(BigDecimal floorArea, BigDecimal ceilingArea, BigDecimal wallsArea,
                              BigDecimal allSurfacesArea, BigDecimal roomVolume, BigDecimal perimeter,
                              BigDecimal perimeterMinusOpenings, int doorsCount, int windowsCount) {
            this.floorArea = floorArea;
            this.ceilingArea = ceilingArea;
            this.wallsArea = wallsArea;
            this.allSurfacesArea = allSurfacesArea;
            this.roomVolume = roomVolume;
            this.perimeter = perimeter;
            this.perimeterMinusOpenings = perimeterMinusOpenings;
            this.doorsCount = doorsCount;
            this.windowsCount = windowsCount;
        }
    }

    @NonNull
    public static GeometryResult calculate(
            @Nullable BigDecimal length,
            @Nullable BigDecimal width,
            @Nullable BigDecimal height,
            @Nullable BigDecimal manualAreaOverride,
            @Nullable List<OpeningEntity> openings) {

        if (length == null || width == null || height == null ||
                length.signum() <= 0 || width.signum() <= 0 || height.signum() <= 0) {
            return new GeometryResult(
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                    BigDecimal.ZERO, 0, 0
            );
        }

        BigDecimal floorArea = manualAreaOverride != null && manualAreaOverride.signum() > 0 ?
                manualAreaOverride : length.multiply(width);
        BigDecimal ceilingArea = length.multiply(width);

        BigDecimal perimeter = length.add(width).multiply(new BigDecimal("2"));
        BigDecimal roomVolume = length.multiply(width).multiply(height);

        BigDecimal roughWallsArea = perimeter.multiply(height);
        BigDecimal doorsWidthSum = BigDecimal.ZERO;

        int doors = 0;
        int windows = 0;

        if (openings != null) {
            for (OpeningEntity opening : openings) {
                if (opening.width == null || opening.height == null) continue;

                if ("DOOR".equals(opening.type)) {
                    doors++;
                    roughWallsArea = roughWallsArea.subtract(opening.width.multiply(opening.height));
                    doorsWidthSum = doorsWidthSum.add(opening.width);
                } else if ("WINDOW".equals(opening.type)) {
                    windows++;
                    roughWallsArea = roughWallsArea.subtract(opening.width.multiply(opening.height));
                } else if ("VENT".equals(opening.type)) {
                    roughWallsArea = roughWallsArea.subtract(opening.width.multiply(opening.height));
                } else if ("COLUMN".equals(opening.type)) {
                    if (opening.depth == null) continue;
                    if ("WALL_ADJACENT".equals(opening.placementType)) {
                        roughWallsArea = roughWallsArea.add(
                                opening.width.add(opening.depth.multiply(new BigDecimal("2")))
                                        .multiply(opening.height)
                        );
                    } else {
                        roughWallsArea = roughWallsArea.add(
                                new BigDecimal("2").multiply(opening.width.add(opening.depth))
                                        .multiply(opening.height)
                        );
                    }
                }
            }
        }

        BigDecimal wallsArea = roughWallsArea.max(BigDecimal.ZERO);
        BigDecimal perimeterMinusOpenings = perimeter.subtract(doorsWidthSum).max(BigDecimal.ZERO);
        BigDecimal allSurfacesArea = floorArea.add(ceilingArea).add(wallsArea);

        return new GeometryResult(
                floorArea, ceilingArea, wallsArea, allSurfacesArea,
                roomVolume, perimeter, perimeterMinusOpenings,
                doors, windows
        );
    }
}
