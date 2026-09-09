package database.persistence.entity;

import database.CreateSolveAttemptCommand;
import database.HistoryCursor;
import database.SavedSolution;
import database.SaveSolutionCommand;
import database.SolveHistoryDetail;
import database.SolveHistoryEntry;
import database.SolveHistoryPage;
import database.TimedSolve;
import database.persistence.repository.SolveJpaRepository;
import database.persistence.repository.SolveSolutionJpaRepository;
import database.persistence.repository.UserJpaRepository;
import database.persistence.repository.UserStatsJpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import solver.CfopStageResult;
import statistics.SolveStatistics;
import statistics.SolveStatisticsCalculator;

import java.math.RoundingMode;
import java.util.List;

/** JPA-backed history and statistics operations for the Spring API adapters. */
@Service
public final class SpringHistoryPersistenceService {
    private final UserJpaRepository users;
    private final SolveJpaRepository solves;
    private final SolveSolutionJpaRepository solutions;
    private final UserStatsJpaRepository userStats;

    public SpringHistoryPersistenceService(
            UserJpaRepository users,
            SolveJpaRepository solves,
            SolveSolutionJpaRepository solutions,
            UserStatsJpaRepository userStats
    ) {
        this.users = users;
        this.solves = solves;
        this.solutions = solutions;
        this.userStats = userStats;
    }

    @Transactional
    public SolveHistoryEntry createAttempt(CreateSolveAttemptCommand command) {
        var user = requireUser(command.userExternalId());
        var existing = solves.findByUserIdAndClientAttemptId(user.getId(), command.clientAttemptId());
        if (existing.isPresent()) {
            return toEntry(existing.get());
        }

        var insertedId = solves.insertIfAbsent(user.getId(), command);
        if (insertedId == null) {
            return solves.findByUserIdAndClientAttemptId(user.getId(), command.clientAttemptId())
                    .map(this::toEntry)
                    .orElseThrow(() -> new IllegalStateException("Idempotent attempt save could not be reloaded"));
        }

        userStats.updateAfterSolve(user.getId(), command.officialMs(), command.dnf());
        return solves.findById(insertedId)
                .map(this::toEntry)
                .orElseThrow(() -> new IllegalStateException("Solve could not be reloaded"));
    }

    @Transactional(readOnly = true)
    public SolveHistoryPage listPage(String userExternalId, int limit, HistoryCursor cursor) {
        var user = requireUser(userExternalId);
        var pageSize = Math.max(1, Math.min(limit, 100));
        var rows = solves.findHistoryPage(
                user.getId(),
                cursor == null ? null : cursor.createdAt(),
                cursor == null ? null : cursor.id(),
                pageSize + 1
        );
        var hasNext = rows.size() > pageSize;
        var entries = rows.stream().limit(pageSize).map(this::toEntry).toList();
        var nextCursor = hasNext && !entries.isEmpty()
                ? new HistoryCursor(entries.get(entries.size() - 1).createdAt(), entries.get(entries.size() - 1).id()).encode()
                : null;
        return new SolveHistoryPage(entries, nextCursor);
    }

    @Transactional(readOnly = true)
    public SolveHistoryDetail findDetail(String userExternalId, long solveId) {
        var user = requireUser(userExternalId);
        var solve = solves.findOwnedById(solveId, user.getId())
                .orElseThrow(() -> new IllegalArgumentException("Solve not found"));
        return new SolveHistoryDetail(
                solve.getId(), solve.getClientAttemptId(), solve.getScramble(), solve.getCrossFaceRequested(),
                solve.getTimerMs(), solve.getOfficialMs(), solve.getPenalty(), solve.isDnf(), solve.getCreatedAt(),
                solutions.findReadyBySolveId(solveId).stream().map(this::toSavedSolution).toList()
        );
    }

    @Transactional
    public void deleteSolve(String userExternalId, long solveId) {
        var user = requireUser(userExternalId);
        if (solves.deleteOwnedById(solveId, user.getId()) == 0) {
            throw new IllegalArgumentException("Solve not found");
        }
        userStats.rebuild(user.getId());
    }

