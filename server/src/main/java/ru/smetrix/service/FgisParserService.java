package ru.smetrix.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import ru.smetrix.config.FgisProperties;
import ru.smetrix.repository.ProjectRepository;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class FgisParserService {

    private static final Logger log = LoggerFactory.getLogger(FgisParserService.class);
    private final FgisProperties properties;
    private final FgisRegionRefreshService regionRefreshService;
    private final ProjectRepository projectRepository;
    private final AtomicBoolean running = new AtomicBoolean();

    public FgisParserService(
            FgisProperties properties,
            FgisRegionRefreshService regionRefreshService,
            ProjectRepository projectRepository
    ) {
        this.properties = properties;
        this.regionRefreshService = regionRefreshService;
        this.projectRepository = projectRepository;
    }

    public void fetchAndSaveMaterials() {
        if (!properties.isEnabled()) {
            log.debug("FGIS import is disabled");
            return;
        }
        if (!running.compareAndSet(false, true)) {
            log.info("FGIS import is already running; this execution is skipped");
            return;
        }

        try {
            Set<String> regionCodes = new LinkedHashSet<>(properties.getApi().getRegionCodes());
            regionCodes.addAll(projectRepository.findActiveRegionCodes());
            for (String regionCode : regionCodes) {
                if (regionCode == null || regionCode.isBlank()) {
                    continue;
                }
                regionRefreshService.refreshIfStale(regionCode);
            }
        } finally {
            running.set(false);
        }
    }
}
