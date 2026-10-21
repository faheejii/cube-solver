package server.solve;

import database.SaveSolutionCommand;
import solver.CfopSolveResult;

import java.util.List;

/** Builds persisted-solve commands without coupling the job manager to HTTP serialization. */
public interface SolveJobSaveCommandFactory {
    SaveSolutionCommand create(
            String userId, long solveId, String requestedCrossFace, CfopSolveResult result,
            List<String> disabledAlgorithms
    );
}
