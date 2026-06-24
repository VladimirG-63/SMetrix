package ru.smetrix.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class WorkTaskSyncDto {
    public String id;
    @JsonProperty("project_room_id") public String projectRoomId;
    @JsonProperty("worker_id") public String workerId;
    @JsonProperty("task_name") public String taskName;
    @JsonProperty("rate_type") public String rateType;
    @JsonProperty("rate_value") public String rateValue;
    @JsonProperty("total_payment") public String totalPayment;
    public long version;
    @JsonProperty("created_at") public Long createdAt;
    @JsonProperty("updated_at") public Long updatedAt;
    @JsonProperty("deleted_at") public Long deletedAt;
}
