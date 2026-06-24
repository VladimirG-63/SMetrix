package ru.smetrix.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "fgis_import_state")
public class FgisImportState {

    @Id
    @Column(name = "region_code", nullable = false, length = 20)
    private String regionCode;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(nullable = false)
    private Long lastAttemptAt;

    private Long lastSuccessAt;

    @Column(length = 500)
    private String lastError;

    private Integer createdCount;
    private Integer updatedCount;
    private Integer unchangedCount;
    private Integer rejectedCount;
}
