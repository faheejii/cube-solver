package database.algorithms;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface AlgorithmCatalogStateJpaRepository
        extends JpaRepository<AlgorithmCatalogStateEntity, Short> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select state from AlgorithmCatalogStateEntity state where state.singletonId = 1")
    Optional<AlgorithmCatalogStateEntity> findSingletonForUpdate();
}
