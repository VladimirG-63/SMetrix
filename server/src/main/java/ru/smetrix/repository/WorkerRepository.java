package ru.smetrix.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.smetrix.entity.Worker;

import java.util.List;
import java.util.UUID;

public interface WorkerRepository extends JpaRepository<Worker, UUID> {
    List<Worker> findByUserIdAndDeletedAtIsNull(UUID userId);

    @Query("SELECT w FROM Worker w WHERE w.userId = :userId AND w.updatedAt > :since")
    List<Worker> findByUserIdAndUpdatedAtAfter(@Param("userId") UUID userId,
                                               @Param("since") Long since);
}
