package ru.smetrix.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SyncPullResponse {
    private List<ProjectSyncDto> projects;
    private List<RoomSyncDto> rooms;
    private List<EstimateItemSyncDto> estimateItems;
    private List<OpeningSyncDto> openings;
    private List<WorkerSyncDto> workers;
    private List<WorkTaskSyncDto> workTasks;

    @com.fasterxml.jackson.annotation.JsonProperty("server_time")
    private long serverTime;
}
