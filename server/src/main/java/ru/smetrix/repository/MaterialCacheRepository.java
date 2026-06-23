package ru.smetrix.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;
import ru.smetrix.entity.MaterialCache;

import java.util.List;
import java.util.Collection;

public interface MaterialCacheRepository extends JpaRepository<MaterialCache, String> {
    List<MaterialCache> findByRegionAndCodeIn(String region, Collection<String> codes);

    List<MaterialCache> findByNameContainingIgnoreCaseAndRegion(String name, String region);

    boolean existsByRegionAndLastUpdatedGreaterThan(String region, Long lastUpdated);

    @Query(value = """
            SELECT * FROM material_cache m
            WHERE upper(m.region) = upper(:region)
              AND (m.name ILIKE concat('%', :query, '%')
                   OR m.code ILIKE concat('%', :query, '%')
                   OR similarity(m.name, :query) > 0.15)
            ORDER BY m.popularity_score DESC,
                     greatest(similarity(m.name, :query), similarity(m.code, :query)) DESC,
                     m.name, m.code
            """,
            countQuery = """
            SELECT count(*) FROM material_cache m
            WHERE upper(m.region) = upper(:region)
              AND (m.name ILIKE concat('%', :query, '%')
                   OR m.code ILIKE concat('%', :query, '%')
                   OR similarity(m.name, :query) > 0.15)
            """,
            nativeQuery = true)
    Page<MaterialCache> searchByRegion(
            @Param("query") String query,
            @Param("region") String region,
            Pageable pageable
    );

    @Modifying
    @Query("UPDATE MaterialCache m SET m.popularityScore = m.popularityScore + 1 " +
            "WHERE UPPER(m.region) = UPPER(:region) AND m.code = :code")
    int incrementPopularity(@Param("code") String code, @Param("region") String region);
}
