package ru.smetrix.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;
import jakarta.validation.constraints.DecimalMin;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "projects", indexes = {
        @Index(name = "idx_projects_user_id", columnList = "user_id"),
        @Index(name = "idx_projects_updated_at", columnList = "updated_at")
})
public class Project {

    @Id
    @Column(nullable = false, updatable = false, columnDefinition = "uuid")
    private UUID id;

    @Column(name = "user_id", nullable = false, columnDefinition = "uuid")
    private UUID userId;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_projects_user"))
    private User user;

    @Column(nullable = false)
    private String name;

    private String city;

    private String regionCode;

    @Column(precision = 19, scale = 6)
    @DecimalMin("0.0")
    private BigDecimal taxMultiplier;

    @Column(precision = 19, scale = 6)
    @DecimalMin("-100.0")
    private BigDecimal logisticsMarkup;

    private String address;

    private String clientId;

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
