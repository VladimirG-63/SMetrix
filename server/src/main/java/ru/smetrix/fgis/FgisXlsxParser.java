package ru.smetrix.fgis;

import org.apache.poi.openxml4j.exceptions.OpenXML4JException;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.openxml4j.opc.PackageAccess;
import org.apache.poi.xssf.eventusermodel.ReadOnlySharedStringsTable;
import org.apache.poi.xssf.eventusermodel.XSSFReader;
import org.apache.poi.xssf.eventusermodel.XSSFSheetXMLHandler;
import org.apache.poi.xssf.model.StylesTable;
import org.apache.poi.xssf.usermodel.XSSFComment;
import org.springframework.stereotype.Component;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class FgisXlsxParser {

    private static final Set<String> CODE_HEADERS = Set.of("код", "шифр", "код ресурса", "шифр ресурса");
    private static final Set<String> NAME_HEADERS = Set.of("наименование", "наименование ресурса");
    private static final Set<String> UNIT_HEADERS = Set.of("единица измерения", "ед изм", "ед измерения");
    private static final Set<String> PRICE_HEADERS = Set.of(
            "цена", "сметная цена", "отпускная цена", "текущая цена", "цена без ндс"
    );
    private static final Set<String> CONSUMPTION_HEADERS = Set.of(
            "расход", "норма", "норма расхода", "норматив", "средний расход", "среднее", "норматив расхода"
    );
    private static final Pattern PERIOD_PATTERN = Pattern.compile(
            "(?iu)(IV|III|II|I|[1-4])\\s*квартал\\s*(20\\d{2})"
    );

    public FgisXlsxParseResult parse(
            InputStream inputStream,
            String regionCode,
            String quarter,
            Consumer<FgisMaterialRecord> recordConsumer
    ) throws IOException {
        if (inputStream == null) {
            throw new IllegalArgumentException("XLSX input stream is required");
        }
        if (regionCode == null || regionCode.isBlank()) {
            throw new IllegalArgumentException("Region code is required");
        }

        int records = 0;
        int rejectedRows = 0;
        int sheets = 0;

        Path temporaryWorkbook = Files.createTempFile("smetrix-fgis-", ".xlsx");
        try {
            Files.copy(inputStream, temporaryWorkbook, StandardCopyOption.REPLACE_EXISTING);
            try (OPCPackage workbook = OPCPackage.open(temporaryWorkbook.toFile(), PackageAccess.READ)) {
            XSSFReader reader = new XSSFReader(workbook);
            StylesTable styles = reader.getStylesTable();
            ReadOnlySharedStringsTable strings = new ReadOnlySharedStringsTable(workbook);
            XSSFReader.SheetIterator sheetIterator = (XSSFReader.SheetIterator) reader.getSheetsData();

            while (sheetIterator.hasNext()) {
                try (InputStream sheet = sheetIterator.next()) {
                    SheetHandler handler = new SheetHandler(regionCode, quarter, recordConsumer);
                    SAXParserFactory factory = SAXParserFactory.newInstance();
                    factory.setNamespaceAware(true);
                    XMLReader xmlReader = factory.newSAXParser().getXMLReader();
                    xmlReader.setContentHandler(new XSSFSheetXMLHandler(
                            styles,
                            null,
                            strings,
                            handler,
                            new org.apache.poi.ss.usermodel.DataFormatter(Locale.forLanguageTag("ru-RU")),
                            false
                    ));
                    xmlReader.parse(new InputSource(sheet));
                    records += handler.records;
                    rejectedRows += handler.rejectedRows;
                    sheets++;
                }
            }
            }
        } catch (OpenXML4JException | SAXException | ParserConfigurationException e) {
            throw new IOException("Cannot parse FGIS XLSX file", e);
        } finally {
            Files.deleteIfExists(temporaryWorkbook);
        }

        if (records == 0) {
            throw new IOException("FGIS XLSX contains no recognizable material rows");
        }
        return new FgisXlsxParseResult(records, rejectedRows, sheets);
    }

    private static final class SheetHandler implements XSSFSheetXMLHandler.SheetContentsHandler {

        private final String regionCode;
        private final String quarter;
        private final Consumer<FgisMaterialRecord> recordConsumer;
        private final Map<Integer, String> cells = new HashMap<>();
        private Map<String, Integer> columns;
        private String detectedQuarter;
        private int records;
        private int rejectedRows;

        private SheetHandler(String regionCode, String quarter, Consumer<FgisMaterialRecord> recordConsumer) {
            this.regionCode = regionCode.trim().toUpperCase(Locale.ROOT);
            this.quarter = quarter;
            this.recordConsumer = recordConsumer;
        }

        @Override
        public void startRow(int rowNum) {
            cells.clear();
        }

        @Override
        public void endRow(int rowNum) {
            if (cells.isEmpty()) {
                return;
            }
            detectQuarter(cells);
            if (columns == null) {
                Map<String, Integer> discovered = discoverColumns(cells);
                if (discovered.containsKey("code")
                        && discovered.containsKey("name")
                        && discovered.containsKey("price")) {
                    columns = discovered;
                }
                return;
            }

            String code = value("code");
            String name = value("name");
            String unit = value("unit");
            String priceText = value("price");
            String consumptionText = value("consumption");
            
            if (isColumnNumberRow(code, name, unit, priceText)) {
                return;
            }
            BigDecimal price = parsePrice(priceText);
            if (isBlank(code) || isBlank(name) || price == null || price.signum() < 0) {
                rejectedRows++;
                return;
            }

            BigDecimal consumptionRate = parseNumber(consumptionText);

            String recordQuarter = detectedQuarter != null ? detectedQuarter : quarter;
            recordConsumer.accept(new FgisMaterialRecord(
                    code, name, unit, price, regionCode, recordQuarter, consumptionRate
            ));
            records++;
        }

        @Override
        public void cell(String cellReference, String formattedValue, XSSFComment comment) {
            cells.put(columnIndex(cellReference), formattedValue == null ? null : formattedValue.trim());
        }

        private String value(String field) {
            Integer column = columns.get(field);
            return column == null ? null : cells.get(column);
        }

        private static Map<String, Integer> discoverColumns(Map<Integer, String> cells) {
            Map<String, Integer> result = new HashMap<>();
            Integer fallbackPriceColumn = null;
            cells.forEach((column, value) -> {
                String header = normalizeHeader(value);
                if (isCodeHeader(header)) {
                    result.putIfAbsent("code", column);
                } else if (isNameHeader(header)) {
                    result.putIfAbsent("name", column);
                } else if (isUnitHeader(header)) {
                    result.putIfAbsent("unit", column);
                } else if (isCurrentPriceHeader(header)) {
                    result.put("price", column);
                } else if (isConsumptionHeader(header)) {
                    result.putIfAbsent("consumption", column);
                }
            });
            if (!result.containsKey("price")) {
                for (Map.Entry<Integer, String> cell : cells.entrySet()) {
                    if (isPriceHeader(normalizeHeader(cell.getValue()))) {
                        fallbackPriceColumn = cell.getKey();
                        break;
                    }
                }
                if (fallbackPriceColumn != null) {
                    result.put("price", fallbackPriceColumn);
                }
            }
            return result;
        }

        private void detectQuarter(Map<Integer, String> rowCells) {
            if (detectedQuarter != null) {
                return;
            }
            String rowText = rowCells.values().stream()
                    .filter(value -> value != null && !value.isBlank())
                    .collect(java.util.stream.Collectors.joining(" "));
            Matcher matcher = PERIOD_PATTERN.matcher(rowText);
            if (matcher.find()) {
                detectedQuarter = matcher.group(2) + "-Q" + quarterNumber(matcher.group(1));
            }
        }

        private static int quarterNumber(String value) {
            return switch (value.toUpperCase(Locale.ROOT)) {
                case "I" -> 1;
                case "II" -> 2;
                case "III" -> 3;
                case "IV" -> 4;
                default -> Integer.parseInt(value);
            };
        }

        private static boolean isCodeHeader(String header) {
            return CODE_HEADERS.contains(header)
                    || header.startsWith("код ресурса")
                    || header.equals("код ресурса")
                    || header.startsWith("код строительного ресурса");
        }

        private static boolean isNameHeader(String header) {
            return NAME_HEADERS.contains(header)
                    || header.startsWith("наименование ресурса")
                    || header.startsWith("наименование строительного ресурса");
        }

        private static boolean isUnitHeader(String header) {
            return UNIT_HEADERS.contains(header)
                    || header.startsWith("единица измерения")
                    || header.startsWith("ед изм");
        }

        private static boolean isPriceHeader(String header) {
            return PRICE_HEADERS.contains(header)
                    || header.startsWith("сметная цена");
        }

        private static boolean isCurrentPriceHeader(String header) {
            return header.startsWith("сметная цена") && header.contains("текущ");
        }

        private static boolean isConsumptionHeader(String header) {
            return CONSUMPTION_HEADERS.contains(header)
                    || header.contains("расход")
                    || header.contains("норматив");
        }

        private static int columnIndex(String cellReference) {
            int column = 0;
            for (int i = 0; i < cellReference.length(); i++) {
                char character = cellReference.charAt(i);
                if (!Character.isLetter(character)) {
                    break;
                }
                column = column * 26 + Character.toUpperCase(character) - 'A' + 1;
            }
            return column - 1;
        }

        private static String normalizeHeader(String value) {
            if (value == null) {
                return "";
            }
            return value.toLowerCase(Locale.ROOT)
                    .replace('ё', 'е')
                    .replaceAll("[^а-яa-z0-9]+", " ")
                    .trim()
                    .replaceAll("\\s+", " ");
        }

        private static BigDecimal parsePrice(String value) {
            return parseNumber(value);
        }

        private static BigDecimal parseNumber(String value) {
            if (isBlank(value)) {
                return null;
            }
            String normalized = value
                    .replace("\u00A0", "")
                    .replace(" ", "")
                    .replace(',', '.')
                    .replaceAll("[^0-9.\\-]", "");
            if (normalized.isEmpty() || normalized.equals("-") || normalized.indexOf('.') != normalized.lastIndexOf('.')) {
                return null;
            }
            try {
                return new BigDecimal(normalized);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }

        private static boolean isColumnNumberRow(String code, String name, String unit, String price) {
            return isInteger(code) && isInteger(name) && isInteger(unit) && isInteger(price);
        }

        private static boolean isInteger(String value) {
            return value != null && value.trim().matches("\\d+");
        }

        private static boolean isBlank(String value) {
            return value == null || value.isBlank();
        }
    }
}
