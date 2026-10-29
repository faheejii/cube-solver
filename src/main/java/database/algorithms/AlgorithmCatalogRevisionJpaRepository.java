package database.algorithms;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface AlgorithmCatalogRevisionJpaRepository
        extends JpaRepository<AlgorithmCatalogRevisionEntity, UUID> {
    List<AlgorithmCatalogRevisionEntity> findAllByIdIn(Collection<UUID> ids);
    List<AlgorithmCatalogRevisionEntity> findAllByAlgorithmId(UUID algorithmId);
}
