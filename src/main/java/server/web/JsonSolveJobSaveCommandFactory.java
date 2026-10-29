package server.web;

import database.SaveSolutionCommand;
import server.solve.SolveJobSaveCommandFactory;
import solver.CfopSolveResult;

import java.util.List;

final class JsonSolveJobSaveCommandFactory implements SolveJobSaveCommandFactory {
    @Override
    public SaveSolutionCommand create(
            String userId, long solveId, String requestedCrossFace, CfopSolveResult result,
            List<String> disabledAlgorithms
    ) {
        var solution = java.util.stream.Stream.of(
                        result.cross().algorithm(), result.f2l().algorithm(),
                        result.oll().algorithm(), result.pll().algorithm())
                .filter(algorithm -> algorithm != null && !algorithm.isBlank())
                .map(String::trim)
                .collect(java.util.stream.Collectors.joining(" "));
        return new SaveSolutionCommand(
                userId, solveId, result.f2lMode(), requestedCrossFace, result.crossFace(),
                solution, solution, result.f2lSetupCaseCount(), result.f2lInsertCaseCount(),
                result.solvedF2LSlots(), result.totalMoveCount(), result.fullySolved(), result.elapsedMs(),
                result.cross().algorithm(), result.cross().moveCount(), result.cross().solved(), result.cross().status(),
                result.f2l().algorithm(), result.f2l().moveCount(), result.f2l().solved(), result.f2l().status(),
                result.oll().algorithm(), result.oll().moveCount(), result.oll().solved(), result.oll().status(),
                result.pll().algorithm(), result.pll().moveCount(), result.pll().solved(), result.pll().status(),
                SolverVersion.CURRENT,
                JsonSupport.f2lTraceJson(result.f2l(), result.f2lTrace()),
                JsonSupport.modeComparisonJson(result.modeComparison()),
                JsonSupport.stringArrayJson(disabledAlgorithms)
        );
    }
}
