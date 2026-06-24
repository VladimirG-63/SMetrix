package ru.smetrix.fgis;

import java.io.IOException;
import java.util.Optional;

/** Pluggable acquisition layer; parsing and persistence never depend on the source mechanism. */
public interface ResourceDataProvider {
    String name();
    Optional<ResourcePayload> fetch(String regionCode, FgisPeriod period)
            throws IOException, InterruptedException;
}
