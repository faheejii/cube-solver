package database.persistence.repository;

import database.persistence.entity.UserStatsEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserStatsJpaRepository extends JpaRepository<UserStatsEntity, Long>, UserStatsWriteRepository {
}
