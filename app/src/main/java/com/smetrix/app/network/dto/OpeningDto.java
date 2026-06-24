package com.smetrix.app.network.dto;

import com.google.gson.annotations.SerializedName;

public class OpeningDto {
    @SerializedName("id")
    public String id;

    @SerializedName("project_room_id")
    public String projectRoomId;

    @SerializedName("operation")
    public String operation;

    @SerializedName("type")
    public String type;

    @SerializedName("width")
    public String width;

    @SerializedName("height")
    public String height;

    @SerializedName("depth")
    public String depth;

    @SerializedName("placement_type")
    public String placementType;

    @SerializedName("version")
    public long version;

    @SerializedName("created_at")
    public String createdAt;

    @SerializedName("updated_at")
    public String updatedAt;
}
