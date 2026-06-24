package com.smetrix.app.network.dto;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/** Изменения сервера после указанного checkpoint. */
public class SyncPullResponse {
    public List<ProjectChange> projects;
    public List<RoomChange> rooms;

    @SerializedName("estimateItems")
    public List<EstimateChange> estimateItems;

    public List<OpeningChange> openings;
    public List<WorkerChange> workers;

    @SerializedName("workTasks")
    public List<WorkTaskChange> workTasks;

    @SerializedName("server_time")
    public long serverTime;

    public static class BaseChange {
        public String id;
        public long version;
        @SerializedName("created_at") public Long createdAt;
        @SerializedName("updated_at") public Long updatedAt;
        @SerializedName("deleted_at") public Long deletedAt;
    }

    public static class ProjectChange extends BaseChange {
        public String name;
        public String city;
        @SerializedName("region_code") public String regionCode;
        @SerializedName("tax_multiplier") public String taxMultiplier;
        @SerializedName("logistics_markup") public String logisticsMarkup;
    }

    public static class RoomChange extends BaseChange {
        @SerializedName("project_id") public String projectId;
        public String name;
        public String length;
        public String width;
        public String height;
        @SerializedName("manual_area_override") public String manualAreaOverride;
    }

    public static class EstimateChange extends BaseChange {
        @SerializedName("project_room_id") public String projectRoomId;
        @SerializedName("fgis_code") public String fgisCode;
        public String name;
        @SerializedName("unit_measure") public String unitMeasure;
        @SerializedName("base_price") public String basePrice;
        @SerializedName("final_price") public String finalPrice;
        @SerializedName("consumption_rate") public String consumptionRate;
        public String quantity;
        @SerializedName("total_price") public String totalPrice;
        public String status;
        @SerializedName("calculation_method") public String calculationMethod;
        @SerializedName("waste_percent") public String wastePercent;
        public Integer layers;
        @SerializedName("thickness_meters") public String thicknessMeters;
        @SerializedName("manual_quantity") public String manualQuantity;
        @SerializedName("coverage_per_piece") public String coveragePerPiece;
        @SerializedName("coverage_per_package") public String coveragePerPackage;
        @SerializedName("package_size") public String packageSize;
        @SerializedName("formula_description") public String formulaDescription;
    }

    public static class OpeningChange extends BaseChange {
        @SerializedName("project_room_id") public String projectRoomId;
        public String type;
        public String width;
        public String height;
        public String depth;
        @SerializedName("placement_type") public String placementType;
    }

    public static class WorkerChange extends BaseChange {
        @SerializedName("user_id") public String userId;
        @SerializedName("full_name") public String fullName;
        public String phone;
        public String specialty;
    }

    public static class WorkTaskChange extends BaseChange {
        @SerializedName("project_room_id") public String projectRoomId;
        @SerializedName("worker_id") public String workerId;
        @SerializedName("task_name") public String taskName;
        @SerializedName("rate_type") public String rateType;
        @SerializedName("rate_value") public String rateValue;
        @SerializedName("total_payment") public String totalPayment;
    }
}
