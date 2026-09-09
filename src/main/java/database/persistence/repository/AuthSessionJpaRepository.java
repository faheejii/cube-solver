package database.persistence.repository;

import database.persistence.entity.AuthSessionEntity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.Optional;

public interface AuthSessionJpaRepository extends JpaRepository<AuthSessionEntity, Long> {
    @EntityGraph(attributePaths = "user")
    Optional<AuthSessionEntity> findByTokenHashAndExpiresAtAfter(String tokenHash, OffsetDateTime now);

    long deleteByTokenHash(String tokenHash);
}
