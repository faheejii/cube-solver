package database.algorithms;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;
import java.util.Collection;
import java.util.List;

public interface AlgorithmCatalogAlgorithmJpaRepository
        extends JpaRepository<AlgorithmCatalogAlgorithmEntity, UUID> {
    Optional<AlgorithmCatalogAlgorithmEntity> findBySeedKey(String seedKey);

    boolean existsByPhaseAndName(String phase, String name);

    List<AlgorithmCatalogAlgorithmEntity> findAllByIdIn(Collection<UUID> ids);
}
