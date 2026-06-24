package ru.smetrix.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class RoomSyncDto {

    public String id;

    @JsonProperty("project_id")
    public String projectId;

    public String name;

    public String length;
    public String width;
    public String height;

    @JsonProperty("manual_area_override")
    public String manualAreaOverride;



    public long version;

    @JsonProperty("created_at")
    public Long createdAt;

    @JsonProperty("updated_at")
    public Long updatedAt;

    @JsonProperty("deleted_at")
    public Long deletedAt;
}
