package ru.smetrix.fgis;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

public class LimitedInputStream extends FilterInputStream {

    private final long maximumBytes;
    private long bytesRead;

    public LimitedInputStream(InputStream inputStream, long maximumBytes) {
        super(inputStream);
        if (maximumBytes <= 0) {
            throw new IllegalArgumentException("Maximum byte count must be positive");
        }
        this.maximumBytes = maximumBytes;
    }

    @Override
    public int read() throws IOException {
        int value = super.read();
        if (value >= 0) {
            count(1);
        }
        return value;
    }

    @Override
    public int read(byte[] buffer, int offset, int length) throws IOException {
        int count = super.read(buffer, offset, length);
        if (count > 0) {
            count(count);
        }
        return count;
    }

    private void count(int count) throws IOException {
        bytesRead += count;
        if (bytesRead > maximumBytes) {
            throw new IOException("FGIS download exceeds the configured size limit");
        }
    }
}