    @Transactional
    public SavedSolution upsertSolution(SaveSolutionCommand command) {
        var user = requireUser(command.userExternalId());
        var solve = solves.findOwnedById(command.solveId(), user.getId())
                .orElseThrow(() -> new IllegalArgumentException("Solve not found"));
        var solution = new SolveSolutionEntity();
        solution.setSolve(solve);
        solution.setMode(command.mode());
        solution.setStatus("ready");
        solution.setCrossFaceRequested(command.crossFaceRequested());
        solution.setCrossFaceChosen(command.crossFaceChosen());
        solution.setSolution(command.solution());
        solution.setNormalizedSolution(command.normalizedSolution());
        solution.setF2lSetupCaseCount(command.f2lSetupCaseCount());
        solution.setF2lInsertCaseCount(command.f2lInsertCaseCount());
        solution.setF2lTraceJson(command.f2lTraceJson());
        solution.setComparisonJson(command.comparisonJson());
        solution.setCrossAlgorithm(command.crossAlgorithm());
        solution.setCrossMoves(command.crossMoves());
        solution.setCrossSolved(command.crossSolved());
        solution.setCrossStatus(command.crossStatus());
        solution.setF2lAlgorithm(command.f2lAlgorithm());
        solution.setF2lMoves(command.f2lMoves());
        solution.setF2lSolved(command.f2lSolved());
        solution.setF2lStatus(command.f2lStatus());
        solution.setOllAlgorithm(command.ollAlgorithm());
        solution.setOllMoves(command.ollMoves());
        solution.setOllSolved(command.ollSolved());
        solution.setOllStatus(command.ollStatus());
        solution.setPllAlgorithm(command.pllAlgorithm());
        solution.setPllMoves(command.pllMoves());
        solution.setPllSolved(command.pllSolved());
        solution.setPllStatus(command.pllStatus());
        solution.setSolvedF2LSlots(command.solvedF2LSlots());
        solution.setTotalMoves(command.totalMoves());
        solution.setSolveElapsedMs(command.solveElapsedMs());
        solution.setFullySolved(command.fullySolved());
        solution.setSolverVersion(command.solverVersion());
        solutions.upsert(solution);
        return solutions.findBySolveIdAndMode(command.solveId(), command.mode())
                .map(this::toSavedSolution)
                .orElseThrow(() -> new IllegalStateException("Saved solution could not be reloaded"));
    }

    @Transactional(readOnly = true)
    public SolveStatistics statistics(String userExternalId) {
        var user = requireUser(userExternalId);
        var aggregate = solves.aggregateStatistics(user.getId());
        var timed = solves.findRecentTimedSolves(user.getId(), 12).stream()
                .map(row -> new TimedSolve(row.getId(), row.getOfficialMs(), Boolean.TRUE.equals(row.getDnf()), row.getCreatedAt()))
                .toList();
        var recent = solves.findHistoryPage(user.getId(), null, null, 5).stream().map(this::toEntry).toList();
        var average = aggregate.getAverageMs() == null
                ? null
                : aggregate.getAverageMs().setScale(0, RoundingMode.HALF_UP).intValue();
        return new SolveStatistics(
                valueOrZero(aggregate.getSolveCount()), valueOrZero(aggregate.getDnfCount()),
                aggregate.getBestMs(), average,
                SolveStatisticsCalculator.rollingAverage(timed, 5),
                SolveStatisticsCalculator.rollingAverage(timed, 12), recent
        );
    }

    private UserEntity requireUser(String externalId) {
        return users.findByExternalId(externalId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
    }

    private SolveHistoryEntry toEntry(SolveEntity solve) {
        var crossFaces = solutions.findReadyBySolveId(solve.getId()).stream().collect(
                java.util.stream.Collectors.toMap(SolveSolutionEntity::getMode,
                        SolveSolutionEntity::getCrossFaceRequested, (first, ignored) -> first));
        return new SolveHistoryEntry(
                solve.getId(), solve.getClientAttemptId(), solve.getScramble(), solve.getCrossFaceRequested(),
                solve.getTimerMs(), solve.getOfficialMs(), solve.getPenalty(), solve.isDnf(),
                crossFaces.get("greedy"), crossFaces.get("optimized"), solve.getCreatedAt()
        );
    }

    private SolveHistoryEntry toEntry(database.persistence.repository.HistoryRowProjection row) {
        return new SolveHistoryEntry(
                row.getId(), row.getClientAttemptId(), row.getScramble(), row.getCrossFaceRequested(),
                row.getTimerMs(), row.getOfficialMs(), row.getPenalty(), Boolean.TRUE.equals(row.getDnf()),
                row.getFastCross(), row.getOptimizedCross(), row.getCreatedAt()
        );
    }

    private SavedSolution toSavedSolution(SolveSolutionEntity solution) {
        return new SavedSolution(
                solution.getMode(), solution.getCrossFaceRequested(), solution.getCrossFaceChosen(),
                solution.getF2lSetupCaseCount(), solution.getF2lInsertCaseCount(),
                stage("cross", solution.getCrossAlgorithm(), solution.getCrossMoves(), solution.isCrossSolved(), solution.getCrossStatus()),
                stage("f2l", solution.getF2lAlgorithm(), solution.getF2lMoves(), solution.isF2lSolved(), solution.getF2lStatus()),
                stage("oll", solution.getOllAlgorithm(), solution.getOllMoves(), solution.isOllSolved(), solution.getOllStatus()),
                stage("pll", solution.getPllAlgorithm(), solution.getPllMoves(), solution.isPllSolved(), solution.getPllStatus()),
                solution.getSolvedF2LSlots(), solution.isFullySolved(), solution.getTotalMoves(), solution.getSolveElapsedMs(),
                solution.getSolverVersion(), solution.getUpdatedAt(), solution.getF2lTraceJson(), solution.getComparisonJson()
        );
    }

    private static CfopStageResult stage(String name, String algorithm, int moves, boolean solved, String status) {
        return new CfopStageResult(name, algorithm, moves, solved, status);
    }

    private static int valueOrZero(Integer value) {
        return value == null ? 0 : value;
    }
}
