package ru.smetrix.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SyncPushRequest {
    private List<ProjectSyncDto> projects;
    private List<RoomSyncDto> rooms;
    private List<EstimateItemSyncDto> estimateItems;
}
