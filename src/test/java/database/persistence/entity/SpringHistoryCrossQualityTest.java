package database.persistence.entity;

import database.persistence.repository.SolveJpaRepository;
import database.persistence.repository.SolveSolutionJpaRepository;
import database.persistence.repository.UserJpaRepository;
import database.persistence.repository.UserStatsJpaRepository;
import org.junit.jupiter.api.Test;
import solver.CrossQualityAnalyzer;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SpringHistoryCrossQualityTest {
    @Test
    void historyDetailDerivesCrossQualityFromStoredScrambleAndAlgorithm() {
        var users = mock(UserJpaRepository.class);
        var solves = mock(SolveJpaRepository.class);
        var solutions = mock(SolveSolutionJpaRepository.class);
        var userStats = mock(UserStatsJpaRepository.class);
        var user = mock(UserEntity.class);
        var solve = mock(SolveEntity.class);
        var saved = mock(SolveSolutionEntity.class);
        var scramble = "R D R' D2 R D' R'";

        when(users.findByExternalId("user-1")).thenReturn(Optional.of(user));
        when(user.getId()).thenReturn(7L);
        when(solves.findOwnedById(12L, 7L)).thenReturn(Optional.of(solve));
        when(solve.getId()).thenReturn(12L);
        when(solve.getScramble()).thenReturn(scramble);
        when(solve.getCrossFaceRequested()).thenReturn("U");
        when(solve.getPenalty()).thenReturn("none");
        when(solve.getCreatedAt()).thenReturn(java.time.OffsetDateTime.now());
        when(solutions.findReadyBySolveId(12L)).thenReturn(List.of(saved));

        when(saved.getMode()).thenReturn("greedy");
        when(saved.getCrossFaceRequested()).thenReturn("U");
        when(saved.getCrossFaceChosen()).thenReturn("U");
        when(saved.getCrossAlgorithm()).thenReturn("z2");
        when(saved.getCrossMoves()).thenReturn(0);
        when(saved.isCrossSolved()).thenReturn(true);
        when(saved.getCrossStatus()).thenReturn("ok");
        when(saved.getF2lStatus()).thenReturn("ok");
        when(saved.getOllStatus()).thenReturn("ok");
        when(saved.getPllStatus()).thenReturn("ok");
        when(saved.getF2lAlgorithm()).thenReturn("");
        when(saved.getOllAlgorithm()).thenReturn("");
        when(saved.getPllAlgorithm()).thenReturn("");

        var detail = new SpringHistoryPersistenceService(users, solves, solutions, userStats)
                .findDetail("user-1", 12L);

        assertEquals(CrossQualityAnalyzer.solvedF2lPairCount(scramble, "z2"),
                detail.solutions().get(0).cross().solvedF2lPairCount());
    }
}
