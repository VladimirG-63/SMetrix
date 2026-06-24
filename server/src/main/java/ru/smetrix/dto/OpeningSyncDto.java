package ru.smetrix.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class OpeningSyncDto {
    public String id;
    @JsonProperty("project_room_id") public String projectRoomId;
    public String type;
    public String width;
    public String height;
    public String depth;
    @JsonProperty("placement_type") public String placementType;
    public long version;
    @JsonProperty("created_at") public Long createdAt;
    @JsonProperty("updated_at") public Long updatedAt;
    @JsonProperty("deleted_at") public Long deletedAt;
}
