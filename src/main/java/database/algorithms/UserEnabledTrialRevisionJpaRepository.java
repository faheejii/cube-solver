package database.algorithms;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UserEnabledTrialRevisionJpaRepository
        extends JpaRepository<UserEnabledTrialRevisionEntity, UserTrialRevisionPreferenceId> {
    java.util.List<UserEnabledTrialRevisionEntity> findAllByIdUserId(long userId);
}
