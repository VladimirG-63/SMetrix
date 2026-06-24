package com.smetrix.app.network.dto;

import com.google.gson.annotations.SerializedName;

public class WorkerDto {
    @SerializedName("id")
    public String id;

    @SerializedName("user_id")
    public String userId;

    @SerializedName("operation")
    public String operation;

    @SerializedName("full_name")
    public String fullName;

    @SerializedName("phone")
    public String phone;

    @SerializedName("specialty")
    public String specialty;

    @SerializedName("version")
    public long version;

    @SerializedName("created_at")
    public String createdAt;

    @SerializedName("updated_at")
    public String updatedAt;
}
