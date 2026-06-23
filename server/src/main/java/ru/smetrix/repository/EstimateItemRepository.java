package ru.smetrix.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.smetrix.entity.EstimateItem;

import java.util.List;
import java.util.UUID;

public interface EstimateItemRepository extends JpaRepository<EstimateItem, UUID> {
    List<EstimateItem> findByProjectRoomIdAndDeletedAtIsNull(UUID projectRoomId);

    @Query("SELECT e FROM EstimateItem e WHERE e.projectRoomId = :projectRoomId AND e.updatedAt > :since")
    List<EstimateItem> findByProjectRoomIdAndUpdatedAtAfter(@Param("projectRoomId") UUID projectRoomId, @Param("since") Long since);

    @Query("SELECT e FROM EstimateItem e WHERE e.projectRoomId IN :roomIds AND e.updatedAt > :since")
    List<EstimateItem> findByProjectRoomIdInAndUpdatedAtAfter(@Param("roomIds") List<UUID> roomIds, @Param("since") Long since);

    @Query("SELECT e FROM EstimateItem e JOIN ProjectRoom r ON e.projectRoomId = r.id " +
            "JOIN Project p ON r.projectId = p.id " +
            "WHERE p.userId = :userId AND e.updatedAt > :since")
    List<EstimateItem> findChangedForUser(@Param("userId") UUID userId,
                                          @Param("since") Long since);

    @Modifying
    @Query("UPDATE EstimateItem e SET e.deletedAt = :time, e.updatedAt = :time, e.version = e.version + 1 WHERE e.projectRoomId IN (SELECT r.id FROM ProjectRoom r WHERE r.projectId = :projectId) AND e.deletedAt IS NULL")
    void softDeleteByProjectId(@Param("projectId") UUID projectId, @Param("time") Long time);
}
