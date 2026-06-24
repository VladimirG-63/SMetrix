package ru.smetrix.fgis;

public record FgisImportResult(int created, int updated, int unchanged, int rejected) {

    public int total() {
        return created + updated + unchanged + rejected;
    }

    public FgisImportResult plus(FgisImportResult other) {
        return new FgisImportResult(
                created + other.created,
                updated + other.updated,
                unchanged + other.unchanged,
                rejected + other.rejected
        );
    }
}
