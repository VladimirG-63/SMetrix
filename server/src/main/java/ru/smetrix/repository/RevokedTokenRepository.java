package ru.smetrix.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;
import ru.smetrix.entity.RevokedToken;

import java.time.Instant;
import java.util.UUID;

public interface RevokedTokenRepository extends JpaRepository<RevokedToken, UUID> {
    @Transactional
    long deleteByExpiresAtBefore(Instant now);
}
