package ru.smetrix.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class SyncBatchResponse {
    public List<SyncItemResult> results;

    @JsonProperty("batch_size")
    public int batchSize;

    @JsonProperty("processed_at")
    public String processedAt;

    public SyncBatchResponse(List<SyncItemResult> results, int batchSize, String processedAt) {
        this.results = results;
        this.batchSize = batchSize;
        this.processedAt = processedAt;
    }
}
