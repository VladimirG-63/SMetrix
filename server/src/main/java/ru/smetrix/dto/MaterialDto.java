package ru.smetrix.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class MaterialDto {
    @JsonProperty("fgis_code")
    public String fgisCode;

    public String name;

    @JsonProperty("unit_measure")
    public String unitMeasure;

    @JsonProperty("base_price")
    public String basePrice;

    @JsonProperty("region_code")
    public String regionCode;

    public String quarter;

    @JsonProperty("priority_score")
    public int priorityScore;

    @JsonProperty("consumption_rate")
    public String consumptionRate;
}
