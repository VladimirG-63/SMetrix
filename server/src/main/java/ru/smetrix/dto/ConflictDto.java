package ru.smetrix.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConflictDto {
    private String entityId;
    private String serverSnapshot;
}
