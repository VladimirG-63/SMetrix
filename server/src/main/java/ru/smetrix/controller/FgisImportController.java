package ru.smetrix.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import ru.smetrix.config.FgisProperties;
import ru.smetrix.entity.FgisImportState;
import ru.smetrix.fgis.FgisFileImportResult;
import ru.smetrix.fgis.FgisPeriod;
import ru.smetrix.service.FgisFileImportService;
import ru.smetrix.service.FgisImportStateService;
import ru.smetrix.service.FgisRemoteImportService;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Locale;

@RestController
@RequestMapping("/api/v1/admin/fgis")
public class FgisImportController {

    private final FgisFileImportService fileImportService;
    private final FgisImportStateService stateService;
    private final FgisRemoteImportService remoteImportService;
    private final FgisProperties properties;

    public FgisImportController(
            FgisFileImportService fileImportService,
            FgisImportStateService stateService,
            FgisRemoteImportService remoteImportService,
            FgisProperties properties
    ) {
        this.fileImportService = fileImportService;
        this.stateService = stateService;
        this.remoteImportService = remoteImportService;
        this.properties = properties;
    }

    @GetMapping("/status")
    public List<FgisImportState> status(@RequestHeader("X-FGIS-Import-Key") String importKey) {
        verifyImportKey(importKey);
        return stateService.findAll();
    }

    @PostMapping("/refresh")
    public FgisFileImportResult refresh(
            @RequestHeader("X-FGIS-Import-Key") String importKey,
            @RequestParam("region") String regionCode
    ) {
        verifyImportKey(importKey);
        String normalizedRegion = normalizeRegion(regionCode);
        try {
            return remoteImportService.importRegion(normalizedRegion);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "FGIS import was interrupted", e);
        } catch (IOException | IllegalArgumentException | IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Cannot download FGIS data", e);
        }
    }

    @PostMapping(value = "/import", consumes = "multipart/form-data")
    public FgisFileImportResult importXlsx(
            @RequestHeader("X-FGIS-Import-Key") String importKey,
            @RequestParam("region") String regionCode,
            @RequestParam(value = "period", required = false) String period,
            @RequestParam("file") MultipartFile file
    ) {
        verifyImportKey(importKey);
        if (regionCode == null || regionCode.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Region code is required");
        }
        if (file.isEmpty() || file.getOriginalFilename() == null
                || !file.getOriginalFilename().toLowerCase().endsWith(".xlsx")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A non-empty XLSX file is required");
        }

        String normalizedRegion = normalizeRegion(regionCode);
        String normalizedPeriod;
        try {
            normalizedPeriod = FgisPeriod.resolve(period).value();
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        }
        stateService.markRunning(normalizedRegion);
        try {
            FgisFileImportResult result = fileImportService.importXlsx(
                    file.getInputStream(), normalizedRegion, normalizedPeriod
            );
            stateService.markSuccess(normalizedRegion, result);
            return result;
        } catch (IOException | IllegalArgumentException e) {
            stateService.markFailed(normalizedRegion, e);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot import the XLSX file", e);
        } catch (RuntimeException e) {
            stateService.markFailed(normalizedRegion, e);
            throw e;
        }
    }

    private void verifyImportKey(String suppliedKey) {
        String expectedKey = properties.getImportKey();
        if (expectedKey == null || expectedKey.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "FGIS manual import is not configured");
        }
        boolean matches = MessageDigest.isEqual(
                expectedKey.getBytes(StandardCharsets.UTF_8),
                suppliedKey.getBytes(StandardCharsets.UTF_8)
        );
        if (!matches) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Invalid FGIS import key");
        }
    }

    private String normalizeRegion(String regionCode) {
        if (regionCode == null || !regionCode.trim().matches("[A-Za-zА-Яа-я0-9_-]{1,20}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid region code");
        }
        return regionCode.trim().toUpperCase(Locale.ROOT);
    }
}
