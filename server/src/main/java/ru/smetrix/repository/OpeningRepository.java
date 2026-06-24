package ru.smetrix.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.smetrix.entity.Opening;

import java.util.List;
import java.util.UUID;

public interface OpeningRepository extends JpaRepository<Opening, UUID> {
    List<Opening> findByProjectRoomIdAndDeletedAtIsNull(UUID projectRoomId);

    @Query("SELECT o FROM Opening o WHERE o.projectRoomId IN :roomIds AND o.updatedAt > :since")
    List<Opening> findByProjectRoomIdInAndUpdatedAtAfter(@Param("roomIds") List<UUID> roomIds,
                                                         @Param("since") Long since);

    @Query("SELECT o FROM Opening o JOIN ProjectRoom r ON o.projectRoomId = r.id " +
            "JOIN Project p ON r.projectId = p.id " +
            "WHERE p.userId = :userId AND o.updatedAt > :since")
    List<Opening> findChangedForUser(@Param("userId") UUID userId,
                                     @Param("since") Long since);

    @Modifying
    @Query("UPDATE Opening o SET o.deletedAt = :time, o.updatedAt = :time, o.version = o.version + 1 WHERE o.projectRoomId IN (SELECT r.id FROM ProjectRoom r WHERE r.projectId = :projectId) AND o.deletedAt IS NULL")
    void softDeleteByProjectId(@Param("projectId") UUID projectId, @Param("time") Long time);
}
