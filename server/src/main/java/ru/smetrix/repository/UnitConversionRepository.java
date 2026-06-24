package ru.smetrix.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.smetrix.entity.UnitConversion;

import java.util.Optional;

public interface UnitConversionRepository extends JpaRepository<UnitConversion, String> {
    Optional<UnitConversion> findByFgisUnitIgnoreCase(String fgisUnit);
}
