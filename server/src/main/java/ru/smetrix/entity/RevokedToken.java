package ru.smetrix.entity;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.Instant;
import java.util.UUID;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RevokedToken {
    /** В колонке token хранится только JWT jti, а не сам bearer-токен. */
    @Id
    private UUID token;
    private Instant revokedAt;
    private Instant expiresAt;
}
