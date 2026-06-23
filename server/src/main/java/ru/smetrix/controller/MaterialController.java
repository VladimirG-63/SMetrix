package ru.smetrix.controller;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import ru.smetrix.dto.MaterialDto;
import ru.smetrix.dto.MaterialSearchResponse;
import ru.smetrix.entity.MaterialCache;
import ru.smetrix.repository.MaterialCacheRepository;

import java.util.Locale;
import java.util.stream.Collectors;
import org.springframework.transaction.annotation.Transactional;

@RestController
@RequestMapping("/api/v1/materials")
public class MaterialController {

    private final MaterialCacheRepository materialCacheRepository;

    public MaterialController(MaterialCacheRepository materialCacheRepository) {
        this.materialCacheRepository = materialCacheRepository;
    }

    @GetMapping("/search")
    public MaterialSearchResponse search(@RequestParam String q, @RequestParam String region) {
        return searchV1(q, region, 20, 0);
    }

    @GetMapping
    public MaterialSearchResponse searchV1(
            @RequestParam String q,
            @RequestParam("region") String region,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        String normalizedQuery = normalizeQuery(q);
        String normalizedRegion = normalizeRegion(region);
        int safeLimit = Math.min(Math.max(limit, 1), 100);
        int safeOffset = Math.min(Math.max(offset, 0), 10_000);
        Page<MaterialCache> matches = materialCacheRepository.searchByRegion(
                normalizedQuery,
                normalizedRegion,
                PageRequest.of(0, safeOffset + safeLimit)
        );
        var items = matches.getContent().stream()
                .skip(safeOffset)
                .limit(safeLimit)
                .map(this::toDto)
                .collect(Collectors.toList());
        int total = (int) Math.min(matches.getTotalElements(), Integer.MAX_VALUE);
        return new MaterialSearchResponse(items, total, safeLimit, safeOffset);
    }

    @PostMapping("/{code}/use")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void recordUse(@PathVariable String code, @RequestParam String region) {
        if (code == null || code.isBlank() || code.length() > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid material code");
        }
        materialCacheRepository.incrementPopularity(code.trim(), normalizeRegion(region));
    }

    private String normalizeQuery(String value) {
        if (value == null || value.isBlank() || value.length() > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Search query must contain 1 to 100 characters");
        }
        return value.trim();
    }

    private String normalizeRegion(String value) {
        if (value == null || !value.trim().matches("[A-Za-zА-Яа-я0-9_-]{1,20}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid region code");
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private MaterialDto toDto(MaterialCache material) {
        MaterialDto dto = new MaterialDto();
        dto.fgisCode = material.getCode();
        dto.name = material.getName();
        dto.unitMeasure = material.getUnit();
        dto.basePrice = material.getPrice() != null ? material.getPrice().toPlainString() : null;
        dto.regionCode = material.getRegion();
        dto.quarter = material.getQuarter();
        dto.priorityScore = material.getPopularityScore();
        dto.consumptionRate = material.getConsumptionRate() != null ? material.getConsumptionRate().toPlainString() : null;
        return dto;
    }
}
