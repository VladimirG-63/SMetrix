package ru.smetrix.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;
import jakarta.validation.constraints.PositiveOrZero;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "work_tasks", indexes = {
        @Index(name = "idx_work_tasks_room_id", columnList = "project_room_id"),
        @Index(name = "idx_work_tasks_worker_id", columnList = "worker_id"),
        @Index(name = "idx_work_tasks_updated_at", columnList = "updated_at")
})
public class WorkTask {
    @Id
    @Column(nullable = false, updatable = false, columnDefinition = "uuid")
    private UUID id;

    @Column(name = "project_room_id", nullable = false, columnDefinition = "uuid")
    private UUID projectRoomId;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_room_id", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_work_tasks_room"))
    private ProjectRoom projectRoom;

    @Column(name = "worker_id", columnDefinition = "uuid")
    private UUID workerId;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "worker_id", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_work_tasks_worker"))
    private Worker worker;

    @Column(nullable = false)
    private String taskName;

    @Column(nullable = false)
    private String rateType;

    @Column(precision = 19, scale = 4)
    @PositiveOrZero
    private BigDecimal rateValue;

    @Column(precision = 19, scale = 2)
    @PositiveOrZero
    private BigDecimal totalPayment;

    @Column(nullable = false)
    private Long version;

    @Version
    @Column(name = "lock_version", nullable = false, columnDefinition = "bigint default 0")
    private Long lockVersion;

    @Column(nullable = false)
    private Long createdAt;

    @Column(nullable = false)
    private Long updatedAt;

    private Long deletedAt;
}
