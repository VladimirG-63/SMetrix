package ru.smetrix.scheduler;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ru.smetrix.service.FgisParserService;

@Component
public class FgisScheduler {

    private final FgisParserService fgisParserService;

    public FgisScheduler(FgisParserService fgisParserService) {
        this.fgisParserService = fgisParserService;
    }

    @Scheduled(cron = "${fgis.scheduler.cron:0 0 3 1 1,4,7,10 *}")
    public void runQuarterlyParser() {
        fgisParserService.fetchAndSaveMaterials();
    }

    @Scheduled(
            fixedDelayString = "${fgis.scheduler.region-refresh-delay-ms:300000}",
            initialDelayString = "${fgis.scheduler.region-refresh-delay-ms:300000}"
    )
    public void refreshProjectRegions() {
        fgisParserService.fetchAndSaveMaterials();
    }
}
