package database.algorithms;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UserDisabledAlgorithmJpaRepository
        extends JpaRepository<UserDisabledAlgorithmEntity, UserAlgorithmPreferenceId> {
    java.util.List<UserDisabledAlgorithmEntity> findAllByIdUserId(long userId);
}
