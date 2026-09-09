package database.persistence.repository;

import database.persistence.entity.SolveSolutionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SolveSolutionJpaRepository extends JpaRepository<SolveSolutionEntity, Long>, SolveSolutionUpsertRepository {
    Optional<SolveSolutionEntity> findBySolveIdAndMode(long solveId, String mode);

    @Query("""
            select solution from SolveSolutionEntity solution
            where solution.solve.id = :solveId and solution.status = 'ready'
            order by case when solution.mode = 'greedy' then 0 else 1 end
            """)
    List<SolveSolutionEntity> findReadyBySolveId(@Param("solveId") long solveId);
}
