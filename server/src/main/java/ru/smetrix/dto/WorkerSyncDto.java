package ru.smetrix.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class WorkerSyncDto {
    public String id;
    @JsonProperty("user_id") public String userId;
    @JsonProperty("full_name") public String fullName;
    public String phone;
    public String specialty;
    public long version;
    @JsonProperty("created_at") public Long createdAt;
    @JsonProperty("updated_at") public Long updatedAt;
    @JsonProperty("deleted_at") public Long deletedAt;
}
