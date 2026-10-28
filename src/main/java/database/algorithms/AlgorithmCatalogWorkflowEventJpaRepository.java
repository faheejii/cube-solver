package database.algorithms;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AlgorithmCatalogWorkflowEventJpaRepository
        extends JpaRepository<AlgorithmCatalogWorkflowEventEntity, Long> {
}
