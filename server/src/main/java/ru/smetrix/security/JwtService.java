package ru.smetrix.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

@Service
public class JwtService {

    private static final String TOKEN_TYPE_CLAIM = "token_type";
    private static final String ACCESS_TYPE = "access";
    private static final String REFRESH_TYPE = "refresh";

    @Value("${jwt.secret}")
    private String jwtSecret;

    private static final long ACCESS_EXPIRATION_MS = 15 * 60 * 1000L;
    private static final long REFRESH_EXPIRATION_MS = 30L * 24 * 60 * 60 * 1000L;

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
    }

    public String generateToken(String email) {
        return generateToken(email, ACCESS_TYPE, ACCESS_EXPIRATION_MS);
    }

    public String generateRefreshToken(String email) {
        return generateToken(email, REFRESH_TYPE, REFRESH_EXPIRATION_MS);
    }

    private String generateToken(String email, String tokenType, long expirationMs) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .subject(email)
                .id(UUID.randomUUID().toString())
                .claim(TOKEN_TYPE_CLAIM, tokenType)
                .issuedAt(new Date(now))
                .expiration(new Date(now + expirationMs))
                .signWith(getSigningKey())
                .compact();
    }

    public long getAccessExpirationSeconds() {
        return ACCESS_EXPIRATION_MS / 1000L;
    }

    public long getRefreshExpirationSeconds() {
        return REFRESH_EXPIRATION_MS / 1000L;
    }

    public String extractEmail(String token) {
        return parseClaims(token).getSubject();
    }

    public String extractTokenId(String token) {
        return parseClaims(token).getId();
    }

    public Date extractExpiration(String token) {
        return parseClaims(token).getExpiration();
    }

    public boolean isAccessTokenValid(String token) {
        return isTokenValid(token, ACCESS_TYPE);
    }

    public boolean isRefreshTokenValid(String token) {
        return isTokenValid(token, REFRESH_TYPE);
    }

    private boolean isTokenValid(String token, String expectedType) {
        try {
            String actualType = parseClaims(token).get(TOKEN_TYPE_CLAIM, String.class);
            return expectedType.equals(actualType);
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    private io.jsonwebtoken.Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
