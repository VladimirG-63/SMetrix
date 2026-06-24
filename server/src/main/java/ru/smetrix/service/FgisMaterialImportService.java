package ru.smetrix.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.smetrix.entity.MaterialCache;
import ru.smetrix.fgis.FgisImportResult;
import ru.smetrix.fgis.FgisMaterialRecord;
import ru.smetrix.repository.MaterialCacheRepository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class FgisMaterialImportService {

    private final MaterialCacheRepository materialCacheRepository;

    public FgisMaterialImportService(MaterialCacheRepository materialCacheRepository) {
        this.materialCacheRepository = materialCacheRepository;
    }

    @Transactional
    public FgisImportResult importRecords(List<FgisMaterialRecord> records) {
        int created = 0;
        int updated = 0;
        int unchanged = 0;
        int rejected = 0;
        long importedAt = System.currentTimeMillis();

        Map<String, List<FgisMaterialRecord>> recordsByRegion = new LinkedHashMap<>();
        for (FgisMaterialRecord source : records) {
            FgisMaterialRecord record = normalize(source);
            if (!isValid(record)) {
                rejected++;
                continue;
            }
            recordsByRegion.computeIfAbsent(record.regionCode(), ignored -> new ArrayList<>()).add(record);
        }

        for (Map.Entry<String, List<FgisMaterialRecord>> regionEntry : recordsByRegion.entrySet()) {
            String region = regionEntry.getKey();
            List<FgisMaterialRecord> regionRecords = regionEntry.getValue();
            List<String> codes = regionRecords.stream()
                    .map(FgisMaterialRecord::code)
                    .distinct()
                    .toList();
            Map<String, MaterialCache> materialsByCode = materialCacheRepository
                    .findByRegionAndCodeIn(region, codes)
                    .stream()
                    .collect(Collectors.toMap(MaterialCache::getCode, material -> material));
            Map<String, MaterialCache> changedMaterials = new LinkedHashMap<>();

            for (FgisMaterialRecord record : regionRecords) {
                MaterialCache material = materialsByCode.get(record.code());

                if (material == null) {
                    material = new MaterialCache();
                    material.setId(UUID.randomUUID().toString());
                    material.setCode(record.code());
                    material.setRegion(record.regionCode());
                    apply(record, material, importedAt);
                    materialsByCode.put(record.code(), material);
                    changedMaterials.put(record.code(), material);
                    created++;
                } else if (hasChanges(record, material)) {
                    apply(record, material, importedAt);
                    changedMaterials.put(record.code(), material);
                    updated++;
                } else {
                    unchanged++;
                }
            }
            if (!changedMaterials.isEmpty()) {
                materialCacheRepository.saveAll(changedMaterials.values());
            }
        }

        return new FgisImportResult(created, updated, unchanged, rejected);
    }

    private FgisMaterialRecord normalize(FgisMaterialRecord record) {
        if (record == null) {
            return null;
        }
        return new FgisMaterialRecord(
                trimToNull(record.code()),
                trimToNull(record.name()),
                trimToNull(record.unit()),
                record.price(),
                normalizeRegion(record.regionCode()),
                trimToNull(record.quarter()),
                record.consumptionRate()
        );
    }

    private boolean isValid(FgisMaterialRecord record) {
        return record != null
                && record.code() != null
                && record.name() != null
                && record.regionCode() != null
                && record.price() != null
                && record.price().signum() >= 0;
    }

    private boolean hasChanges(FgisMaterialRecord record, MaterialCache material) {
        return !Objects.equals(record.name(), material.getName())
                || !Objects.equals(record.unit(), material.getUnit())
                || !Objects.equals(record.quarter(), material.getQuarter())
                || material.getPrice() == null
                || record.price().compareTo(material.getPrice()) != 0
                || !Objects.equals(record.consumptionRate(), material.getConsumptionRate());
    }

    private void apply(FgisMaterialRecord source, MaterialCache target, long importedAt) {
        target.setName(source.name());
        target.setUnit(source.unit());
        target.setPrice(source.price());
        target.setQuarter(source.quarter());
        target.setConsumptionRate(source.consumptionRate());
        target.setLastUpdated(importedAt);
    }

    private String normalizeRegion(String value) {
        String region = trimToNull(value);
        return region == null ? null : region.toUpperCase(Locale.ROOT);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
