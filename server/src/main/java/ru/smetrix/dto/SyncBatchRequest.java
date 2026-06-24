package ru.smetrix.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

public class SyncBatchRequest {
    @JsonProperty("project_id")
    public String projectId;

    public List<Map<String, Object>> items;
}
