package ru.smetrix.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.smetrix.entity.FgisImportState;

public interface FgisImportStateRepository extends JpaRepository<FgisImportState, String> {

    boolean existsByRegionCodeAndStatusAndLastSuccessAtGreaterThan(
            String regionCode,
            String status,
            Long lastSuccessAt
    );
}
