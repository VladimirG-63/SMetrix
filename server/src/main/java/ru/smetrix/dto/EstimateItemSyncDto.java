package ru.smetrix.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class EstimateItemSyncDto {

    public String id;

    @JsonProperty("project_room_id")
    public String projectRoomId;

    public String name;

    @JsonProperty("fgis_code")
    public String fgisCode;

    @JsonProperty("unit_measure")
    public String unitMeasure;

    @JsonProperty("base_price")
    public String basePrice;

    @JsonProperty("final_price")
    public String finalPrice;

    @JsonProperty("consumption_rate")
    public String consumptionRate;

    public String quantity;

    @JsonProperty("total_price")
    public String totalPrice;

    public String type;
    public String status;

    @JsonProperty("calculation_method")
    public String calculationMethod;

    @JsonProperty("waste_percent")
    public String wastePercent;

    public Integer layers;

    @JsonProperty("thickness_meters")
    public String thicknessMeters;

    @JsonProperty("manual_quantity")
    public String manualQuantity;

    @JsonProperty("coverage_per_piece")
    public String coveragePerPiece;

    @JsonProperty("coverage_per_package")
    public String coveragePerPackage;

    @JsonProperty("package_size")
    public String packageSize;

    @JsonProperty("formula_description")
    public String formulaDescription;

    public long version;

    @JsonProperty("created_at")
    public Long createdAt;

    @JsonProperty("updated_at")
    public Long updatedAt;

    @JsonProperty("deleted_at")
    public Long deletedAt;
}
