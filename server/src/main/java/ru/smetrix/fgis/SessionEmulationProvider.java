package ru.smetrix.fgis;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import ru.smetrix.config.FgisProperties;

import java.io.IOException;
import java.util.Optional;

/** Optional fallback for a captured/session-backed endpoint configured by an operator. */
@Component
@Order(20)
public class SessionEmulationProvider implements ResourceDataProvider {
    private final FgisProperties properties;
    private final DirectHttpProvider downloader;
    public SessionEmulationProvider(FgisProperties properties, DirectHttpProvider downloader) {
        this.properties = properties; this.downloader = downloader;
    }
    @Override public String name() { return "session-emulation"; }
    @Override public Optional<ResourcePayload> fetch(String region, FgisPeriod period)
            throws IOException, InterruptedException {
        return downloader.fetchTemplate(properties.getApi().getSessionUrlTemplate(), region, period, name());
    }
}
