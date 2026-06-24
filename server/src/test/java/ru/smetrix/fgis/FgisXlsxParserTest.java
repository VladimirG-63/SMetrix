package ru.smetrix.fgis;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class FgisXlsxParserTest {

    private final FgisXlsxParser parser = new FgisXlsxParser();

    @Test
    void parsesOfficialNineColumnSplitFormAndUsesCurrentPrice() throws Exception {
        List<FgisMaterialRecord> records = new ArrayList<>();

        FgisXlsxParseResult result = parser.parse(
                new ByteArrayInputStream(splitFormWorkbook()), "77", null, records::add
        );

        assertThat(result.records()).isEqualTo(2);
        assertThat(result.rejectedRows()).isEqualTo(1);
        assertThat(records).containsExactly(
                new FgisMaterialRecord(
                        "01.1.02.03-0001", "Арматура стальная А500С", "т",
                        new BigDecimal("71234.56"), "77", "2025-Q2"
                ),
                new FgisMaterialRecord(
                        "01.1.02.04-0002", "Цемент портландцемент ЦЕМ I 42,5Н", "т",
                        new BigDecimal("6890.1"), "77", "2025-Q2"
                )
        );
    }

    @Test
    void parsesRealSplitFormWhenFileIsProvided() throws Exception {
        String configuredFile = System.getProperty("fgis.test.file");
        Assumptions.assumeTrue(configuredFile != null && !configuredFile.isBlank());

        AtomicBoolean hasRebar = new AtomicBoolean();
        AtomicBoolean hasCement = new AtomicBoolean();
        AtomicBoolean hasExpectedPeriod = new AtomicBoolean();
        try (var input = Files.newInputStream(Path.of(configuredFile))) {
            FgisXlsxParseResult result = parser.parse(input, "61", null, record -> {
                String name = record.name().toLowerCase();
                hasRebar.compareAndSet(false, name.contains("арматур"));
                hasCement.compareAndSet(false, name.contains("цемент"));
                hasExpectedPeriod.compareAndSet(false, "2026-Q2".equals(record.quarter()));
            });

            assertThat(result.records()).isGreaterThan(10_000);
            assertThat(hasRebar).isTrue();
            assertThat(hasCement).isTrue();
            assertThat(hasExpectedPeriod).isTrue();
        }
    }

    private byte[] splitFormWorkbook() throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("Общая сплит-форма");
            sheet.createRow(0).createCell(0)
                    .setCellValue("Сплит-форма ФГИС ЦС, город Москва, II квартал 2025");

            var header = sheet.createRow(1);
            String[] headers = {
                    "Код ресурса, услуги",
                    "Наименование строительного ресурса, услуги",
                    "Единица измерения",
                    "Отпускная цена в уровне цен по состоянию на 01.01.2022 г., руб.",
                    "Сметная цена в уровне цен по состоянию на 01.01.2022 г., руб.",
                    "Номер группы ресурсов",
                    "Наименование группы ресурсов",
                    "Сметная цена в текущем уровне цен, руб.",
                    "Индекс изменения сметной стоимости"
            };
            for (int column = 0; column < headers.length; column++) {
                header.createCell(column).setCellValue(headers[column]);
            }

            addResource(sheet, 2, "01.1.02.03-0001", "Арматура стальная А500С", "т", 45000, 50000, 71234.56);
            addResource(sheet, 3, "01.1.02.04-0002", "Цемент портландцемент ЦЕМ I 42,5Н", "т", 4000, 4300, 6890.10);
            addResource(sheet, 4, "", "Некорректная строка", "т", 1, 1, 1);

            workbook.write(output);
            return output.toByteArray();
        }
    }

    private void addResource(
            org.apache.poi.ss.usermodel.Sheet sheet,
            int rowNumber,
            String code,
            String name,
            String unit,
            double releasePrice,
            double basePrice,
            double currentPrice
    ) {
        var row = sheet.createRow(rowNumber);
        row.createCell(0).setCellValue(code);
        row.createCell(1).setCellValue(name);
        row.createCell(2).setCellValue(unit);
        row.createCell(3).setCellValue(releasePrice);
        row.createCell(4).setCellValue(basePrice);
        row.createCell(5).setCellValue("01.1.02");
        row.createCell(6).setCellValue("Материалы");
        row.createCell(7).setCellValue(currentPrice);
        row.createCell(8).setCellValue(1.4);
    }
}
