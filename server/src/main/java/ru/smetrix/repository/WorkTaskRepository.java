package ru.smetrix.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.smetrix.entity.WorkTask;

import java.util.List;
import java.util.UUID;

public interface WorkTaskRepository extends JpaRepository<WorkTask, UUID> {
    List<WorkTask> findByProjectRoomIdAndDeletedAtIsNull(UUID projectRoomId);

    @Query("SELECT w FROM WorkTask w WHERE w.projectRoomId IN :roomIds AND w.updatedAt > :since")
    List<WorkTask> findByProjectRoomIdInAndUpdatedAtAfter(@Param("roomIds") List<UUID> roomIds,
                                                          @Param("since") Long since);

    @Query("SELECT w FROM WorkTask w JOIN ProjectRoom r ON w.projectRoomId = r.id " +
            "JOIN Project p ON r.projectId = p.id " +
            "WHERE p.userId = :userId AND w.updatedAt > :since")
    List<WorkTask> findChangedForUser(@Param("userId") UUID userId,
                                      @Param("since") Long since);

    @Modifying
    @Query("UPDATE WorkTask w SET w.deletedAt = :time, w.updatedAt = :time, w.version = w.version + 1 WHERE w.projectRoomId IN (SELECT r.id FROM ProjectRoom r WHERE r.projectId = :projectId) AND w.deletedAt IS NULL")
    void softDeleteByProjectId(@Param("projectId") UUID projectId, @Param("time") Long time);
}
