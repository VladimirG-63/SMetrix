package ru.smetrix.fgis;

import java.io.IOException;
import java.io.InputStream;

public record ResourcePayload(String provider, InputStream stream) implements AutoCloseable {
    @Override
    public void close() throws IOException {
        stream.close();
    }
}
