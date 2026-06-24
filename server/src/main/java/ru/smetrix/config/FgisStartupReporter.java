package ru.smetrix.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class FgisStartupReporter {

    private static final Logger log = LoggerFactory.getLogger(FgisStartupReporter.class);

    private final FgisProperties properties;

    public FgisStartupReporter(FgisProperties properties) {
        this.properties = properties;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void reportConfiguration() {
        boolean manualImportReady = properties.getImportKey() != null
                && !properties.getImportKey().isBlank();
        boolean remoteImportReady = properties.isEnabled()
                && properties.getApi().getDownloadUrlTemplate() != null
                && properties.getApi().getDownloadUrlTemplate().contains("{region}");

        if (manualImportReady) {
            log.info("FGIS XLSX import is ready: POST /api/v1/admin/fgis/import");
        } else {
            log.warn("FGIS XLSX import is not configured: set FGIS_IMPORT_KEY");
        }

        if (remoteImportReady) {
            log.info("FGIS scheduled remote import is enabled for regions {}",
                    properties.getApi().getRegionCodes());
        } else if (properties.isEnabled()) {
            log.warn("FGIS scheduled import is enabled but FGIS_DOWNLOAD_URL_TEMPLATE is not configured");
        } else {
            log.info("FGIS scheduled remote import is disabled; official XLSX upload remains available");
        }
    }
}
