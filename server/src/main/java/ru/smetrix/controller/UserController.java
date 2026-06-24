package ru.smetrix.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import ru.smetrix.dto.UserProfileUpdateRequest;
import ru.smetrix.dto.ApiErrorResponse;
import ru.smetrix.entity.User;
import ru.smetrix.repository.UserRepository;

@RestController
@RequestMapping("/api/v1/user")
public class UserController {

    private final UserRepository userRepository;

    public UserController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @PutMapping("/profile")
    public ResponseEntity<?> updateProfile(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody UserProfileUpdateRequest request) {

        String currentEmail = userDetails.getUsername();

        User user = userRepository.findByEmail(currentEmail)
                .orElseThrow(() -> new RuntimeException("Пользователь не найден"));

        String newEmail = request.getEmail();
        if (newEmail != null && !newEmail.isBlank() && !newEmail.equalsIgnoreCase(currentEmail)) {
            boolean emailTaken = userRepository.findByEmail(newEmail).isPresent();
            if (emailTaken) {
                return ResponseEntity.status(409).body(
                        ApiErrorResponse.of("EMAIL_ALREADY_EXISTS",
                                "Этот email уже занят другим пользователем"));
            }
            user.setEmail(newEmail);
        }

        if (request.getName() != null && !request.getName().isBlank()) {
            user.setName(request.getName());
        }

        user.setUpdatedAt(System.currentTimeMillis());
        userRepository.save(user);

        return ResponseEntity.ok("Профиль успешно обновлён");
    }

    @GetMapping("/profile")
    public ResponseEntity<?> getProfile(@AuthenticationPrincipal UserDetails userDetails) {
        String email = userDetails.getUsername();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Пользователь не найден"));

        java.util.Map<String, String> profile = new java.util.HashMap<>();
        profile.put("name", user.getName() != null ? user.getName() : "");
        profile.put("email", user.getEmail());
        return ResponseEntity.ok(profile);
    }
}
