package ru.smetrix.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.smetrix.entity.ProjectRoom;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.Optional;
import java.util.UUID;

public interface ProjectRoomRepository extends JpaRepository<ProjectRoom, UUID> {
    List<ProjectRoom> findByProjectIdAndDeletedAtIsNull(UUID projectId);

    @Query("SELECT r FROM ProjectRoom r WHERE r.projectId = :projectId AND r.updatedAt > :since")
    List<ProjectRoom> findByProjectIdAndUpdatedAtAfter(@Param("projectId") UUID projectId, @Param("since") Long since);

    @Query("SELECT r FROM ProjectRoom r WHERE r.projectId IN :projectIds AND r.updatedAt > :since")
    List<ProjectRoom> findByProjectIdInAndUpdatedAtAfter(@Param("projectIds") List<UUID> projectIds, @Param("since") Long since);

    @Query("SELECT r FROM ProjectRoom r JOIN Project p ON r.projectId = p.id " +
            "WHERE p.userId = :userId AND r.updatedAt > :since")
    List<ProjectRoom> findChangedForUser(@Param("userId") UUID userId,
                                         @Param("since") Long since);

    @Query(value = """
        SELECT CAST(r.id AS text)
        FROM project_rooms r
        JOIN projects p ON r.project_id = p.id
        WHERE CAST(r.id AS text) IN (:ids)
          AND CAST(p.user_id AS text) = :userId
        """, nativeQuery = true)
    Set<String> findIdsByIdInAndUserId(@Param("ids") Collection<String> ids,
                                       @Param("userId") String userId);

    @Modifying
    @Query("UPDATE ProjectRoom r SET r.deletedAt = :time, r.updatedAt = :time, r.version = r.version + 1 WHERE r.projectId = :projectId AND r.deletedAt IS NULL")
    void softDeleteByProjectId(@Param("projectId") UUID projectId, @Param("time") Long time);
}
