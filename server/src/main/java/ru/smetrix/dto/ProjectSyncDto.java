package ru.smetrix.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ProjectSyncDto {

    public String id;
    public String name;
    public String city;

    @JsonProperty("region_code")
    public String regionCode;

    @JsonProperty("tax_multiplier")
    public String taxMultiplier;

    @JsonProperty("logistics_markup")
    public String logisticsMarkup;

    public long version;

    @JsonProperty("created_at")
    public Long createdAt;

    @JsonProperty("updated_at")
    public Long updatedAt;

    @JsonProperty("deleted_at")
    public Long deletedAt;
}
