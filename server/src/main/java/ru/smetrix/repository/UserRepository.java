package ru.smetrix.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.smetrix.entity.User;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmail(String email);
}
