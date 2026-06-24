package ru.smetrix;

import org.junit.jupiter.api.Test;
import ru.smetrix.config.FgisProperties;
import ru.smetrix.fgis.*;
import ru.smetrix.service.FgisFileImportService;
import ru.smetrix.service.FgisImportStateService;
import ru.smetrix.service.FgisRemoteImportService;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class FgisProviderFallbackTest {
    @Test
    void fallsBackToNextProvider() throws Exception {
        FgisProperties properties = new FgisProperties();
        properties.getApi().setPeriod("2026-Q1");
        FgisFileImportResult expected = new FgisFileImportResult(
                new FgisXlsxParseResult(1, 0, 1), new FgisImportResult(1, 0, 0, 0));
        FgisFileImportService importer = new FgisFileImportService(null, null) {
            @Override public FgisFileImportResult importXlsx(java.io.InputStream stream,
                                                              String region, String period) {
                return expected;
            }
        };
        final boolean[] success = {false};
        FgisImportStateService state = new FgisImportStateService(null) {
            @Override public void markRunning(String region) { }
            @Override public void markFailed(String region, Exception error) { }
            @Override public void markSuccess(String region, FgisFileImportResult result) { success[0] = true; }
        };
        ResourceDataProvider failed = new ResourceDataProvider() {
            public String name() { return "failed"; }
            public Optional<ResourcePayload> fetch(String region, FgisPeriod period) throws IOException {
                throw new IOException("blocked");
            }
        };
        ResourceDataProvider fallback = new ResourceDataProvider() {
            public String name() { return "fallback"; }
            public Optional<ResourcePayload> fetch(String region, FgisPeriod period) {
                return Optional.of(new ResourcePayload(name(), new ByteArrayInputStream(new byte[]{1})));
            }
        };
        FgisRemoteImportService service = new FgisRemoteImportService(
                properties, importer, state, List.of(failed, fallback));

        assertThat(service.importRegion("77")).isEqualTo(expected);
        assertThat(success[0]).isTrue();
    }
}
