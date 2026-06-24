package ru.smetrix.fgis;

import java.time.LocalDate;
import java.util.Locale;

public record FgisPeriod(int year, int quarter) {

    public FgisPeriod {
        if (year < 2000 || year > 2100 || quarter < 1 || quarter > 4) {
            throw new IllegalArgumentException("FGIS period must use YYYY-QN format");
        }
    }

    public static FgisPeriod resolve(String configuredPeriod) {
        if (configuredPeriod == null || configuredPeriod.isBlank()) {
            LocalDate lastCompletedQuarter = LocalDate.now().minusMonths(3);
            return new FgisPeriod(
                    lastCompletedQuarter.getYear(),
                    (lastCompletedQuarter.getMonthValue() - 1) / 3 + 1
            );
        }
        String normalized = configuredPeriod.trim().toUpperCase(Locale.ROOT);
        if (!normalized.matches("20[0-9]{2}-Q[1-4]")) {
            throw new IllegalArgumentException("FGIS period must use YYYY-QN format");
        }
        return new FgisPeriod(
                Integer.parseInt(normalized.substring(0, 4)),
                Integer.parseInt(normalized.substring(6))
        );
    }

    public String value() {
        return year + "-Q" + quarter;
    }
}
