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

    @Query("""
            select user from UserEntity user
            where user.id > :afterId
              and (:query = '' or locate(lower(:query), lower(coalesce(user.email, ''))) > 0
                   or locate(lower(:query), lower(coalesce(user.displayName, ''))) > 0)
            order by user.id asc
            """)
    java.util.List<UserEntity> searchAfter(
            @Param("afterId") long afterId,
            @Param("query") String query,
            org.springframework.data.domain.Pageable pageable
    );

    long countByRole(String role);
}
