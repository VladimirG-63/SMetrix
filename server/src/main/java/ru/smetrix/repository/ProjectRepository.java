package ru.smetrix.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.smetrix.entity.Project;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.Optional;
import java.util.UUID;

public interface ProjectRepository extends JpaRepository<Project, UUID> {
    List<Project> findByUserIdAndDeletedAtIsNull(UUID userId);

    @Query("SELECT p FROM Project p WHERE p.userId = :userId AND p.updatedAt > :since")
    List<Project> findByUserIdAndUpdatedAtAfter(@Param("userId") UUID userId, @Param("since") Long since);

    @Query(value = """
        SELECT CAST(p.id AS text)
        FROM projects p
        WHERE CAST(p.id AS text) IN (:ids)
          AND CAST(p.user_id AS text) = :userId
        """, nativeQuery = true)
    Set<String> findIdsByIdInAndUserId(@Param("ids") Collection<String> ids,
                                       @Param("userId") String userId);

    @Query("SELECT DISTINCT UPPER(TRIM(p.regionCode)) FROM Project p " +
            "WHERE p.deletedAt IS NULL AND p.regionCode IS NOT NULL AND TRIM(p.regionCode) <> ''")
    Set<String> findActiveRegionCodes();
}
