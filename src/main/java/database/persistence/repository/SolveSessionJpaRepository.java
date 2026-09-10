package database.persistence.repository;

import database.persistence.entity.SolveSessionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SolveSessionJpaRepository extends JpaRepository<SolveSessionEntity, Long> {
}
