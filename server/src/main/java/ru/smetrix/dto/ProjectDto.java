package ru.smetrix.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ProjectDto {
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
    public String createdAt;

    @JsonProperty("updated_at")
    public String updatedAt;

    @JsonProperty("deleted_at")
    public String deletedAt;
}
