package ru.smetrix.fgis;

public record FgisFileImportResult(FgisXlsxParseResult parsing, FgisImportResult database) {
}
