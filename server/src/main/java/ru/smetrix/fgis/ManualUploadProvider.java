package ru.smetrix.fgis;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Optional;

/** Documents the terminal fallback; payloads are supplied by the protected multipart endpoint. */
@Component
@Order(30)
public class ManualUploadProvider implements ResourceDataProvider {
    @Override public String name() { return "manual-upload"; }
    @Override public Optional<ResourcePayload> fetch(String region, FgisPeriod period) { return Optional.empty(); }
}
