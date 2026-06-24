package ru.smetrix.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import ru.smetrix.config.FgisProperties;
import ru.smetrix.fgis.FgisFileImportResult;

import java.time.Duration;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class FgisRegionRefreshService {

    private static final Logger log = LoggerFactory.getLogger(FgisRegionRefreshService.class);

    private final FgisProperties properties;
    private final FgisRemoteImportService remoteImportService;
    private final FgisImportStateService stateService;
    private final AdminAlertService alertService;
    private final Set<String> regionsInProgress = ConcurrentHashMap.newKeySet();

    public FgisRegionRefreshService(
            FgisProperties properties,
            FgisRemoteImportService remoteImportService,
            FgisImportStateService stateService,
            AdminAlertService alertService
    ) {
        this.properties = properties;
        this.remoteImportService = remoteImportService;
        this.stateService = stateService;
        this.alertService = alertService;
    }

    public void refreshIfStale(String rawRegionCode) {
        String regionCode = normalize(rawRegionCode);
        if (regionCode == null || !properties.isEnabled()) {
            return;
        }

        long freshnessMillis = Duration.ofHours(Math.max(1, properties.getScheduler().getFreshnessHours())).toMillis();
        long freshAfter = System.currentTimeMillis() - freshnessMillis;
        if (stateService.isFresh(regionCode, freshAfter)) {
            return;
        }
        if (!regionsInProgress.add(regionCode)) {
            return;
        }

        try {
            FgisFileImportResult result = remoteImportService.importRegion(regionCode);
            log.info("FGIS region {} imported: created={}, updated={}, unchanged={}, rejected={}",
                    regionCode,
                    result.database().created(),
                    result.database().updated(),
                    result.database().unchanged(),
                    result.database().rejected());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("FGIS import interrupted for region {}", regionCode);
            alertService.fgisFailure(regionCode, e);
        } catch (Exception e) {
            log.error("FGIS import failed for region {}: {}", regionCode, e.getMessage());
            alertService.fgisFailure(regionCode, e);
        } finally {
            regionsInProgress.remove(regionCode);
        }
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        return normalized.matches("[A-ZА-Я0-9_-]{1,20}") ? normalized : null;
    }
}
