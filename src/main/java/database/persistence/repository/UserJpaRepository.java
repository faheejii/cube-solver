package database.persistence.repository;

import database.persistence.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserJpaRepository extends JpaRepository<UserEntity, Long> {
    Optional<UserEntity> findByExternalId(String externalId);

    @Query("""
            select user from UserEntity user
            where lower(user.email) = lower(:email)
              and user.passwordHash is not null
            """)
    Optional<UserEntity> findCredentialByEmail(@Param("email") String email);
}
