package ru.smetrix.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "users",
        uniqueConstraints = @UniqueConstraint(name = "uk_users_email", columnNames = "email"),
        indexes = @Index(name = "idx_users_email", columnList = "email"))
public class User {

    @Id
    @Column(nullable = false, updatable = false, columnDefinition = "uuid")
    private UUID id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String passwordHash;

    @Column(nullable = false)
    private Long createdAt;

    @Column(nullable = false)
    private Long updatedAt;

    @Column(name = "reset_code")
    private String resetCode;

    @Column(name = "reset_code_expires_at")
    private Long resetCodeExpiresAt;

    @Column(columnDefinition = "VARCHAR(255) DEFAULT ''")
    private String name;

    public String getResetCode() { return resetCode; }
    public void setResetCode(String resetCode) { this.resetCode = resetCode; }

    public Long getResetCodeExpiresAt() { return resetCodeExpiresAt; }
    public void setResetCodeExpiresAt(Long resetCodeExpiresAt) { this.resetCodeExpiresAt = resetCodeExpiresAt; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
}
