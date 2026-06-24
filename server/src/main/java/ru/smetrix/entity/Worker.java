package ru.smetrix.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "workers", indexes = {
        @Index(name = "idx_workers_user_id", columnList = "user_id"),
        @Index(name = "idx_workers_updated_at", columnList = "updated_at")
})
public class Worker {
    @Id
    @Column(nullable = false, updatable = false, columnDefinition = "uuid")
    private UUID id;

    @Column(name = "user_id", nullable = false, columnDefinition = "uuid")
    private UUID userId;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_workers_user"))
    private User user;

    @Column(nullable = false)
    private String fullName;

    private String phone;

    private String specialty;

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
