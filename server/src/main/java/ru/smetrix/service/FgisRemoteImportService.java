package ru.smetrix.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import ru.smetrix.config.FgisProperties;
import ru.smetrix.fgis.FgisFileImportResult;
import ru.smetrix.fgis.FgisPeriod;
import ru.smetrix.fgis.ResourceDataProvider;
import ru.smetrix.fgis.ResourcePayload;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

@Service
public class FgisRemoteImportService {
    private static final Logger log = LoggerFactory.getLogger(FgisRemoteImportService.class);

    private final FgisProperties properties;
    private final FgisFileImportService fileImportService;
    private final FgisImportStateService stateService;
    private final List<ResourceDataProvider> providers;

    public FgisRemoteImportService(FgisProperties properties,
                                   FgisFileImportService fileImportService,
                                   FgisImportStateService stateService,
                                   List<ResourceDataProvider> providers) {
        this.properties = properties;
        this.fileImportService = fileImportService;
        this.stateService = stateService;
        this.providers = providers;
    }

    public FgisFileImportResult importRegion(String regionCode) throws IOException, InterruptedException {
        if (regionCode == null || !regionCode.matches("[A-Za-zА-Яа-я0-9_-]{1,20}")) {
            throw new IllegalArgumentException("Invalid FGIS region code");
        }
        FgisPeriod period = FgisPeriod.resolve(properties.getApi().getPeriod());
        stateService.markRunning(regionCode);
        IOException lastFailure = null;
        try {
            for (ResourceDataProvider provider : providers) {
                try {
                    Optional<ResourcePayload> fetched = provider.fetch(regionCode, period);
                    if (fetched.isEmpty()) continue;
                    try (ResourcePayload payload = fetched.get()) {
                        FgisFileImportResult result = fileImportService.importXlsx(
                                payload.stream(), regionCode, period.value());
                        stateService.markSuccess(regionCode, result);
                        log.info("FGIS {} imported through {}", regionCode, payload.provider());
                        return result;
                    }
                } catch (IOException e) {
                    lastFailure = e;
                    log.warn("FGIS provider {} failed for {}: {}", provider.name(), regionCode, e.getMessage());
                }
            }
            throw lastFailure != null ? lastFailure
                    : new IOException("No automatic FGIS provider is configured; use manual XLSX upload");
        } catch (IOException | InterruptedException | RuntimeException e) {
            stateService.markFailed(regionCode, e);
            throw e;
        }
    }
}
