package ru.smetrix.fgis;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import ru.smetrix.config.FgisProperties;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;

@Component
@Order(10)
public class DirectHttpProvider implements ResourceDataProvider {
    private static final Logger log = LoggerFactory.getLogger(DirectHttpProvider.class);
    private final FgisProperties properties;

    public DirectHttpProvider(FgisProperties properties) { this.properties = properties; }
    @Override public String name() { return "direct-http"; }

    @Override
    public Optional<ResourcePayload> fetch(String region, FgisPeriod period)
            throws IOException, InterruptedException {
        return fetchTemplate(properties.getApi().getDownloadUrlTemplate(), region, period, name());
    }

    public Optional<ResourcePayload> fetchTemplate(String template, String region, FgisPeriod period, String provider)
            throws IOException, InterruptedException {
        if (template == null || template.isBlank()) return Optional.empty();
        URI uri = buildUri(template, region, period);
        IOException last = null;
        int attempts = Math.max(1, properties.getApi().getMaxAttempts());
        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                return Optional.of(download(uri, provider));
            } catch (IOException e) {
                last = e;
                if (attempt == attempts) break;
                long delay = Math.max(100L, properties.getApi().getRetryDelayMs()) * (1L << (attempt - 1));
                log.warn("FGIS provider {} attempt {}/{} failed; retry in {} ms: {}",
                        provider, attempt, attempts, delay, e.getMessage());
                Thread.sleep(delay);
            }
        }
        throw last == null ? new IOException("FGIS download failed") : last;
    }

    private ResourcePayload download(URI uri, String provider) throws IOException, InterruptedException {
        if (!"https".equalsIgnoreCase(uri.getScheme())) throw new IOException("FGIS URL must use HTTPS");
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(properties.getApi().getConnectTimeoutSeconds()))
                .followRedirects(HttpClient.Redirect.NORMAL).build();
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(properties.getApi().getReadTimeoutSeconds()))
                .header("Accept", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                .header("User-Agent", "SMetrix-FGIS-Importer/1.0").GET().build();
        HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() != 200) {
            response.body().close();
            throw new IOException("FGIS returned HTTP " + response.statusCode());
        }
        long max = properties.getApi().getMaxDownloadBytes();
        if (response.headers().firstValueAsLong("Content-Length").orElse(-1L) > max) {
            response.body().close();
            throw new IOException("FGIS file exceeds size limit");
        }
        return new ResourcePayload(provider, new LimitedInputStream(response.body(), max));
    }

    private URI buildUri(String template, String region, FgisPeriod period) {
        if (!template.contains("{region}")) throw new IllegalArgumentException("FGIS template needs {region}");
        return URI.create(template.replace("{region}", region).replace("{period}", period.value())
                .replace("{year}", Integer.toString(period.year()))
                .replace("{quarter}", Integer.toString(period.quarter())));
    }
}
