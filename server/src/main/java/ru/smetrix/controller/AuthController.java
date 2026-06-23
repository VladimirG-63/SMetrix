package ru.smetrix.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import ru.smetrix.dto.AuthRequest;
import ru.smetrix.dto.AuthResponse;
import ru.smetrix.dto.ApiErrorResponse;
import ru.smetrix.entity.RevokedToken;
import ru.smetrix.entity.User;
import ru.smetrix.repository.RevokedTokenRepository;
import ru.smetrix.repository.UserRepository;
import ru.smetrix.security.AuthRateLimiter;
import ru.smetrix.security.JwtService;
import ru.smetrix.service.MailService;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final long RESET_CODE_TTL_MS = 15 * 60 * 1000L;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final MailService mailService;
    private final RevokedTokenRepository revokedTokenRepository;
    private final AuthRateLimiter rateLimiter;

    public AuthController(UserRepository userRepository,
                          PasswordEncoder passwordEncoder,
                          JwtService jwtService,
                          MailService mailService,
                          RevokedTokenRepository revokedTokenRepository,
                          AuthRateLimiter rateLimiter) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.mailService = mailService;
        this.revokedTokenRepository = revokedTokenRepository;
        this.rateLimiter = rateLimiter;
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody AuthRequest request,
                                      HttpServletRequest httpRequest) {
        String email = normalizeEmail(request.getEmail());
        if (!allow("register", httpRequest, email, 5, Duration.ofHours(1))) {
            return tooManyRequests();
        }
        if (userRepository.findByEmail(email).isPresent()) {
            return ResponseEntity.status(409).body(
                    ApiErrorResponse.of("EMAIL_ALREADY_EXISTS", "Этот email уже зарегистрирован"));
        }

        long now = System.currentTimeMillis();

        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setName(request.getName() != null ? request.getName().trim() : "");
        user.setCreatedAt(now);
        user.setUpdatedAt(now);

        userRepository.save(user);

        String accessToken = jwtService.generateToken(user.getEmail());
        String refreshToken = jwtService.generateRefreshToken(user.getEmail());
        return ResponseEntity.ok(new AuthResponse(
                accessToken,
                refreshToken,
                jwtService.getAccessExpirationSeconds(),
                jwtService.getRefreshExpirationSeconds(),
                user.getId() != null ? user.getId().toString() : null,
                user.getName(),
                user.getEmail()
        ));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@RequestBody Map<String, String> request,
                                            HttpServletRequest httpRequest) {
        String email = normalizeEmail(request.get("email"));
        if (!isValidEmail(email)) {
            return ResponseEntity.badRequest().body(
                    ApiErrorResponse.of("VALIDATION_ERROR", "Некорректный email"));
        }
        if (!allow("forgot", httpRequest, email, 5, Duration.ofHours(1))) {
            return tooManyRequests();
        }

        var optionalUser = userRepository.findByEmail(email);
        if (optionalUser.isEmpty()) {
            return ResponseEntity.ok("Если аккаунт существует, код восстановления отправлен");
        }

        User user = optionalUser.get();

        String code = String.format("%06d", SECURE_RANDOM.nextInt(1000000));
        long expiresAt = System.currentTimeMillis() + RESET_CODE_TTL_MS;

        user.setResetCode(passwordEncoder.encode(code));
        user.setResetCodeExpiresAt(expiresAt);
        userRepository.save(user);

        mailService.sendResetEmail(email, code);

        return ResponseEntity.ok("Если аккаунт существует, код восстановления отправлен");
    }

    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@RequestBody Map<String, String> request,
                                           HttpServletRequest httpRequest) {
        String email = normalizeEmail(request.get("email"));
        String code = request.get("code");
        String newPassword = request.get("newPassword");
        if (!isValidEmail(email) || code == null || !code.matches("\\d{6}")
                || newPassword == null || newPassword.length() < 8 || newPassword.length() > 128) {
            return ResponseEntity.badRequest().body(
                    ApiErrorResponse.of("VALIDATION_ERROR", "Некорректные данные восстановления"));
        }
        if (!allow("reset", httpRequest, email, 10, Duration.ofMinutes(15))) {
            return tooManyRequests();
        }

        var optionalUser = userRepository.findByEmail(email);
        if (optionalUser.isEmpty()) {
            return invalidResetCode();
        }

        User user = optionalUser.get();

        if (user.getResetCode() == null || !passwordEncoder.matches(code, user.getResetCode())) {
            return invalidResetCode();
        }

        Long expiresAt = user.getResetCodeExpiresAt();
        if (expiresAt == null || System.currentTimeMillis() > expiresAt) {
            user.setResetCode(null);
            user.setResetCodeExpiresAt(null);
            userRepository.save(user);
            return invalidResetCode();
        }

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setResetCode(null);
        user.setResetCodeExpiresAt(null);
        userRepository.save(user);

        return ResponseEntity.ok("Пароль успешно изменен");
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody AuthRequest request,
                                   HttpServletRequest httpRequest) {
        String email = normalizeEmail(request.getEmail());
        if (!allow("login", httpRequest, email, 10, Duration.ofMinutes(15))) {
            return tooManyRequests();
        }
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            return ResponseEntity.status(401).body(
                    ApiErrorResponse.of("INVALID_CREDENTIALS", "Неверный email или пароль"));
        }
        String accessToken = jwtService.generateToken(user.getEmail());
        String refreshToken = jwtService.generateRefreshToken(user.getEmail());
        return ResponseEntity.ok(new AuthResponse(
                accessToken,
                refreshToken,
                jwtService.getAccessExpirationSeconds(),
                jwtService.getRefreshExpirationSeconds(),
                user.getId() != null ? user.getId().toString() : null,
                user.getName(),
                user.getEmail()
        ));
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(@RequestBody Map<String, String> request,
                                     HttpServletRequest httpRequest) {
        if (!allow("refresh", httpRequest, "", 30, Duration.ofMinutes(1))) {
            return tooManyRequests();
        }
        String refreshToken = request.get("refresh_token");
        if (refreshToken == null || refreshToken.isBlank()
                || !jwtService.isRefreshTokenValid(refreshToken)) {
            return invalidRefreshToken();
        }

        String tokenId = jwtService.extractTokenId(refreshToken);
        UUID refreshTokenId = parseTokenId(tokenId);
        if (refreshTokenId == null || revokedTokenRepository.existsById(refreshTokenId)) {
            return invalidRefreshToken();
        }

        String email = jwtService.extractEmail(refreshToken);
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) {
            return invalidRefreshToken();
        }


        revokedTokenRepository.save(new RevokedToken(
                refreshTokenId,
                Instant.now(),
                jwtService.extractExpiration(refreshToken).toInstant()
        ));
        revokedTokenRepository.deleteByExpiresAtBefore(Instant.now());

        String newAccessToken = jwtService.generateToken(user.getEmail());
        String newRefreshToken = jwtService.generateRefreshToken(user.getEmail());
        return ResponseEntity.ok(new AuthResponse(
                newAccessToken,
                newRefreshToken,
                jwtService.getAccessExpirationSeconds(),
                jwtService.getRefreshExpirationSeconds(),
                user.getId() != null ? user.getId().toString() : null,
                user.getName(),
                user.getEmail()
        ));
    }
    @PostMapping("/logout")
    public ResponseEntity<?> logout(@RequestBody Map<String, String> request) {
        String refreshToken = request.get("refresh_token");
        if (refreshToken != null && !refreshToken.isBlank()
                && jwtService.isRefreshTokenValid(refreshToken)) {
            UUID refreshTokenId = parseTokenId(jwtService.extractTokenId(refreshToken));
            if (refreshTokenId == null) {
                return ResponseEntity.ok().build();
            }
            revokedTokenRepository.save(new RevokedToken(
                    refreshTokenId,
                    Instant.now(),
                    jwtService.extractExpiration(refreshToken).toInstant()
            ));
        }
        return ResponseEntity.ok().build();
    }

    private boolean allow(String action, HttpServletRequest request, String identity,
                          int maxAttempts, Duration window) {
        String client = request.getRemoteAddr() + ':' + identity;
        return rateLimiter.tryAcquire(action + ':' + client, maxAttempts, window);
    }

    private ResponseEntity<ApiErrorResponse> tooManyRequests() {
        return ResponseEntity.status(429).body(
                ApiErrorResponse.of("RATE_LIMITED", "Слишком много запросов. Попробуйте позже."));
    }

    private ResponseEntity<ApiErrorResponse> invalidRefreshToken() {
        return ResponseEntity.status(401).body(
                ApiErrorResponse.of("INVALID_REFRESH_TOKEN", "Недействительный refresh token"));
    }

    private ResponseEntity<ApiErrorResponse> invalidResetCode() {
        return ResponseEntity.badRequest().body(
                ApiErrorResponse.of("INVALID_RESET_CODE", "Неверный или истёкший код восстановления"));
    }

    private String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    private boolean isValidEmail(String email) {
        return !email.isBlank() && email.length() <= 254
                && email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    }

    private UUID parseTokenId(String tokenId) {
        try {
            return tokenId == null || tokenId.isBlank() ? null : UUID.fromString(tokenId);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
