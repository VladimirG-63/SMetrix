package com.smetrix.app.network.dto;

import com.google.gson.annotations.SerializedName;

public class WorkTaskDto {
    @SerializedName("id")
    public String id;

    @SerializedName("project_room_id")
    public String projectRoomId;

    @SerializedName("worker_id")
    public String workerId;

    @SerializedName("operation")
    public String operation;

    @SerializedName("task_name")
    public String taskName;

    @SerializedName("rate_type")
    public String rateType;

    @SerializedName("rate_value")
    public String rateValue;

    @SerializedName("total_payment")
    public String totalPayment;

    @SerializedName("version")
    public long version;

    @SerializedName("created_at")
    public String createdAt;

    @SerializedName("updated_at")
    public String updatedAt;
}
